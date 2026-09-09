package com.neps.dto;

import java.time.LocalDateTime;

public record MeasurementReviewResponse(
        Long reviewId,
        Long measurementId,
        Long reviewerId,
        String decision,
        String opinion,
        String publicReply,
        LocalDateTime reviewedAt,
        String measurementStatus,
        String taskStatus,
        String feedbackStatus) {
}
