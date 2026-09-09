package com.neps.dto;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long id,
        Long gridId,
        String address,
        LocalDateTime observedAt,
        String description,
        String status,
        String publicReply,
        String analysisStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}