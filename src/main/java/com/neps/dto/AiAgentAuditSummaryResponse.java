package com.neps.dto;

import java.time.LocalDateTime;

public record AiAgentAuditSummaryResponse(
        Integer days,
        Long totalCount,
        Long succeededCount,
        Long failedCount,
        Double successRatePct,
        Long averageDurationMs,
        LocalDateTime since
) {
}
