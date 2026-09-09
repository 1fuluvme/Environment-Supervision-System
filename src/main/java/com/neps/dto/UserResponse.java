package com.neps.dto;

public record UserResponse(
        Long id,
        String phone,
        String displayName,
        String role
) {
}
