package com.urbanmove.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fachada de identidad (ADR 0004): Keycloak gestiona credenciales y sesiones; este servicio
 * registra usuarios, asigna roles, mantiene el espejo administrativo y publica los eventos.
 */
@Service
public class IdentityService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);
    private static final Set<String> ROLES = Set.of("USER", "MAINTENANCE", "ADMIN");

    public record Register(String username, String email, String password, String fullName) {
    }

    private final KeycloakClient keycloak;
    private final JdbcTemplate jdbc;
    private final EventStore events;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;

    public IdentityService(KeycloakClient keycloak, JdbcTemplate jdbc, EventStore events, TransactionTemplate tx, ObjectMapper objectMapper) {
        this.keycloak = keycloak;
        this.jdbc = jdbc;
        this.events = events;
        this.tx = tx;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null || password == null) {
            throw ApiException.badRequest("MISSING_CREDENTIALS", "Usuario y contraseña son obligatorios");
        }
        return session(keycloak.passwordGrant(username.trim(), password));
    }

    public Map<String, Object> refresh(String refreshToken) {
        return session(keycloak.refresh(refreshToken));
    }

    private Map<String, Object> session(Map<String, Object> token) {
        String accessToken = (String) token.get("access_token");
        JsonNode claims = claims(accessToken);
        List<String> roles = new ArrayList<>();
        claims.path("realm_access").path("roles").forEach(r -> {
            if (ROLES.contains(r.asText())) {
                roles.add(r.asText());
            }
        });
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", claims.path("sub").asText());
        user.put("username", claims.path("preferred_username").asText());
        user.put("fullName", claims.path("name").asText(""));
        user.put("email", claims.path("email").asText(""));
        user.put("roles", roles);
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("accessToken", accessToken);
        session.put("refreshToken", token.get("refresh_token"));
        session.put("expiresIn", token.get("expires_in"));
        session.put("user", user);
        return session;
    }

    public Map<String, Object> register(Register r) {
        if (r.username() == null || !r.username().matches("[a-zA-Z0-9._-]{3,30}")) {
            throw ApiException.badRequest("INVALID_USERNAME", "El usuario tiene 3-30 caracteres: letras, números, punto, guion");
        }
        if (r.email() == null || !r.email().contains("@")) {
            throw ApiException.badRequest("INVALID_EMAIL", "Correo no válido");
        }
        if (r.password() == null || r.password().length() < 8) {
            throw ApiException.badRequest("WEAK_PASSWORD", "La contraseña necesita al menos 8 caracteres");
        }
        String fullName = r.fullName() == null || r.fullName().isBlank() ? r.username() : r.fullName().trim();
        String[] parts = fullName.split("\\s+", 2);
        String userId = keycloak.createUser(r.username().toLowerCase(), r.email(), parts[0], parts.length > 1 ? parts[1] : "-", r.password());
        keycloak.assignRealmRole(userId, "USER");
        tx.executeWithoutResult(s -> record(userId, r.username().toLowerCase(), r.email(), fullName, List.of("USER")));
        return login(r.username(), r.password());
    }

    public List<Map<String, Object>> users() {
        return jdbc.queryForList("SELECT * FROM user_account ORDER BY created_at");
    }

    @Transactional
    public Map<String, Object> assignRole(String userId, String role) {
        if (!ROLES.contains(role)) {
            throw ApiException.badRequest("INVALID_ROLE", "Rol no válido");
        }
        Map<String, Object> account = jdbc.queryForList("SELECT * FROM user_account WHERE user_id = ?", userId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Usuario " + userId));
        keycloak.assignRealmRole(userId, role);
        List<String> roles = new ArrayList<>(List.of(account.get("roles").toString().split(",")));
        roles.removeIf(String::isBlank);
        if (!roles.contains(role)) {
            roles.add(role);
        }
        jdbc.update("UPDATE user_account SET roles = ? WHERE user_id = ?", String.join(",", roles), userId);
        events.append("user", userId, "UserRoleAssigned", Map.of("userId", userId, "role", role));
        return jdbc.queryForList("SELECT * FROM user_account WHERE user_id = ?", userId).get(0);
    }

    private void record(String userId, String username, String email, String fullName, List<String> roles) {
        int inserted = jdbc.update("INSERT INTO user_account (user_id, username, email, full_name, roles) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                userId, username, email, fullName, String.join(",", roles));
        if (inserted == 0) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("email", email);
        payload.put("fullName", fullName);
        payload.put("roles", roles);
        events.append("user", userId, "UserRegistered", payload);
    }

    private JsonNode claims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Token de Keycloak ilegible", e);
        }
    }

    /** Sincroniza los usuarios semilla del realm (importados por Keycloak) y publica UserRegistered. */
    @Override
    public void run(ApplicationArguments args) {
        Thread sync = new Thread(() -> {
            for (int attempt = 0; attempt < 60; attempt++) {
                try {
                    for (Map<String, Object> u : keycloak.users()) {
                        String id = String.valueOf(u.get("id"));
                        String fullName = (u.getOrDefault("firstName", "") + " " + u.getOrDefault("lastName", "")).trim();
                        List<String> roles = keycloak.realmRoles(id);
                        tx.executeWithoutResult(s -> record(id, String.valueOf(u.get("username")), (String) u.get("email"), fullName, roles));
                    }
                    log.info("Usuarios de Keycloak sincronizados");
                    return;
                } catch (Exception e) {
                    log.debug("Keycloak aún no disponible: {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "keycloak-sync");
        sync.setDaemon(true);
        sync.start();
    }
}
