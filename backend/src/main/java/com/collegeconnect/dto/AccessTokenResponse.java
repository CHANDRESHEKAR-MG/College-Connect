package com.collegeconnect.dto;

public record AccessTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
