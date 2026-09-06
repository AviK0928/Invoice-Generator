package com.example.invoice.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The gateway's rules on the servlet stack. Same shape, three differences.
 *
 * /error is permitted. Spring Security filters the ERROR dispatch by default,
 * so anyRequest().denyAll() turns every unhandled exception into a 403 and
 * hides what actually failed. The reactive gateway has no error forward, so
 * this had nothing to correspond to there.
 *
 * The springdoc paths are single, not per-service: one process serves one spec
 * at /v3/api-docs. The /api/{service}/v3/api-docs namespacing existed so the
 * gateway's proxy routes carried it.
 *
 * Sessions are stateless and CSRF is off for the same reason as at the gateway:
 * no cookies, every request carries a bearer token.
 */
@Configuration
public class SecurityConfig {

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .csrf(csrf -> csrf.disable())
                                .httpBasic(basic -> basic.disable())
                                .formLogin(form -> form.disable())
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                                                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                                                .requestMatchers("/error").permitAll()
                                                .requestMatchers("/", "/swagger-ui.html", "/swagger-ui/**",
                                                                "/webjars/**", "/v3/api-docs/**", "/v3/api-docs")
                                                .permitAll()
                                                .requestMatchers("/api/**").authenticated()
                                                .anyRequest().denyAll())
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt.jwtAuthenticationConverter(authoritiesConverter())))
                                .build();
        }

        /** Maps the "roles" claim to ROLE_-prefixed authorities. */
        private JwtAuthenticationConverter authoritiesConverter() {
                JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
                authorities.setAuthoritiesClaimName("roles");
                authorities.setAuthorityPrefix("ROLE_");

                JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(authorities);
                return converter;
        }
}