package com.neps.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StatisticsResponse(
        Long regionId,
        String regionCode,
        String regionName,
        LocalDate startDate,
        LocalDate endDate,
        String reportType,
        String unit,
        long measurementCount,
        long validAqiCount,
        long goodCount,
        long pollutedCount,
        int configuredGridCount,
        int coveredGridCount,
        BigDecimal coverageRatePercent,
        List<LevelCount> levelDistribution,
        List<TrendPoint> trend,
        LocalDateTime generatedAt
) {
    public record LevelCount(
            int level,
            String category,
            long count
    ) {
    }

    public record TrendPoint(
            String period,
            BigDecimal averageAqi,
            long validRecordCount
    ) {
    }
}
