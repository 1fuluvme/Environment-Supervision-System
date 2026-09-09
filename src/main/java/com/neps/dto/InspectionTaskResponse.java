package com.neps.dto;

import java.time.LocalDateTime;

public record InspectionTaskResponse(
        Long taskId,
        Long feedbackId,
        Long gridId,
        String address,
        LocalDateTime observedAt,
        String description,
        String feedbackStatus,
        Long assigneeId,
        String requirement,
        String priority,
        String taskStatus,
        LocalDateTime assignedAt
) {
}
