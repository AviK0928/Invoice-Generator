package com.example.invoice.api_gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Users come from configuration, not a database.
 *
 * There is no user service in this system, and adding one to demonstrate JWT
 * would be scope creep. Passwords are bcrypt hashes; plaintext never appears
 * in config or in the repository. See docs/adr for the trade-off.
 */
@Component
@ConfigurationProperties(prefix = "auth")
@Getter
@Setter
public class AuthProperties {

    /** username -> {passwordHash, roles} */
    private Map<String, User> users = Map.of();

    private long tokenTtlMinutes = 60;

    @Getter
    @Setter
    public static class User {
        private String passwordHash;
        private List<String> roles = List.of("USER");
    }
}