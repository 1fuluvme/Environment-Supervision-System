package com.neps.dto;

import java.time.LocalDateTime;

public record AiAnalysisResponse(
        Long id,
        String targetType,
        Long targetId,
        String status,
        String provider,
        String modelName,
        String inputSnapshot,
        String resultText,
        String failureReason,
        boolean demo,
        Integer attemptCount,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
