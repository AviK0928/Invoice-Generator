package com.example.invoice.api_gateway.service;

import com.example.invoice.api_gateway.config.AuthProperties;
import com.example.invoice.api_gateway.dto.LoginRequest;
import com.example.invoice.api_gateway.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String ISSUER = "invoice-generator";

    /**
     * A real bcrypt hash, verified against the encoder. Used when the username
     * is unknown so that verification still costs a full bcrypt round —
     * returning early would make a missing user measurably faster than a wrong
     * password, which lets an attacker enumerate valid usernames.
     *
     * Its validity is the whole point and is easy to lose. The previous value
     * looked like a hash but was not one: BCryptPasswordEncoder rejected it on
     * shape, logged "Encoded password does not look like BCrypt", and returned
     * false without hashing anything. An unknown user came back in
     * microseconds against tens of milliseconds for a wrong password — the
     * exact gap this constant exists to close. Nothing failed; only the log
     * showed it.
     *
     * Which password it corresponds to does not matter. That it is well-formed
     * does. If this is ever regenerated, check the log for that warning.
     */
    private static final String DUMMY_HASH = "$2b$10$gEVIhuWm4QhkKTQmrTrrFOrhJraFkmp/LVHv0hbCGVSkDH4oJyCbm";

    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public TokenResponse login(LoginRequest request) {
        AuthProperties.User user = authProperties.getUsers().get(request.username());

        String hash = (user != null) ? user.getPasswordHash() : DUMMY_HASH;
        boolean matches = passwordEncoder.matches(request.password(), hash);

        if (user == null || !matches) {
            // Never log which of the two failed, or the password itself.
            log.warn("Failed login attempt for username: {}", request.username());
            throw new BadCredentialsException("Invalid username or password.");
        }

        return issueToken(request.username(), user);
    }

    private TokenResponse issueToken(String username, AuthProperties.User user) {
        Instant now = Instant.now();
        long ttlMinutes = authProperties.getTokenTtlMinutes();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES))
                .subject(username)
                .claim("roles", user.getRoles())
                .build();

        // NimbusJwtEncoder defaults to RS256 when the header is unset, which
        // fails with "Failed to select a JWK signing key" against a symmetric
        // secret. HS256 must be stated explicitly.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        log.info("Issued token for {} (roles={}, ttl={}m)", username, user.getRoles(), ttlMinutes);

        return new TokenResponse(token, "Bearer", ttlMinutes * 60);
    }
}