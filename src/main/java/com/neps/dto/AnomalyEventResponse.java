package com.neps.dto;

import java.time.LocalDateTime;

public record AnomalyEventResponse(
        Long id,
        Long feedbackId,
        Long taskId,
        Long measurementId,
        Long gridId,
        String status,
        String suggestedPriority,
        String confirmedPriority,
        String triggerReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewReason,
        Integer aqi,
        String reportType,
        String qualityFlag,
        String address,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}