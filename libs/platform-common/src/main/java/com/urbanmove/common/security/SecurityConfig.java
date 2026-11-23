package com.urbanmove.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Cada servicio valida el JWT emitido por Keycloak (roles de realm USER, MAINTENANCE, ADMIN).
 * {@code /internal/**} y {@code /actuator/**} solo son accesibles dentro de la red de Docker:
 * el gateway no los expone.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Value("${urbanmove.security.public-paths:}") String[] publicPathsRaw,
                                            @Value("${urbanmove.security.public-get-paths:}") String[] publicGetPathsRaw) throws Exception {
        String[] publicPaths = java.util.Arrays.stream(publicPathsRaw).filter(p -> !p.isBlank()).toArray(String[]::new);
        String[] publicGetPaths = java.util.Arrays.stream(publicGetPathsRaw).filter(p -> !p.isBlank()).toArray(String[]::new);
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> {
                    a.requestMatchers("/actuator/**", "/internal/**", "/error").permitAll();
                    if (publicPaths.length > 0) {
                        a.requestMatchers(publicPaths).permitAll();
                    }
                    if (publicGetPaths.length > 0) {
                        a.requestMatchers(HttpMethod.GET, publicGetPaths).permitAll();
                    }
                    a.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new RealmRoleConverter())));
        return http.build();
    }

    static final class RealmRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
        @Override
        @SuppressWarnings("unchecked")
        public AbstractAuthenticationToken convert(Jwt jwt) {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Collection<String> roles = realmAccess == null ? List.of() : (Collection<String>) realmAccess.getOrDefault("roles", List.of());
            List<GrantedAuthority> authorities = roles.stream()
                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
        }
    }
}
