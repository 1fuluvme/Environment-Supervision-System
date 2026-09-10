package com.neps.dto;

import java.time.LocalDateTime;

public record UnifiedTaskResponse(
        String taskType,
        Long id,
        Long feedbackId,
        Long anomalyEventId,
        Long gridId,
        String address,
        LocalDateTime observedAt,
        String description,
        String requirement,
        String priority,
        String status,
        LocalDateTime assignedAt) {
}
