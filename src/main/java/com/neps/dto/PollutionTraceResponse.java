package com.neps.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PollutionTraceResponse(
        Long id,
        Long anomalyEventId,
        Long gridId,
        Long regionId,
        String status,
        String failureReason,
        BigDecimal targetLongitude,
        BigDecimal targetLatitude,
        LocalDateTime eventObservedAt,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        Integer timeWindowHours,
        BigDecimal maxDistanceKm,
        BigDecimal directionToleranceDeg,
        Long weatherRecordId,
        BigDecimal windDirection,
        BigDecimal windSpeed,
        List<Long> emissionRecordIds,
        List<Candidate> candidates,
        String methodVersion,
        boolean demo,
        Long generatedBy,
        LocalDateTime generatedAt
) {
    public record Candidate(
            Long emissionRecordId,
            String enterpriseCode,
            String enterpriseName,
            BigDecimal longitude,
            BigDecimal latitude,
            LocalDateTime observedAt,
            String pollutantCode,
            BigDecimal emissionValue,
            String emissionUnit,
            BigDecimal distanceKm,
            BigDecimal bearingDeg,
            BigDecimal directionDifferenceDeg,
            String evidence,
            String sourceName
    ) {
    }
}