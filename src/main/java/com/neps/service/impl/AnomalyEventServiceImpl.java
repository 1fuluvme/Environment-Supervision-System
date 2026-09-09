package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.AnomalyEventResponse;
import com.neps.dto.ReviewAnomalyRequest;
import com.neps.entity.AnomalyEvent;
import com.neps.entity.Feedback;
import com.neps.entity.Grid;
import com.neps.entity.InspectionTask;
import com.neps.entity.Measurement;
import com.neps.entity.MeasurementReview;
import com.neps.entity.OperationLog;
import com.neps.entity.User;
import com.neps.mapper.AnomalyEventMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.GridMapper;
import com.neps.mapper.InspectionTaskMapper;
import com.neps.mapper.MeasurementMapper;
import com.neps.mapper.MeasurementReviewMapper;
import com.neps.mapper.OperationLogMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.service.AnomalyEventService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.neps.entity.Warning;
import com.neps.entity.WorkOrder;
import com.neps.mapper.WarningMapper;
import com.neps.mapper.WorkOrderMapper;

@Service
public class AnomalyEventServiceImpl
        extends ServiceImpl<AnomalyEventMapper, AnomalyEvent>
        implements AnomalyEventService {

    private static final Set<String> STATUSES =
            Set.of(
                    "PENDING_REVIEW",
                    "PROCESSING",
                    "CLOSED",
                    "EXCLUDED");

    private static final Set<String> PRIORITIES =
            Set.of(
                    "LOW",
                    "MEDIUM",
                    "HIGH");

    private final UserMapper userMapper;
    private final UserRegionMapper userRegionMapper;
    private final GridMapper gridMapper;
    private final FeedbackMapper feedbackMapper;
    private final InspectionTaskMapper inspectionTaskMapper;
    private final MeasurementMapper measurementMapper;
    private final MeasurementReviewMapper measurementReviewMapper;
    private final OperationLogMapper operationLogMapper;
    private final WarningMapper warningMapper;
    private final WorkOrderMapper workOrderMapper;

    public AnomalyEventServiceImpl(
            UserMapper userMapper,
            UserRegionMapper userRegionMapper,
            GridMapper gridMapper,
            FeedbackMapper feedbackMapper,
            InspectionTaskMapper inspectionTaskMapper,
            MeasurementMapper measurementMapper,
            MeasurementReviewMapper measurementReviewMapper,
            OperationLogMapper operationLogMapper,WarningMapper warningMapper,
            WorkOrderMapper workOrderMapper) {

        this.userMapper = userMapper;
        this.userRegionMapper = userRegionMapper;
        this.gridMapper = gridMapper;
        this.feedbackMapper = feedbackMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.measurementMapper = measurementMapper;
        this.measurementReviewMapper =
                measurementReviewMapper;
        this.operationLogMapper = operationLogMapper;
        this.warningMapper = warningMapper;
        this.workOrderMapper = workOrderMapper;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<AnomalyEventResponse> listForAdmin(
            String status,
            String priority) {

        User admin = currentAdmin();

        String normalizedStatus =
                normalize(status);

        String normalizedPriority =
                normalize(priority);

        if (normalizedStatus != null
                && !STATUSES.contains(normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "异常事件状态不正确");
        }

        if (normalizedPriority != null
                && !PRIORITIES.contains(
                normalizedPriority)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "异常事件优先级不正确");
        }

        return lambdaQuery()
                .eq(
                        normalizedStatus != null,
                        AnomalyEvent::getStatus,
                        normalizedStatus)
                .apply(
                        normalizedPriority != null,
                        "COALESCE(confirmed_priority, suggested_priority) = {0}",
                        normalizedPriority)
                .orderByDesc(AnomalyEvent::getCreatedAt)
                .orderByDesc(AnomalyEvent::getId)
                .list()
                .stream()
                .filter(event ->
                        isAccessible(admin, event))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public AnomalyEventResponse getForAdmin(
            Long eventId) {

        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "异常事件ID必须是正整数");
        }

        User admin = currentAdmin();

        AnomalyEvent event =
                getById(eventId);

        if (event == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "异常事件不存在");
        }

        requireAccess(admin, event);

        return toResponse(event);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AnomalyEventResponse review(
            Long eventId,
            ReviewAnomalyRequest request) {

        if (eventId == null || eventId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "异常事件ID必须是正整数");
        }

        User admin = currentAdmin();

        AnomalyEvent existing =
                baseMapper.selectById(eventId);

        if (existing == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "异常事件不存在");
        }

        /*
         * 加锁顺序继续保持：
         * 任务 → 反馈 → 检测 → 异常事件。
         */
        InspectionTask task =
                inspectionTaskMapper.selectOne(
                        Wrappers
                                .<InspectionTask>lambdaQuery()
                                .eq(
                                        InspectionTask::getId,
                                        existing.getTaskId())
                                .last("FOR UPDATE"));

        Feedback feedback =
                feedbackMapper.selectOne(
                        Wrappers
                                .<Feedback>lambdaQuery()
                                .eq(
                                        Feedback::getId,
                                        existing.getFeedbackId())
                                .last("FOR UPDATE"));

        Measurement measurement =
                measurementMapper.selectOne(
                        Wrappers
                                .<Measurement>lambdaQuery()
                                .eq(
                                        Measurement::getId,
                                        existing.getMeasurementId())
                                .last("FOR UPDATE"));

        AnomalyEvent event =
                baseMapper.selectOne(
                        Wrappers
                                .<AnomalyEvent>lambdaQuery()
                                .eq(
                                        AnomalyEvent::getId,
                                        eventId)
                                .last("FOR UPDATE"));

        if (task == null
                || feedback == null
                || measurement == null
                || event == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常事件关联数据不完整");
        }

        requireAccess(admin, event);

        if (!Objects.equals(
                event.getTaskId(),
                task.getId())
                || !Objects.equals(
                event.getFeedbackId(),
                feedback.getId())
                || !Objects.equals(
                event.getMeasurementId(),
                measurement.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常事件关联关系不正确");
        }

        if (!"PENDING_REVIEW".equals(
                event.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前异常事件已经复核");
        }

        String decision =
                request.decision()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        String reviewReason =
                request.reviewReason().trim();

        String publicReply =
                trimToNull(request.publicReply());

        LocalDateTime now =
                LocalDateTime.now();

        if ("CONFIRM".equals(decision)) {
            confirm(
                    event,
                    task,
                    feedback,
                    measurement,
                    admin,
                    request,
                    reviewReason,
                    publicReply,
                    now);
        } else {
            exclude(
                    event,
                    admin,
                    reviewReason,
                    now);
        }

        saveOperationLog(
                event,
                task,
                feedback,
                admin,
                decision,
                reviewReason);

        return toResponse(
                baseMapper.selectById(event.getId()));
    }

    private void confirm(
            AnomalyEvent event,
            InspectionTask task,
            Feedback feedback,
            Measurement measurement,
            User admin,
            ReviewAnomalyRequest request,
            String reviewReason,
            String publicReply,
            LocalDateTime now) {

        String confirmedPriority =
                normalize(request.confirmedPriority());

        if (confirmedPriority == null
                || !PRIORITIES.contains(
                confirmedPriority)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "确认异常时必须填写有效的最终优先级");
        }

        if (publicReply == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "确认异常时必须填写公众办理说明");
        }

        if (!"PENDING".equals(
                measurement.getReviewStatus())
                || !"PENDING_REVIEW".equals(
                task.getStatus())
                || !"PENDING_REVIEW".equals(
                feedback.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前业务状态不允许确认异常");
        }

        event.setStatus("PROCESSING");
        event.setConfirmedPriority(
                confirmedPriority);
        event.setReviewedBy(admin.getId());
        event.setReviewedAt(now);
        event.setReviewReason(reviewReason);

        measurement.setReviewStatus("APPROVED");
        task.setStatus("COMPLETED");
        feedback.setStatus("PROCESSING");
        feedback.setPublicReply(publicReply);

        if (baseMapper.updateById(event) != 1
                || measurementMapper.updateById(
                measurement) != 1
                || inspectionTaskMapper.updateById(
                task) != 1
                || feedbackMapper.updateById(
                feedback) != 1) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常确认状态更新失败");
        }

        MeasurementReview review =
                new MeasurementReview();

        review.setMeasurementId(
                measurement.getId());
        review.setReviewerId(admin.getId());
        review.setDecision("ANOMALY");
        review.setOpinion(reviewReason);
        review.setPublicReply(publicReply);
        review.setReviewedAt(now);

        if (measurementReviewMapper.insert(review) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常检测复核记录保存失败");
        }

        createDisposalSuggestion(
                event,
                feedback,
                admin,
                confirmedPriority,
                now);
    }

    private void createDisposalSuggestion(
            AnomalyEvent event,
            Feedback feedback,
            User admin,
            String confirmedPriority,
            LocalDateTime now) {

        Warning warning = new Warning();
        warning.setAnomalyEventId(event.getId());
        warning.setGridId(event.getGridId());
        warning.setWarningLevel(confirmedPriority);
        warning.setTitle("污染异常预警");
        warning.setContent(event.getTriggerReason());
        warning.setStatus("ACTIVE");
        warning.setCreatedAt(now);
        warning.setUpdatedAt(now);

        if (warningMapper.insert(warning) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "污染预警生成失败");
        }

        WorkOrder workOrder = new WorkOrder();
        workOrder.setAnomalyEventId(event.getId());
        workOrder.setFeedbackId(feedback.getId());
        workOrder.setGridId(event.getGridId());
        workOrder.setPriority(confirmedPriority);
        workOrder.setStatus("PENDING_CONFIRM");
        workOrder.setCreatedAt(now);
        workOrder.setUpdatedAt(now);

        if (workOrderMapper.insert(workOrder) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "待确认处置工单生成失败");
        }

        OperationLog log = new OperationLog();
        log.setBusinessType("WORK_ORDER");
        log.setBusinessId(workOrder.getId());
        log.setAction("CREATE_SUGGESTION");
        log.setOperatorId(admin.getId());
        log.setToStatus("PENDING_CONFIRM");
        log.setRemark("根据已确认污染异常自动生成处置工单建议");

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单生成记录保存失败");
        }
    }

    private void exclude(
            AnomalyEvent event,
            User admin,
            String reviewReason,
            LocalDateTime now) {

        event.setStatus("EXCLUDED");
        event.setConfirmedPriority(null);
        event.setReviewedBy(admin.getId());
        event.setReviewedAt(now);
        event.setReviewReason(reviewReason);

        if (baseMapper.updateById(event) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常排除状态更新失败");
        }
    }

    private void saveOperationLog(
            AnomalyEvent event,
            InspectionTask task,
            Feedback feedback,
            User admin,
            String decision,
            String reviewReason) {

        OperationLog log = new OperationLog();
        log.setBusinessType("FEEDBACK");
        log.setBusinessId(feedback.getId());
        log.setOperatorId(admin.getId());
        log.setFromAssigneeId(
                task.getAssigneeId());
        log.setToAssigneeId(
                task.getAssigneeId());
        log.setRemark(reviewReason);

        if ("CONFIRM".equals(decision)) {
            log.setAction("CONFIRM_ANOMALY");
            log.setFromStatus("PENDING_REVIEW");
            log.setToStatus("PROCESSING");
        } else {
            log.setAction("EXCLUDE_ANOMALY");
            log.setFromStatus("PENDING_REVIEW");
            log.setToStatus("PENDING_REVIEW");
        }

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常复核操作记录保存失败");
        }
    }

    private boolean isAccessible(
            User admin,
            AnomalyEvent event) {

        Grid grid =
                gridMapper.selectById(
                        event.getGridId());

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常事件关联的网格不存在");
        }

        return userRegionMapper.countAccessibleRegion(
                admin.getId(),
                grid.getRegionId()) > 0;
    }

    private void requireAccess(
            User admin,
            AnomalyEvent event) {

        if (!isAccessible(admin, event)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "异常事件不在当前管理员授权范围内");
        }
    }

    private User currentAdmin() {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User admin =
                userMapper.selectOne(
                        Wrappers.<User>lambdaQuery()
                                .eq(
                                        User::getPhone,
                                        phone)
                                .eq(
                                        User::getRole,
                                        "ADMIN")
                                .eq(
                                        User::getEnabled,
                                        1));

        if (admin == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前管理员账号不可用");
        }

        return admin;
    }

    private AnomalyEventResponse toResponse(
            AnomalyEvent event) {

        Measurement measurement =
                measurementMapper.selectById(
                        event.getMeasurementId());

        Feedback feedback =
                feedbackMapper.selectById(
                        event.getFeedbackId());

        if (measurement == null || feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常事件关联数据不完整");
        }

        return new AnomalyEventResponse(
                event.getId(),
                event.getFeedbackId(),
                event.getTaskId(),
                event.getMeasurementId(),
                event.getGridId(),
                event.getStatus(),
                event.getSuggestedPriority(),
                event.getConfirmedPriority(),
                event.getTriggerReason(),
                event.getReviewedBy(),
                event.getReviewedAt(),
                event.getReviewReason(),
                measurement.getAqi(),
                measurement.getReportType(),
                measurement.getQualityFlag(),
                feedback.getAddress(),
                feedback.getDescription(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim()
                .toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim();
    }
}
