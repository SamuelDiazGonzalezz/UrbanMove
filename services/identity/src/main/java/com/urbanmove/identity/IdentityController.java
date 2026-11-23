package com.urbanmove.identity;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class IdentityController {

    public record LoginRequest(String username, String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record RoleRequest(String role) {
    }

    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        return identity.login(request.username(), request.password());
    }

    @PostMapping("/api/auth/refresh")
    public Map<String, Object> refresh(@RequestBody RefreshRequest request) {
        return identity.refresh(request.refreshToken());
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody IdentityService.Register request) {
        return identity.register(request);
    }

    @GetMapping("/api/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> users() {
        return identity.users();
    }

    @PostMapping("/api/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> assignRole(@PathVariable String userId, @RequestBody RoleRequest request) {
        return identity.assignRole(userId, request.role());
    }
}
