package com.example.invoice.api_gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's security boundary, as a client sees it: who gets a token, who
 * is turned away, and which paths are reachable without one.
 *
 * A real port rather than a mock exchange, because the thing under test is the
 * filter chain in front of the routes. Requests to proxied paths are all
 * rejected by that chain before routing, so no backend needs to exist —
 * an authenticated request to /api/customers/** would try to reach
 * localhost:8081 and fail, which is why none of these send one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthContractTest {

    @Autowired
    WebTestClient client;

    // ------------------------------------------------------------- login

    @Test
    @DisplayName("valid credentials return a bearer token")
    void loginReturnsToken() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"admin","password":"test-password"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                // token-ttl-minutes is 60, reported in seconds.
                .jsonPath("$.expiresIn").isEqualTo(3600);
    }

    @Test
    @DisplayName("a wrong password and an unknown user are indistinguishable")
    void failedLoginsLookIdentical() {
        byte[] wrongPassword = login("admin", "not-the-password")
                .expectStatus().isUnauthorized()
                .expectBody().returnResult().getResponseBody();

        byte[] unknownUser = login("nobody", "test-password")
                .expectStatus().isUnauthorized()
                .expectBody().returnResult().getResponseBody();

        // AuthService verifies a dummy hash for unknown users so both paths
        // cost a full bcrypt round. The response must not give away what the
        // timing defence is protecting: which of the two failed.
        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("an unknown user costs a bcrypt round, like a wrong password")
    void unknownUserIsNotFasterThanWrongPassword() {
        long unknownUser = time(() -> login("nobody", "test-password")
                .expectStatus().isUnauthorized());
        long wrongPassword = time(() -> login("admin", "not-the-password")
                .expectStatus().isUnauthorized());

        // A malformed DUMMY_HASH makes BCryptPasswordEncoder bail on shape
        // without hashing, so an unknown user returns in microseconds while a
        // wrong password takes a full cost-10 round. That gap enumerates
        // usernames. Deliberately loose — this catches "no work at all", not
        // a timing side channel, which HTTP cannot measure precisely.
        assertThat(unknownUser).isGreaterThan(wrongPassword / 4);
    }

    private long time(Runnable request) {
        long start = System.nanoTime();
        request.run();
        return System.nanoTime() - start;
    }

    @Test
    @DisplayName("blank credentials are a 400, not a 401")
    void blankCredentialsAreRejectedAsInvalid() {
        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"username":"","password":""}
                        """)
                .exchange()
                // @NotBlank fails before AuthService is reached, so this is a
                // malformed request rather than a rejected credential.
                .expectStatus().isBadRequest();
    }

    // --------------------------------------------------- protected routes

    @Test
    @DisplayName("a proxied route needs a token")
    void protectedRouteRequiresToken() {
        client.get().uri("/api/customers/1")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("a malformed token is rejected, not passed through")
    void garbageTokenIsRejected() {
        client.get().uri("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("a token signed with the wrong key is rejected")
    void foreignTokenIsRejected() {
        // Structurally a JWT, signed with a different secret. Catches a
        // decoder that parses without verifying.
        String foreign = "eyJhbGciOiJIUzI1NiJ9"
                + ".eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIkFETUlOIl19"
                + ".Ck2b7dVMuVhCVOA1BOJ0Ck0TqLc5CGWmSNiJcpMHi1o";

        client.get().uri("/api/customers/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + foreign)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ------------------------------------------------------ public paths

    @Test
    @DisplayName("the health probe is public")
    void healthIsPublic() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("anything outside /api is denied even with a valid token")
    void unmappedPathIsDeniedToAuthenticatedCallers() {
        String token = validToken();

        // anyExchange().denyAll() is the last rule. Authenticated but not
        // permitted is a 403, distinct from the 401 an anonymous caller gets.
        client.get().uri("/internal/whatever")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ---------------------------------------------------------- helpers

    private WebTestClient.ResponseSpec login(String username, String password) {
        return client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .exchange();
    }

    private String validToken() {
        return login("user", "test-password")
                .expectStatus().isOk()
                .expectBody(TokenBody.class)
                .returnResult().getResponseBody()
                .accessToken();
    }

    private record TokenBody(String accessToken, String tokenType, long expiresIn) {
    }
}