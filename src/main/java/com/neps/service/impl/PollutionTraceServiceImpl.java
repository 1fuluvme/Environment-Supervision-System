package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neps.dto.PollutionTraceGenerateRequest;
import com.neps.dto.PollutionTraceResponse;
import com.neps.dto.PollutionTraceResponse.Candidate;
import com.neps.entity.*;
import com.neps.mapper.*;
import com.neps.service.PollutionTraceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PollutionTraceServiceImpl
        extends ServiceImpl<PollutionTraceMapper, PollutionTrace>
        implements PollutionTraceService {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final Set<String> ROLES =
            Set.of("ADMIN", "DECISION");
    private static final Set<String> TRACEABLE_STATUSES =
            Set.of("PROCESSING", "CLOSED");

    private final AnomalyEventMapper anomalyEventMapper;
    private final FeedbackMapper feedbackMapper;
    private final GridMapper gridMapper;
    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;
    private final ExternalWeatherMapper weatherMapper;
    private final ExternalEmissionMapper emissionMapper;
    private final ObjectMapper objectMapper;

    private final int timeWindowHours;
    private final BigDecimal maxDistanceKm;
    private final BigDecimal directionToleranceDeg;
    private final String methodVersion;

    public PollutionTraceServiceImpl(
            AnomalyEventMapper anomalyEventMapper,
            FeedbackMapper feedbackMapper,
            GridMapper gridMapper,
            UserMapper userMapper,
            UserRegionMapper userRegionMapper,
            ExternalWeatherMapper weatherMapper,
            ExternalEmissionMapper emissionMapper,
            ObjectMapper objectMapper,
            @Value("${app.trace.time-window-hours}")
            int timeWindowHours,
            @Value("${app.trace.max-distance-km}")
            BigDecimal maxDistanceKm,
            @Value("${app.trace.direction-tolerance-deg}")
            BigDecimal directionToleranceDeg,
            @Value("${app.trace.method-version}")
            String methodVersion) {

        this.anomalyEventMapper = anomalyEventMapper;
        this.feedbackMapper = feedbackMapper;
        this.gridMapper = gridMapper;
        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
        this.weatherMapper = weatherMapper;
        this.emissionMapper = emissionMapper;
        this.objectMapper = objectMapper;
        this.timeWindowHours = timeWindowHours;
        this.maxDistanceKm = maxDistanceKm;
        this.directionToleranceDeg =
                directionToleranceDeg;
        this.methodVersion = methodVersion;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public PollutionTraceResponse generate(
            PollutionTraceGenerateRequest request) {

        User user = currentUser();
        TraceContext context =
                context(request.anomalyEventId());

        requireAccess(user, context.grid());

        if (!TRACEABLE_STATUSES.contains(
                context.event().getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有已确认的异常事件可以进行疑似溯源");
        }

        byte demo = Boolean.TRUE.equals(request.demo())
                ? (byte) 1
                : (byte) 0;

        boolean exists = lambdaQuery()
                .eq(
                        PollutionTrace::getAnomalyEventId,
                        context.event().getId())
                .eq(
                        PollutionTrace::getMethodVersion,
                        methodVersion)
                .eq(
                        PollutionTrace::getIsDemo,
                        demo)
                .count() > 0;

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该异常事件的相同版本溯源已经生成");
        }

        Feedback feedback = context.feedback();
        LocalDateTime observedAt =
                feedback.getObservedAt();
        LocalDateTime windowStart =
                observedAt.minusHours(timeWindowHours);
        LocalDateTime windowEnd =
                observedAt.plusHours(timeWindowHours);

        PollutionTrace trace = new PollutionTrace();
        trace.setAnomalyEventId(
                context.event().getId());
        trace.setTargetLongitude(
                feedback.getLongitude());
        trace.setTargetLatitude(
                feedback.getLatitude());
        trace.setEventObservedAt(observedAt);
        trace.setWindowStart(windowStart);
        trace.setWindowEnd(windowEnd);
        trace.setTimeWindowHours(timeWindowHours);
        trace.setMaxDistanceKm(maxDistanceKm);
        trace.setDirectionToleranceDeg(
                directionToleranceDeg);
        trace.setMethodVersion(methodVersion);
        trace.setIsDemo(demo);
        trace.setGeneratedBy(user.getId());
        trace.setGeneratedAt(LocalDateTime.now());

        if (feedback.getLongitude() == null
                || feedback.getLatitude() == null) {

            return saveInsufficient(
                    trace,
                    context,
                    "异常反馈缺少经纬度，不能进行空间筛选");
        }

        ExternalWeather weather = weatherMapper
                .selectList(
                        Wrappers.<ExternalWeather>lambdaQuery()
                                .eq(
                                        ExternalWeather::getRegionId,
                                        context.grid().getRegionId())
                                .eq(
                                        ExternalWeather::getQualityFlag,
                                        "VALID")
                                .eq(
                                        ExternalWeather::getIsDemo,
                                        demo)
                                .between(
                                        ExternalWeather::getObservedAt,
                                        windowStart,
                                        windowEnd))
                .stream()
                .min(Comparator.comparingLong(item ->
                        Math.abs(Duration.between(
                                observedAt,
                                item.getObservedAt()
                        ).toSeconds())))
                .orElse(null);

        if (weather == null) {
            return saveInsufficient(
                    trace,
                    context,
                    "搜索时间窗内没有有效气象记录");
        }

        trace.setWeatherRecordId(weather.getId());
        trace.setWindDirection(
                weather.getWindDirection());
        trace.setWindSpeed(weather.getWindSpeed());

        if (weather.getWindSpeed() == null
                || weather.getWindSpeed()
                .compareTo(BigDecimal.ZERO) <= 0) {

            return saveInsufficient(
                    trace,
                    context,
                    "气象记录风速为0，无法判断上风向");
        }

        // ponytail: 最多读取500条时间重合记录；数据量超过后改为数据库空间索引查询。
        List<ExternalEmission> emissions =
                emissionMapper.selectList(
                        Wrappers.<ExternalEmission>lambdaQuery()
                                .eq(
                                        ExternalEmission::getRegionId,
                                        context.grid().getRegionId())
                                .eq(
                                        ExternalEmission::getQualityFlag,
                                        "VALID")
                                .eq(
                                        ExternalEmission::getIsDemo,
                                        demo)
                                .between(
                                        ExternalEmission::getObservedAt,
                                        windowStart,
                                        windowEnd)
                                .orderByDesc(
                                        ExternalEmission::getObservedAt)
                                .last("LIMIT 500"));

        if (emissions.isEmpty()) {
            return saveInsufficient(
                    trace,
                    context,
                    "搜索时间窗内没有有效排污来源记录");
        }

        trace.setEmissionRecordIds(
                emissions.stream()
                        .map(item ->
                                item.getId().toString())
                        .collect(
                                Collectors.joining(",")));

        double targetLongitude =
                feedback.getLongitude().doubleValue();
        double targetLatitude =
                feedback.getLatitude().doubleValue();
        double windDirection =
                weather.getWindDirection().doubleValue();

        List<Candidate> candidates = emissions.stream()
                .map(emission -> candidate(
                        emission,
                        targetLongitude,
                        targetLatitude,
                        windDirection))
                .filter(Objects::nonNull)
                .sorted(
                        Comparator.comparing(
                                        Candidate::directionDifferenceDeg)
                                .thenComparing(
                                        Candidate::distanceKm))
                .toList();

        try {
            trace.setCandidateSnapshot(
                    objectMapper.writeValueAsString(
                            candidates));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "候选来源快照生成失败",
                    exception);
        }

        trace.setStatus("SUCCEEDED");
        trace.setFailureReason(null);
        saveTrace(trace);

        return toResponse(trace, context);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public List<PollutionTraceResponse> list(
            Long anomalyEventId) {

        User user = currentUser();

        if (anomalyEventId != null
                && anomalyEventId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "异常事件ID必须是正整数");
        }

        return lambdaQuery()
                .eq(
                        anomalyEventId != null,
                        PollutionTrace::getAnomalyEventId,
                        anomalyEventId)
                .orderByDesc(
                        PollutionTrace::getGeneratedAt)
                .orderByDesc(PollutionTrace::getId)
                .last("LIMIT 200")
                .list()
                .stream()
                .map(trace ->
                        Map.entry(
                                trace,
                                context(
                                        trace.getAnomalyEventId())))
                .filter(entry ->
                        canAccess(
                                user,
                                entry.getValue().grid()))
                .map(entry ->
                        toResponse(
                                entry.getKey(),
                                entry.getValue()))
                .toList();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public PollutionTraceResponse get(
            Long traceId) {

        if (traceId == null || traceId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "溯源结果ID必须是正整数");
        }

        User user = currentUser();
        PollutionTrace trace = getById(traceId);

        if (trace == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "疑似溯源结果不存在");
        }

        TraceContext context =
                context(trace.getAnomalyEventId());
        requireAccess(user, context.grid());

        return toResponse(trace, context);
    }

    private Candidate candidate(
            ExternalEmission emission,
            double targetLongitude,
            double targetLatitude,
            double windDirection) {

        double longitude =
                emission.getLongitude().doubleValue();
        double latitude =
                emission.getLatitude().doubleValue();

        double distance = distanceKm(
                targetLongitude,
                targetLatitude,
                longitude,
                latitude);

        if (distance > maxDistanceKm.doubleValue()) {
            return null;
        }

        double bearing = bearingDeg(
                targetLongitude,
                targetLatitude,
                longitude,
                latitude);

        double difference =
                directionDifference(
                        bearing,
                        windDirection);

        if (difference
                > directionToleranceDeg.doubleValue()) {

            return null;
        }

        BigDecimal roundedDistance = decimal(distance);
        BigDecimal roundedBearing = decimal(bearing);
        BigDecimal roundedDifference =
                decimal(difference);

        String evidence =
                "排污记录与异常时间重合，距离"
                        + roundedDistance
                        + "km，来源方位"
                        + roundedBearing
                        + "°，与风向差"
                        + roundedDifference
                        + "°";

        return new Candidate(
                emission.getId(),
                emission.getEnterpriseCode(),
                emission.getEnterpriseName(),
                emission.getLongitude(),
                emission.getLatitude(),
                emission.getObservedAt(),
                emission.getPollutantCode(),
                emission.getEmissionValue(),
                emission.getEmissionUnit(),
                roundedDistance,
                roundedBearing,
                roundedDifference,
                evidence,
                emission.getSourceName());
    }

    private double distanceKm(
            double fromLongitude,
            double fromLatitude,
            double toLongitude,
            double toLatitude) {

        double latitudeDelta =
                Math.toRadians(
                        toLatitude - fromLatitude);
        double longitudeDelta =
                Math.toRadians(
                        toLongitude - fromLongitude);

        double fromLatitudeRad =
                Math.toRadians(fromLatitude);
        double toLatitudeRad =
                Math.toRadians(toLatitude);

        double value =
                Math.sin(latitudeDelta / 2)
                        * Math.sin(latitudeDelta / 2)
                        + Math.cos(fromLatitudeRad)
                        * Math.cos(toLatitudeRad)
                        * Math.sin(longitudeDelta / 2)
                        * Math.sin(longitudeDelta / 2);

        return EARTH_RADIUS_KM
                * 2
                * Math.atan2(
                Math.sqrt(value),
                Math.sqrt(1 - value));
    }

    private double bearingDeg(
            double fromLongitude,
            double fromLatitude,
            double toLongitude,
            double toLatitude) {

        double fromLatitudeRad =
                Math.toRadians(fromLatitude);
        double toLatitudeRad =
                Math.toRadians(toLatitude);
        double longitudeDelta =
                Math.toRadians(
                        toLongitude - fromLongitude);

        double y = Math.sin(longitudeDelta)
                * Math.cos(toLatitudeRad);

        double x = Math.cos(fromLatitudeRad)
                * Math.sin(toLatitudeRad)
                - Math.sin(fromLatitudeRad)
                * Math.cos(toLatitudeRad)
                * Math.cos(longitudeDelta);

        return (Math.toDegrees(
                Math.atan2(y, x)) + 360) % 360;
    }

    private double directionDifference(
            double bearing,
            double windDirection) {

        return Math.abs(
                (bearing - windDirection + 540)
                        % 360 - 180);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private PollutionTraceResponse saveInsufficient(
            PollutionTrace trace,
            TraceContext context,
            String reason) {

        trace.setStatus("INSUFFICIENT");
        trace.setFailureReason(reason);
        trace.setCandidateSnapshot(null);
        saveTrace(trace);

        return toResponse(trace, context);
    }

    private void saveTrace(PollutionTrace trace) {
        try {
            if (!save(trace)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "疑似溯源结果保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该异常事件的相同版本溯源已经生成");
        }
    }

    private TraceContext context(
            Long anomalyEventId) {

        AnomalyEvent event =
                anomalyEventMapper.selectById(
                        anomalyEventId);

        if (event == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "异常事件不存在");
        }

        Feedback feedback =
                feedbackMapper.selectById(
                        event.getFeedbackId());
        Grid grid =
                gridMapper.selectById(
                        event.getGridId());

        if (feedback == null || grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常事件关联数据不完整");
        }

        return new TraceContext(
                event,
                feedback,
                grid);
    }

    private void requireAccess(
            User user,
            Grid grid) {

        if (!canAccess(user, grid)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "异常事件不在当前账号授权范围内");
        }
    }

    private boolean canAccess(
            User user,
            Grid grid) {

        return userRegionMapper
                .countAccessibleRegion(
                        user.getId(),
                        grid.getRegionId()) > 0;
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
                    "当前账号不能访问疑似溯源");
        }

        return user;
    }

    private PollutionTraceResponse toResponse(
            PollutionTrace trace,
            TraceContext context) {

        List<Candidate> candidates = List.of();

        if (trace.getCandidateSnapshot() != null) {
            try {
                candidates = objectMapper.readValue(
                        trace.getCandidateSnapshot(),
                        new TypeReference<>() {
                        });
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "候选来源快照读取失败",
                        exception);
            }
        }

        List<Long> emissionIds =
                trace.getEmissionRecordIds() == null
                        || trace.getEmissionRecordIds()
                        .isBlank()
                        ? List.of()
                        : Arrays.stream(
                                trace.getEmissionRecordIds()
                                        .split(","))
                        .map(Long::valueOf)
                        .toList();

        return new PollutionTraceResponse(
                trace.getId(),
                trace.getAnomalyEventId(),
                context.grid().getId(),
                context.grid().getRegionId(),
                trace.getStatus(),
                trace.getFailureReason(),
                trace.getTargetLongitude(),
                trace.getTargetLatitude(),
                trace.getEventObservedAt(),
                trace.getWindowStart(),
                trace.getWindowEnd(),
                trace.getTimeWindowHours(),
                trace.getMaxDistanceKm(),
                trace.getDirectionToleranceDeg(),
                trace.getWeatherRecordId(),
                trace.getWindDirection(),
                trace.getWindSpeed(),
                emissionIds,
                candidates,
                trace.getMethodVersion(),
                Byte.valueOf((byte) 1)
                        .equals(trace.getIsDemo()),
                trace.getGeneratedBy(),
                trace.getGeneratedAt());
    }

    private record TraceContext(
            AnomalyEvent event,
            Feedback feedback,
            Grid grid) {
    }
}
