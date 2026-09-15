package com.neps.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiAgentAuditResponse(
        Long id,
        Long userId,
        String userPhone,
        String conversationId,
        Long regionId,
        LocalDate startDate,
        LocalDate endDate,
        String reportType,
        String question,
        String status,
        String provider,
        String modelName,
        List<String> sourceTypes,
        Long durationMs,
        String failureReason,
        LocalDateTime createdAt
) {
}
