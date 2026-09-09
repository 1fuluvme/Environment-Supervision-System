package com.neps.dto;

public record AdminUserResponse(
        Long id,
        String phone,
        String displayName,
        String role,
        boolean enabled
) {
}
