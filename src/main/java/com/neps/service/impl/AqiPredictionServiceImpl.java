package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.AqiPredictionGenerateRequest;
import com.neps.dto.AqiPredictionResponse;
import com.neps.entity.AqiPrediction;
import com.neps.entity.ExternalAqi;
import com.neps.entity.Region;
import com.neps.entity.User;
import com.neps.mapper.AqiPredictionMapper;
import com.neps.mapper.ExternalAqiMapper;
import com.neps.mapper.RegionMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.service.AqiPredictionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class AqiPredictionServiceImpl
        extends ServiceImpl<AqiPredictionMapper, AqiPrediction>
        implements AqiPredictionService {

    private static final String METHOD = "MA7";
    private static final int WINDOW_SIZE = 7;
    private static final Set<String> ALLOWED_ROLES =
            Set.of("ADMIN", "DECISION");

    private final ExternalAqiMapper externalAqiMapper;
    private final RegionMapper regionMapper;
    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;

    public AqiPredictionServiceImpl(
            ExternalAqiMapper externalAqiMapper,
            RegionMapper regionMapper,
            UserMapper userMapper,
            UserRegionMapper userRegionMapper) {

        this.externalAqiMapper = externalAqiMapper;
        this.regionMapper = regionMapper;
        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
    }

    @Override
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public AqiPredictionResponse generate(
            AqiPredictionGenerateRequest request) {

        User user = currentUser();
        Region region = accessibleRegion(
                user,
                request.regionId());

        LocalDate targetDate = request.targetDate();

        if (targetDate.isAfter(
                LocalDate.now().plusDays(1))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "最多只能预测未来一天");
        }

        String sourceName =
                request.sourceName().trim();
        byte isDemo =
                Boolean.TRUE.equals(request.demo())
                        ? (byte) 1
                        : (byte) 0;

        boolean exists = lambdaQuery()
                .eq(
                        AqiPrediction::getRegionId,
                        region.getId())
                .eq(
                        AqiPrediction::getTargetDate,
                        targetDate)
                .eq(
                        AqiPrediction::getMethod,
                        METHOD)
                .eq(
                        AqiPrediction::getSourceName,
                        sourceName)
                .eq(
                        AqiPrediction::getIsDemo,
                        isDemo)
                .count() > 0;

        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "相同范围的预测已经生成");
        }

        List<ExternalAqi> inputs =
                externalAqiMapper.selectRecentValidDaily(
                        region.getId(),
                        targetDate,
                        sourceName,
                        isDemo);

        if (inputs.size() < WINDOW_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "有效DAILY数据不足7个完整日，不能生成预测");
        }

        int total = inputs.stream()
                .mapToInt(ExternalAqi::getAqi)
                .sum();

        short predictedAqi =
                (short) Math.round(
                        total / (double) WINDOW_SIZE);

        ExternalAqi actual =
                externalAqiMapper.selectOne(
                        Wrappers.<ExternalAqi>lambdaQuery()
                                .eq(
                                        ExternalAqi::getRegionId,
                                        region.getId())
                                .eq(
                                        ExternalAqi::getObservedAt,
                                        targetDate.atStartOfDay())
                                .eq(
                                        ExternalAqi::getReportType,
                                        "DAILY")
                                .eq(
                                        ExternalAqi::getQualityFlag,
                                        "VALID")
                                .eq(
                                        ExternalAqi::getSourceName,
                                        sourceName)
                                .eq(
                                        ExternalAqi::getIsDemo,
                                        isDemo)
                                .orderByDesc(
                                        ExternalAqi::getId)
                                .last("LIMIT 1"));

        LocalDate historyStartDate = inputs.stream()
                .map(data ->
                        data.getObservedAt().toLocalDate())
                .min(LocalDate::compareTo)
                .orElseThrow();

        LocalDate historyEndDate = inputs.stream()
                .map(data ->
                        data.getObservedAt().toLocalDate())
                .max(LocalDate::compareTo)
                .orElseThrow();

        String inputRecordIds = inputs.stream()
                .map(data ->
                        data.getId().toString())
                .sorted()
                .reduce((left, right) ->
                        left + "," + right)
                .orElseThrow();

        AqiPrediction prediction =
                new AqiPrediction();

        prediction.setRegionId(region.getId());
        prediction.setHistoryStartDate(
                historyStartDate);
        prediction.setHistoryEndDate(
                historyEndDate);
        prediction.setTargetDate(targetDate);
        prediction.setMethod(METHOD);
        prediction.setSampleCount(WINDOW_SIZE);
        prediction.setPredictedAqi(predictedAqi);
        prediction.setInputRecordIds(
                inputRecordIds);
        prediction.setSourceName(sourceName);
        prediction.setIsDemo(isDemo);
        prediction.setGeneratedBy(user.getId());
        prediction.setGeneratedAt(
                LocalDateTime.now());

        if (actual != null) {
            prediction.setActualAqi(
                    actual.getAqi());
            prediction.setAbsoluteError(
                    BigDecimal.valueOf(
                            Math.abs(
                                    actual.getAqi()
                                            - predictedAqi)));
        }

        try {
            if (!save(prediction)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "AQI预测保存失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "相同范围的预测已经生成");
        }

        return toResponse(prediction, region);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public List<AqiPredictionResponse> list(
            Long regionId) {

        User user = currentUser();
        List<Long> accessibleIds =
                userRegionMapper.selectMyRegions(
                                user.getId())
                        .stream()
                        .map(Region::getId)
                        .toList();

        if (regionId != null) {
            if (regionId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "区域ID必须是正整数");
            }

            accessibleRegion(user, regionId);
            accessibleIds = List.of(regionId);
        }

        if (accessibleIds.isEmpty()) {
            return List.of();
        }

        return lambdaQuery()
                .in(
                        AqiPrediction::getRegionId,
                        accessibleIds)
                .orderByDesc(
                        AqiPrediction::getGeneratedAt)
                .orderByDesc(
                        AqiPrediction::getId)
                .last("LIMIT 200")
                .list()
                .stream()
                .map(prediction -> {
                    Region region = regionMapper.selectById(
                            prediction.getRegionId());

                    if (region == null) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "预测关联区域不存在");
                    }

                    return toResponse(
                            prediction,
                            region);
                })
                .toList();
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN','DECISION')")
    public AqiPredictionResponse get(
            Long predictionId) {

        if (predictionId == null
                || predictionId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预测记录ID必须是正整数");
        }

        User user = currentUser();
        AqiPrediction prediction =
                getById(predictionId);

        if (prediction == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "AQI预测记录不存在");
        }

        Region region = accessibleRegion(
                user,
                prediction.getRegionId());

        return toResponse(prediction, region);
    }

    private Region accessibleRegion(
            User user,
            Long regionId) {

        Region region =
                regionMapper.selectById(regionId);

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

        return region;
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
                || !ALLOWED_ROLES.contains(
                user.getRole())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账号不能访问AQI预测");
        }

        return user;
    }

    private AqiPredictionResponse toResponse(
            AqiPrediction prediction,
            Region region) {

        List<Long> inputIds =
                Arrays.stream(
                                prediction
                                        .getInputRecordIds()
                                        .split(","))
                        .map(Long::valueOf)
                        .toList();

        return new AqiPredictionResponse(
                prediction.getId(),
                prediction.getRegionId(),
                region.getCode(),
                region.getName(),
                prediction.getHistoryStartDate(),
                prediction.getHistoryEndDate(),
                prediction.getTargetDate(),
                prediction.getMethod(),
                prediction.getSampleCount(),
                prediction.getPredictedAqi(),
                prediction.getActualAqi(),
                prediction.getAbsoluteError(),
                inputIds,
                prediction.getSourceName(),
                Byte.valueOf((byte) 1).equals(
                        prediction.getIsDemo()),
                prediction.getGeneratedBy(),
                prediction.getGeneratedAt());
    }
}
