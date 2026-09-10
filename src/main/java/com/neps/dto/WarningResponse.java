package com.neps.dto;

import java.time.LocalDateTime;

public record WarningResponse(
        Long id,
        Long anomalyEventId,
        Long gridId,
        String warningLevel,
        String title,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime closedAt) {
}
