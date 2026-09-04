package com.example.invoice.api_gateway.controller;

import com.example.invoice.api_gateway.dto.LoginRequest;
import com.example.invoice.api_gateway.dto.TokenResponse;
import com.example.invoice.api_gateway.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Authentication")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Exchange credentials for a JWT", description = "Send the returned token as `Authorization: Bearer <token>`.")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}