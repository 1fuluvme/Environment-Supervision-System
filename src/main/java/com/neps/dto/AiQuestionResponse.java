package com.neps.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiQuestionResponse(
        String question,
        Long regionId,
        String regionName,
        LocalDate startDate,
        LocalDate endDate,
        String reportType,
        String answer,
        List<String> monitoringFacts,
        List<String> predictions,
        List<String> suggestions,
        List<Source> sources,
        String provider,
        String modelName,
        boolean demo,
        LocalDateTime generatedAt
) {
    public record Source(
            String id,
            String type,
            String description
    ) {
    }
}
