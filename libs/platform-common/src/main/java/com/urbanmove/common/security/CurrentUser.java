package com.urbanmove.common.security;

import com.urbanmove.common.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Acceso al usuario autenticado (claim {@code sub} de Keycloak = userId en toda la plataforma). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String id() {
        return token().getToken().getSubject();
    }

    public static String username() {
        return token().getToken().getClaimAsString("preferred_username");
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public static boolean isStaff() {
        return hasRole("ADMIN") || hasRole("MAINTENANCE");
    }

    public static String bearer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt ? "Bearer " + jwt.getToken().getTokenValue() : null;
    }

    private static JwtAuthenticationToken token() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Inicia sesión para continuar");
    }
}
