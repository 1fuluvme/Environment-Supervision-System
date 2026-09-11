package com.neps.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DecisionDashboardResponse(
        Long regionId,
        String regionCode,
        String regionName,
        LocalDate startDate,
        LocalDate endDate,
        long confirmedEventCount,
        long openWarningCount,
        List<CountItem> warningLevelDistribution,
        long workOrderCount,
        long closedWorkOrderCount,
        BigDecimal workOrderCompletionRatePercent,
        List<CountItem> workOrderStatusDistribution,
        LocalDateTime generatedAt
) {
    public record CountItem(
            String value,
            long count
    ) {
    }
}
