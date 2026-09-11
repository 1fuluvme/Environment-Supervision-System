package com.neps.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neps.dto.DecisionDashboardResponse;
import com.neps.dto.DecisionDashboardResponse.CountItem;
import com.neps.entity.*;
import com.neps.mapper.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DecisionDashboardService {

    private static final List<String> WARNING_LEVELS =
            List.of("LOW", "MEDIUM", "HIGH");

    private static final List<String> WORK_ORDER_STATUSES =
            List.of(
                    "PENDING_CONFIRM",
                    "PENDING",
                    "PENDING_REVIEW",
                    "CLOSED");

    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;
    private final RegionMapper regionMapper;
    private final GridMapper gridMapper;
    private final AnomalyEventMapper eventMapper;
    private final WarningMapper warningMapper;
    private final WorkOrderMapper workOrderMapper;

    public DecisionDashboardService(
            UserMapper userMapper,
            UserRegionMapper userRegionMapper,
            RegionMapper regionMapper,
            GridMapper gridMapper,
            AnomalyEventMapper eventMapper,
            WarningMapper warningMapper,
            WorkOrderMapper workOrderMapper) {

        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
        this.regionMapper = regionMapper;
        this.gridMapper = gridMapper;
        this.eventMapper = eventMapper;
        this.warningMapper = warningMapper;
        this.workOrderMapper = workOrderMapper;
    }

    @PreAuthorize("hasRole('DECISION')")
    public DecisionDashboardResponse query(
            Long regionId,
            LocalDate startDate,
            LocalDate endDate) {

        validate(regionId, startDate, endDate);

        User user = currentUser();
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

        List<Long> regionIds =
                regionMapper.selectEnabledIdsInTree(
                        regionId);

        List<Long> gridIds =
                regionIds.isEmpty()
                        ? List.of()
                        : gridMapper.selectList(
                                Wrappers.<Grid>lambdaQuery()
                                        .in(
                                                Grid::getRegionId,
                                                regionIds)
                                        .eq(
                                                Grid::getEnabled,
                                                1))
                        .stream()
                        .map(Grid::getId)
                        .toList();

        LocalDateTime from =
                startDate.atStartOfDay();
        LocalDateTime to =
                endDate.plusDays(1).atStartOfDay();

        List<AnomalyEvent> confirmedEvents =
                gridIds.isEmpty()
                        ? List.of()
                        : eventMapper.selectList(
                        Wrappers.<AnomalyEvent>lambdaQuery()
                                .in(
                                        AnomalyEvent::getGridId,
                                        gridIds)
                                .in(
                                        AnomalyEvent::getStatus,
                                        List.of(
                                                "PROCESSING",
                                                "CLOSED"))
                                .ge(
                                        AnomalyEvent::getCreatedAt,
                                        from)
                                .lt(
                                        AnomalyEvent::getCreatedAt,
                                        to));

        List<Warning> openWarnings =
                gridIds.isEmpty()
                        ? List.of()
                        : warningMapper.selectList(
                        Wrappers.<Warning>lambdaQuery()
                                .in(
                                        Warning::getGridId,
                                        gridIds)
                                .eq(
                                        Warning::getStatus,
                                        "ACTIVE")
                                .ge(
                                        Warning::getCreatedAt,
                                        from)
                                .lt(
                                        Warning::getCreatedAt,
                                        to));

        List<WorkOrder> workOrders =
                gridIds.isEmpty()
                        ? List.of()
                        : workOrderMapper.selectList(
                        Wrappers.<WorkOrder>lambdaQuery()
                                .in(
                                        WorkOrder::getGridId,
                                        gridIds)
                                .ge(
                                        WorkOrder::getCreatedAt,
                                        from)
                                .lt(
                                        WorkOrder::getCreatedAt,
                                        to));

        long closedCount = workOrders.stream()
                .filter(order ->
                        "CLOSED".equals(
                                order.getStatus()))
                .count();

        BigDecimal completionRate =
                workOrders.isEmpty()
                        ? null
                        : BigDecimal.valueOf(
                                closedCount
                                        * 100.0
                                        / workOrders.size())
                        .setScale(
                                2,
                                RoundingMode.HALF_UP);

        return new DecisionDashboardResponse(
                region.getId(),
                region.getCode(),
                region.getName(),
                startDate,
                endDate,
                confirmedEvents.size(),
                openWarnings.size(),
                distribution(
                        WARNING_LEVELS,
                        openWarnings,
                        Warning::getWarningLevel),
                workOrders.size(),
                closedCount,
                completionRate,
                distribution(
                        WORK_ORDER_STATUSES,
                        workOrders,
                        WorkOrder::getStatus),
                LocalDateTime.now());
    }

    private <T> List<CountItem> distribution(
            List<String> values,
            List<T> records,
            Function<T, String> classifier) {

        Map<String, Long> counts =
                records.stream()
                        .collect(
                                Collectors.groupingBy(
                                        classifier,
                                        Collectors.counting()));

        return values.stream()
                .map(value ->
                        new CountItem(
                                value,
                                counts.getOrDefault(
                                        value,
                                        0L)))
                .toList();
    }

    private void validate(
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
                    "查询时间范围不能超过366天");
        }
    }

    private User currentUser() {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "DECISION")
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账号不能访问决策大屏");
        }

        return user;
    }
}
