package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.WarningResponse;
import com.neps.entity.Grid;
import com.neps.entity.User;
import com.neps.entity.Warning;
import com.neps.mapper.GridMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.mapper.WarningMapper;
import com.neps.service.WarningService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WarningServiceImpl
        extends ServiceImpl<WarningMapper, Warning>
        implements WarningService {

    private static final Set<String> STATUSES =
            Set.of("ACTIVE", "CLOSED");

    private static final Set<String> LEVELS =
            Set.of("LOW", "MEDIUM", "HIGH");

    private final UserMapper userMapper;
    private final GridMapper gridMapper;
    private final UserRegionMapper userRegionMapper;

    public WarningServiceImpl(
            UserMapper userMapper,
            GridMapper gridMapper,
            UserRegionMapper userRegionMapper) {

        this.userMapper = userMapper;
        this.gridMapper = gridMapper;
        this.userRegionMapper = userRegionMapper;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<WarningResponse> listForAdmin(
            String status,
            String level) {

        User admin = currentAdmin();

        String normalizedStatus = normalize(status);
        String normalizedLevel = normalize(level);

        if (normalizedStatus != null
                && !STATUSES.contains(normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预警状态不正确");
        }

        if (normalizedLevel != null
                && !LEVELS.contains(normalizedLevel)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预警级别不正确");
        }

        return lambdaQuery()
                .eq(
                        normalizedStatus != null,
                        Warning::getStatus,
                        normalizedStatus)
                .eq(
                        normalizedLevel != null,
                        Warning::getWarningLevel,
                        normalizedLevel)
                .orderByDesc(Warning::getCreatedAt)
                .orderByDesc(Warning::getId)
                .list()
                .stream()
                .filter(warning ->
                        isAccessible(admin, warning))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public WarningResponse getForAdmin(
            Long warningId) {

        if (warningId == null || warningId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "预警ID必须是正整数");
        }

        User admin = currentAdmin();
        Warning warning = getById(warningId);

        if (warning == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "预警不存在");
        }

        if (!isAccessible(admin, warning)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "预警不在当前管理员授权范围内");
        }

        return toResponse(warning);
    }

    private boolean isAccessible(
            User admin,
            Warning warning) {

        Grid grid =
                gridMapper.selectById(
                        warning.getGridId());

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "预警关联的网格不存在");
        }

        return userRegionMapper.countAccessibleRegion(
                admin.getId(),
                grid.getRegionId()) > 0;
    }

    private User currentAdmin() {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User admin = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "ADMIN")
                        .eq(User::getEnabled, 1));

        if (admin == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前管理员账号不可用");
        }

        return admin;
    }

    private WarningResponse toResponse(
            Warning warning) {

        return new WarningResponse(
                warning.getId(),
                warning.getAnomalyEventId(),
                warning.getGridId(),
                warning.getWarningLevel(),
                warning.getTitle(),
                warning.getContent(),
                warning.getStatus(),
                warning.getCreatedAt(),
                warning.getUpdatedAt(),
                warning.getClosedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim()
                .toUpperCase(Locale.ROOT);
    }
}
