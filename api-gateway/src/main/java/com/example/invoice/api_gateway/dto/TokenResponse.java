package com.example.invoice.api_gateway.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}