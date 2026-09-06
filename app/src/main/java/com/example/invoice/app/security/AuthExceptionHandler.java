package com.example.invoice.app.security;

import com.example.invoice.common.auth.AuthController;
import com.example.invoice.common.web.BaseExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A failed login is 401, not 500.
 *
 * AuthService throws BadCredentialsException from a controller. At the gateway
 * that reached Spring Security's reactive handling and became a 401. In one
 * servlet context it reaches the five domain advices instead, whose inherited
 * catch-all on Exception turns a wrong password into a server error — which is
 * both wrong and, on a public demo, indistinguishable from a broken endpoint.
 *
 * Two annotations are load-bearing and neither is optional:
 *
 * assignableTypes scopes this advice to AuthController. Without it, extending
 * BaseExceptionHandler at highest precedence would make the inherited
 * 
 * @ExceptionHandler(Exception.class) the first match for every exception in the
 *                                    application, and every domain 404 and 409
 *                                    would become a 500.
 *
 * @Order is needed even with that scoping: for an exception from
 *        AuthController, the five unordered domain advices also match via the
 *        same
 *        inherited catch-all, and the resolver takes the first advice in order.
 *
 *        The detail names neither username nor password, for the same reason
 *        AuthService verifies a dummy hash for unknown users — the response
 *        should not
 *        distinguish an unknown user from a wrong password.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler extends BaseExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
                "Invalid username or password.", "authentication-failed");
    }
}