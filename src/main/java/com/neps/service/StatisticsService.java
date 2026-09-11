package com.neps.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neps.dto.StatisticsMeasurementRow;
import com.neps.dto.StatisticsResponse;
import com.neps.dto.StatisticsResponse.LevelCount;
import com.neps.dto.StatisticsResponse.TrendPoint;
import com.neps.entity.Region;
import com.neps.entity.User;
import com.neps.mapper.MeasurementMapper;
import com.neps.mapper.RegionMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

@Service
public class StatisticsService {

    private static final Set<String> ROLES =
            Set.of("ADMIN", "DECISION");

    private static final Set<String> REPORT_TYPES =
            Set.of("DAILY", "REALTIME");

    private static final Map<Integer, String> CATEGORIES =
            Map.of(
                    1, "优",
                    2, "良",
                    3, "轻度污染",
                    4, "中度污染",
                    5, "重度污染",
                    6, "严重污染");

    private static final DateTimeFormatter HOUR_FORMAT =
            DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd HH:00");

    private final MeasurementMapper measurementMapper;
    private final RegionMapper regionMapper;
    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;

    public StatisticsService(
            MeasurementMapper measurementMapper,
            RegionMapper regionMapper,
            UserMapper userMapper,
            UserRegionMapper userRegionMapper) {

        this.measurementMapper = measurementMapper;
        this.regionMapper = regionMapper;
        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
    }

    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public StatisticsResponse query(
            Long regionId,
            LocalDate startDate,
            LocalDate endDate,
            String reportType) {

        validateRequest(
                regionId,
                startDate,
                endDate);

        User user = currentUser();
        Region region = regionMapper.selectById(regionId);

        if (region == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "区域不存在");
        }

        if (userRegionMapper.countAccessibleRegion(
                user.getId(),
                regionId) == 0) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "区域不在当前账号授权范围内");
        }

        String normalizedReportType =
                normalizeReportType(reportType);

        List<StatisticsMeasurementRow> rows =
                measurementMapper.selectStatisticsRows(
                        regionId,
                        normalizedReportType,
                        startDate.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay());

        int configuredGridCount = (int) rows.stream()
                .map(StatisticsMeasurementRow::gridId)
                .distinct()
                .count();

        List<StatisticsMeasurementRow> measurements =
                rows.stream()
                        .filter(row ->
                                row.measurementId() != null)
                        .toList();

        List<StatisticsMeasurementRow> validRows =
                measurements.stream()
                        .filter(row ->
                                Integer.valueOf(1).equals(
                                        row.validAqi()))
                        .toList();

        long goodCount = validRows.stream()
                .filter(row -> row.aqi() <= 100)
                .count();

        long pollutedCount = validRows.stream()
                .filter(row -> row.aqi() > 100)
                .count();

        int coveredGridCount = (int) validRows.stream()
                .map(StatisticsMeasurementRow::gridId)
                .distinct()
                .count();

        BigDecimal coverageRate =
                configuredGridCount == 0
                        ? null
                        : BigDecimal.valueOf(
                                coveredGridCount
                                        * 100.0
                                        / configuredGridCount)
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        List<LevelCount> distribution =
                IntStream.rangeClosed(1, 6)
                        .mapToObj(level ->
                                new LevelCount(
                                        level,
                                        CATEGORIES.get(level),
                                        validRows.stream()
                                                .filter(row ->
                                                        row.aqiLevel()
                                                                == level)
                                                .count()))
                        .toList();

        Map<String, List<StatisticsMeasurementRow>>
                rowsByPeriod = validRows.stream()
                .collect(
                        java.util.stream.Collectors.groupingBy(
                                row -> period(
                                        row.measuredAt(),
                                        normalizedReportType),
                                TreeMap::new,
                                java.util.stream.Collectors.toList()));

        List<TrendPoint> trend = rowsByPeriod
                .entrySet()
                .stream()
                .map(entry -> {
                    double average = entry.getValue()
                            .stream()
                            .mapToInt(
                                    StatisticsMeasurementRow::aqi)
                            .average()
                            .orElseThrow();

                    return new TrendPoint(
                            entry.getKey(),
                            BigDecimal.valueOf(average)
                                    .setScale(
                                            2,
                                            RoundingMode.HALF_UP),
                            entry.getValue().size());
                })
                .toList();

        return new StatisticsResponse(
                region.getId(),
                region.getCode(),
                region.getName(),
                startDate,
                endDate,
                normalizedReportType,
                "AQI",
                measurements.size(),
                validRows.size(),
                goodCount,
                pollutedCount,
                configuredGridCount,
                coveredGridCount,
                coverageRate,
                distribution,
                trend,
                LocalDateTime.now());
    }

    private void validateRequest(
            Long regionId,
            LocalDate startDate,
            LocalDate endDate) {

        if (regionId == null || regionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "区域ID必须是正整数");
        }

        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开始日期和结束日期不能为空");
        }

        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开始日期不能晚于结束日期");
        }

        if (endDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "结束日期不能晚于当前日期");
        }

        if (ChronoUnit.DAYS.between(
                startDate,
                endDate) > 365) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "统计时间范围不能超过366天");
        }
    }

    private String normalizeReportType(
            String reportType) {

        if (reportType == null
                || reportType.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "统计口径不能为空");
        }

        String normalized =
                reportType.trim().toUpperCase();

        if (!REPORT_TYPES.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "统计口径只能是DAILY或REALTIME");
        }

        return normalized;
    }

    private String period(
            LocalDateTime measuredAt,
            String reportType) {

        return "DAILY".equals(reportType)
                ? measuredAt.toLocalDate().toString()
                : measuredAt.format(HOUR_FORMAT);
    }

    private User currentUser() {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getEnabled, 1));

        if (user == null
                || !ROLES.contains(user.getRole())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账号不能访问统计数据");
        }

        return user;
    }
}
