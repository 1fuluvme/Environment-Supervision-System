package com.neps.dto;

import java.time.LocalDateTime;

public record AdminFeedbackResponse(
        Long id,
        Long submitterId,
        String submitterPhone,
        String submitterName,
        Long gridId,
        String gridCode,
        String gridName,
        String address,
        LocalDateTime observedAt,
        String description,
        String status,
        String publicReply,
        String analysisStatus,
        Long taskId,
        Long assigneeId,
        String taskStatus,
        String priority,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
