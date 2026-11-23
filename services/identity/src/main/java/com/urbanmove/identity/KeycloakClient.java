package com.urbanmove.identity;

import com.urbanmove.common.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Cliente mínimo de Keycloak: token de usuario (password grant) y API de administración. */
@Component
public class KeycloakClient {

    private final RestClient http;
    private final String realm;
    private final String clientId;
    private final String adminUser;
    private final String adminPassword;

    public KeycloakClient(@Value("${KEYCLOAK_URL:http://keycloak:8080}") String baseUrl,
                          @Value("${KEYCLOAK_REALM:urbanmove}") String realm,
                          @Value("${KEYCLOAK_CLIENT_ID:urbanmove-web}") String clientId,
                          @Value("${KEYCLOAK_ADMIN:admin}") String adminUser,
                          @Value("${KEYCLOAK_ADMIN_PASSWORD:admin}") String adminPassword) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.realm = realm;
        this.clientId = clientId;
        this.adminUser = adminUser;
        this.adminPassword = adminPassword;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid");
        return token(form);
    }

    public Map<String, Object> refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);
        return token(form);
    }

    private Map<String, Object> token(MultiValueMap<String, String> form) {
        try {
            return http.post().uri("/realms/{realm}/protocol/openid-connect/token", realm)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
        } catch (HttpClientErrorException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Usuario o contraseña incorrectos");
        }
    }

    private String adminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUser);
        form.add("password", adminPassword);
        Map<String, Object> token = http.post().uri("/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        return "Bearer " + token.get("access_token");
    }

    public List<Map<String, Object>> users() {
        return http.get().uri("/admin/realms/{realm}/users?max=500", realm).header("Authorization", adminToken())
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
    }

    public List<String> realmRoles(String userId) {
        List<Map<String, Object>> roles = http.get().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId)
                .header("Authorization", adminToken()).retrieve().body(new ParameterizedTypeReference<>() {
                });
        return roles == null ? List.of() : roles.stream().map(r -> String.valueOf(r.get("name")))
                .filter(r -> List.of("USER", "MAINTENANCE", "ADMIN").contains(r)).toList();
    }

    public String createUser(String username, String email, String firstName, String lastName, String password) {
        Map<String, Object> body = Map.of(
                "username", username, "email", email, "firstName", firstName, "lastName", lastName,
                "enabled", true, "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)));
        try {
            ResponseEntity<Void> response = http.post().uri("/admin/realms/{realm}/users", realm)
                    .header("Authorization", adminToken()).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().toBodilessEntity();
            String location = response.getHeaders().getLocation().toString();
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict e) {
            throw ApiException.conflict("USER_EXISTS", "Ese usuario o correo ya está registrado");
        } catch (HttpClientErrorException e) {
            throw ApiException.badRequest("INVALID_USER", "Keycloak rechazó los datos: " + e.getResponseBodyAsString());
        }
    }

    public void assignRealmRole(String userId, String role) {
        String auth = adminToken();
        Map<String, Object> roleRep = http.get().uri("/admin/realms/{realm}/roles/{role}", realm, role).header("Authorization", auth)
                .retrieve().body(new ParameterizedTypeReference<>() {
                });
        http.post().uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, userId).header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).body(List.of(roleRep)).retrieve().toBodilessEntity();
    }
}
