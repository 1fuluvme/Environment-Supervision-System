package com.neps.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AqiPredictionResponse(
        Long id,
        Long regionId,
        String regionCode,
        String regionName,
        LocalDate historyStartDate,
        LocalDate historyEndDate,
        LocalDate targetDate,
        String method,
        Integer sampleCount,
        Short predictedAqi,
        Short actualAqi,
        BigDecimal absoluteError,
        List<Long> inputRecordIds,
        String sourceName,
        boolean demo,
        Long generatedBy,
        LocalDateTime generatedAt
) {
}
