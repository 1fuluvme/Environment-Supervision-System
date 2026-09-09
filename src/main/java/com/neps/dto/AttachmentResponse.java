package com.neps.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        String originalName,
        String contentType,
        Long sizeBytes,
        LocalDateTime createdAt,
        String contentUrl
) {
}
