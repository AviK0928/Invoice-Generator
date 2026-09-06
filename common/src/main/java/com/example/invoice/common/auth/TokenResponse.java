package com.example.invoice.common.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}