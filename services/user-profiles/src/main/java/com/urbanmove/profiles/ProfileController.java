package com.urbanmove.profiles;

import com.urbanmove.common.events.EventEnvelope;
import com.urbanmove.common.events.EventHandler;
import com.urbanmove.common.events.EventStore;
import com.urbanmove.common.security.CurrentUser;
import com.urbanmove.common.web.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Perfil del usuario. Se crea al consumir {@code UserRegistered} y se edita desde la app. */
@RestController
@RequestMapping("/api/profiles")
public class ProfileController implements EventHandler {

    public record ProfileUpdate(String fullName, String phone, String preferredZoneId) {
    }

    private final JdbcTemplate jdbc;
    private final EventStore events;

    public ProfileController(JdbcTemplate jdbc, EventStore events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return jdbc.queryForList("SELECT * FROM user_profile WHERE user_id = ?", CurrentUser.id()).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Perfil (se crea unos segundos después del registro)"));
    }

    @PutMapping("/me")
    @Transactional
    public Map<String, Object> update(@RequestBody ProfileUpdate update) {
        me();
        jdbc.update("UPDATE user_profile SET full_name = COALESCE(?, full_name), phone = ?, preferred_zone_id = ?, updated_at = now() WHERE user_id = ?",
                update.fullName(), update.phone(), update.preferredZoneId(), CurrentUser.id());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", CurrentUser.id());
        payload.put("changedFields", List.of("fullName", "phone", "preferredZoneId"));
        payload.put("preferredZoneId", update.preferredZoneId());
        events.append("profile", CurrentUser.id(), "UserProfileUpdated", payload);
        return me();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> all() {
        return jdbc.queryForList("SELECT * FROM user_profile ORDER BY created_at DESC");
    }

    @Override
    public Set<String> eventTypes() {
        return Set.of("UserRegistered");
    }

    @Override
    public void handle(EventEnvelope e) {
        jdbc.update("""
                INSERT INTO user_profile (user_id, username, email, full_name) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO NOTHING
                """, e.text("userId"), e.text("username"), e.text("email"), e.text("fullName"));
    }
}
