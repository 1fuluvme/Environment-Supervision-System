package com.neps.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;

public record FeedbackResponse(
        Long id,
        Long gridId,
        String address,
        BigDecimal longitude,
        BigDecimal latitude,
        LocalDateTime observedAt,
        String description,
        String status,
        String publicReply,
        String analysisStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}