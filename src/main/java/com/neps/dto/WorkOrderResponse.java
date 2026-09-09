package com.neps.dto;

import java.time.LocalDateTime;

public record WorkOrderResponse(
        Long id,
        Long anomalyEventId,
        Long feedbackId,
        Long gridId,
        Long assigneeId,
        String assigneeName,
        Long assignedBy,
        String requirement,
        String priority,
        String status,
        LocalDateTime assignedAt,
        LocalDateTime handledAt,
        String measures,
        String result,
        LocalDateTime submittedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewOpinion,
        String address,
        String description,
        String triggerReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
