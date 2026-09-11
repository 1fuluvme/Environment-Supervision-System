package com.neps.dto;

import org.apache.ibatis.annotations.AutomapConstructor;

import java.time.LocalDateTime;

public record StatisticsMeasurementRow(
        Long gridId,
        Long measurementId,
        LocalDateTime measuredAt,
        Integer aqi,
        Integer aqiLevel,
        Integer validAqi
) {
    @AutomapConstructor
    public StatisticsMeasurementRow {
    }
}