package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.CreateFeedbackRequest;
import com.neps.dto.FeedbackResponse;
import com.neps.entity.AiAnalysis;
import com.neps.entity.Feedback;
import com.neps.entity.Grid;
import com.neps.entity.User;
import com.neps.mapper.AiAnalysisMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.GridMapper;
import com.neps.mapper.UserMapper;
import com.neps.service.FeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.neps.dto.AdminFeedbackResponse;
import com.neps.entity.InspectionTask;
import com.neps.mapper.InspectionTaskMapper;
import com.neps.mapper.UserRegionMapper;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

import java.util.List;

@Service
public class FeedbackServiceImpl
        extends ServiceImpl<FeedbackMapper, Feedback>
        implements FeedbackService {

    private final UserMapper userMapper;
    private final GridMapper gridMapper;
    private final AiAnalysisMapper aiAnalysisMapper;
    private static final Set<String> FEEDBACK_STATUSES =
            Set.of(
                    "PENDING_ASSIGN",
                    "CHECKING",
                    "PENDING_REVIEW",
                    "PROCESSING",
                    "COMPLETED");
    private final UserRegionMapper userRegionMapper;
    private final InspectionTaskMapper inspectionTaskMapper;

    public FeedbackServiceImpl(
            UserMapper userMapper,
            GridMapper gridMapper,
            AiAnalysisMapper aiAnalysisMapper,
            UserRegionMapper userRegionMapper,
            InspectionTaskMapper inspectionTaskMapper) {

        this.userMapper = userMapper;
        this.gridMapper = gridMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
        this.userRegionMapper = userRegionMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('PUBLIC')")
    public FeedbackResponse createFeedback(
            CreateFeedbackRequest request) {

        User submitter = currentPublicUser();

        Grid grid = gridMapper.selectOne(
                Wrappers.<Grid>lambdaQuery()
                        .eq(Grid::getId, request.gridId())
                        .eq(Grid::getEnabled, 1));

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "网格不存在或已停用");
        }

        Feedback feedback = new Feedback();
        feedback.setSubmitterId(submitter.getId());
        feedback.setGridId(grid.getId());
        feedback.setAddress(request.address().trim());
        feedback.setObservedAt(request.observedAt());
        feedback.setDescription(request.description().trim());
        feedback.setStatus("PENDING_ASSIGN");

        if (!save(feedback)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈保存失败");
        }

        AiAnalysis analysis = new AiAnalysis();
        analysis.setTargetType("FEEDBACK");
        analysis.setTargetId(feedback.getId());
        analysis.setStatus("PENDING");

        if (aiAnalysisMapper.insert(analysis) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "待分析记录创建失败");
        }

        return toResponse(getById(feedback.getId()));
    }

    @Override
    @PreAuthorize("hasRole('PUBLIC')")
    public List<FeedbackResponse> listMine() {
        User submitter = currentPublicUser();

        return lambdaQuery()
                .eq(Feedback::getSubmitterId, submitter.getId())
                .orderByDesc(Feedback::getCreatedAt)
                .orderByDesc(Feedback::getId)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('PUBLIC')")
    public FeedbackResponse getMine(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈ID必须是正整数");
        }

        User submitter = currentPublicUser();

        Feedback feedback = lambdaQuery()
                .eq(Feedback::getId, id)
                .eq(Feedback::getSubmitterId, submitter.getId())
                .one();

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "反馈不存在");
        }

        return toResponse(feedback);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<AdminFeedbackResponse> listForAdmin(
            String status,
            Long regionId,
            LocalDateTime from,
            LocalDateTime to) {

        User admin = currentAdminUser();

        String normalizedStatus =
                status == null || status.isBlank()
                        ? null
                        : status.trim()
                        .toUpperCase(Locale.ROOT);

        if (normalizedStatus != null
                && !FEEDBACK_STATUSES.contains(
                normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈状态不正确");
        }

        if (from != null
                && to != null
                && from.isAfter(to)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "开始时间不能晚于结束时间");
        }

        if (regionId != null) {
            if (regionId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "区域ID必须是正整数");
            }

            if (userRegionMapper.countAccessibleRegion(
                    admin.getId(), regionId) == 0) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "区域不在当前管理员授权范围内");
            }
        }

        // ponytail: 单机演示按条读取关联信息；
        // 数据量明显增大后再改为一次JOIN和分页查询。
        return baseMapper.selectAccessibleForAdmin(
                        admin.getId(),
                        normalizedStatus,
                        regionId,
                        from,
                        to)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public AdminFeedbackResponse getForAdmin(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈ID必须是正整数");
        }

        User admin = currentAdminUser();

        Feedback feedback = getById(id);

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "反馈不存在");
        }

        Grid grid = gridMapper.selectById(
                feedback.getGridId());

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈关联的网格不存在");
        }

        if (userRegionMapper.countAccessibleRegion(
                admin.getId(),
                grid.getRegionId()) == 0) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "反馈不在当前管理员授权范围内");
        }

        return toAdminResponse(feedback);
    }

    private User currentPublicUser() {
        String phone = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "PUBLIC")
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前公众账号不可用");
        }

        return user;
    }

    private User currentAdminUser() {
        String phone = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, "ADMIN")
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前管理员账号不可用");
        }

        return user;
    }

    private AdminFeedbackResponse toAdminResponse(
            Feedback feedback) {

        User submitter = userMapper.selectById(
                feedback.getSubmitterId());

        Grid grid = gridMapper.selectById(
                feedback.getGridId());

        AiAnalysis analysis =
                aiAnalysisMapper.selectOne(
                        Wrappers.<AiAnalysis>lambdaQuery()
                                .eq(
                                        AiAnalysis::getTargetType,
                                        "FEEDBACK")
                                .eq(
                                        AiAnalysis::getTargetId,
                                        feedback.getId()));

        InspectionTask task =
                inspectionTaskMapper.selectOne(
                        Wrappers.<InspectionTask>lambdaQuery()
                                .eq(
                                        InspectionTask::getFeedbackId,
                                        feedback.getId()));

        if (submitter == null
                || grid == null
                || analysis == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈关联数据不完整");
        }

        return new AdminFeedbackResponse(
                feedback.getId(),
                submitter.getId(),
                submitter.getPhone(),
                submitter.getDisplayName(),
                grid.getId(),
                grid.getCode(),
                grid.getName(),
                feedback.getAddress(),
                feedback.getObservedAt(),
                feedback.getDescription(),
                feedback.getStatus(),
                feedback.getPublicReply(),
                analysis.getStatus(),
                task == null ? null : task.getId(),
                task == null ? null : task.getAssigneeId(),
                task == null ? null : task.getStatus(),
                task == null ? null : task.getPriority(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    private FeedbackResponse toResponse(Feedback feedback) {
        AiAnalysis analysis = aiAnalysisMapper.selectOne(
                Wrappers.<AiAnalysis>lambdaQuery()
                        .eq(AiAnalysis::getTargetType, "FEEDBACK")
                        .eq(AiAnalysis::getTargetId, feedback.getId()));

        if (analysis == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈对应的分析记录缺失");
        }

        return new FeedbackResponse(
                feedback.getId(),
                feedback.getGridId(),
                feedback.getAddress(),
                feedback.getObservedAt(),
                feedback.getDescription(),
                feedback.getStatus(),
                feedback.getPublicReply(),
                analysis.getStatus(),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }
}
