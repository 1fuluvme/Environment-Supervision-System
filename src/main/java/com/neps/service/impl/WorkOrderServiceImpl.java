package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.AssignWorkOrderRequest;
import com.neps.dto.WorkOrderResponse;
import com.neps.entity.AnomalyEvent;
import com.neps.entity.Feedback;
import com.neps.entity.Grid;
import com.neps.entity.OperationLog;
import com.neps.entity.User;
import com.neps.entity.UserGrid;
import com.neps.entity.WorkOrder;
import com.neps.mapper.AnomalyEventMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.GridMapper;
import com.neps.mapper.OperationLogMapper;
import com.neps.mapper.UserGridMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.mapper.WorkOrderMapper;
import com.neps.service.WorkOrderService;
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

import com.neps.dto.SubmitWorkOrderResultRequest;

import com.neps.dto.ReviewWorkOrderRequest;
import com.neps.entity.Warning;
import com.neps.entity.WorkOrderReview;
import com.neps.mapper.WarningMapper;
import com.neps.mapper.WorkOrderReviewMapper;

@Service
public class WorkOrderServiceImpl
        extends ServiceImpl<WorkOrderMapper, WorkOrder>
        implements WorkOrderService {

    private static final Set<String> STATUSES =
            Set.of(
                    "PENDING_CONFIRM",
                    "PENDING",
                    "PENDING_REVIEW",
                    "CLOSED");

    private static final Set<String> PRIORITIES =
            Set.of("LOW", "MEDIUM", "HIGH");

    private final UserMapper userMapper;
    private final UserGridMapper userGridMapper;
    private final UserRegionMapper userRegionMapper;
    private final GridMapper gridMapper;
    private final FeedbackMapper feedbackMapper;
    private final AnomalyEventMapper anomalyEventMapper;
    private final OperationLogMapper operationLogMapper;
    private final WarningMapper warningMapper;
    private final WorkOrderReviewMapper workOrderReviewMapper;

    public WorkOrderServiceImpl(
            UserMapper userMapper,
            UserGridMapper userGridMapper,
            UserRegionMapper userRegionMapper,
            GridMapper gridMapper,
            FeedbackMapper feedbackMapper,
            AnomalyEventMapper anomalyEventMapper,
            OperationLogMapper operationLogMapper,
            WarningMapper warningMapper,
            WorkOrderReviewMapper workOrderReviewMapper) {

        this.userMapper = userMapper;
        this.userGridMapper = userGridMapper;
        this.userRegionMapper = userRegionMapper;
        this.gridMapper = gridMapper;
        this.feedbackMapper = feedbackMapper;
        this.anomalyEventMapper = anomalyEventMapper;
        this.operationLogMapper = operationLogMapper;
        this.warningMapper = warningMapper;
        this.workOrderReviewMapper = workOrderReviewMapper;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkOrderResponse> listForAdmin(
            String status,
            String priority) {

        User admin = currentUser("ADMIN");

        String normalizedStatus = normalize(status);
        String normalizedPriority = normalize(priority);

        if (normalizedStatus != null
                && !STATUSES.contains(normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "工单状态不正确");
        }

        if (normalizedPriority != null
                && !PRIORITIES.contains(normalizedPriority)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "工单优先级不正确");
        }

        return lambdaQuery()
                .eq(
                        normalizedStatus != null,
                        WorkOrder::getStatus,
                        normalizedStatus)
                .eq(
                        normalizedPriority != null,
                        WorkOrder::getPriority,
                        normalizedPriority)
                .orderByDesc(WorkOrder::getCreatedAt)
                .orderByDesc(WorkOrder::getId)
                .list()
                .stream()
                .filter(order -> isAccessible(admin, order))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public WorkOrderResponse getForAdmin(
            Long workOrderId) {

        requirePositiveId(workOrderId);

        User admin = currentUser("ADMIN");
        WorkOrder order = getById(workOrderId);

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "处置工单不存在");
        }

        requireAccess(admin, order);

        return toResponse(order);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WorkOrderResponse assign(
            Long workOrderId,
            AssignWorkOrderRequest request) {

        requirePositiveId(workOrderId);

        User admin = currentUser("ADMIN");

        WorkOrder order = baseMapper.selectOne(
                Wrappers.<WorkOrder>lambdaQuery()
                        .eq(
                                WorkOrder::getId,
                                workOrderId)
                        .last("FOR UPDATE"));

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "处置工单不存在");
        }

        Grid grid = gridMapper.selectOne(
                Wrappers.<Grid>lambdaQuery()
                        .eq(Grid::getId, order.getGridId())
                        .last("FOR UPDATE"));

        if (grid == null
                || !Byte.valueOf((byte) 1)
                .equals(grid.getEnabled())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "工单所属网格不存在或已停用");
        }

        requireAccess(admin, order);

        User worker = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(
                                User::getId,
                                request.assigneeId())
                        .last("FOR UPDATE"));

        if (worker == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "网格员不存在");
        }

        if (!"GRID".equals(worker.getRole())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "目标用户必须是网格员");
        }

        if (!Byte.valueOf((byte) 1)
                .equals(worker.getEnabled())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "不能指派给已停用的网格员");
        }

        Long responsibleCount =
                userGridMapper.selectCount(
                        Wrappers
                                .<UserGrid>lambdaQuery()
                                .eq(
                                        UserGrid::getUserId,
                                        worker.getId())
                                .eq(
                                        UserGrid::getGridId,
                                        order.getGridId()));

        if (responsibleCount == null
                || responsibleCount == 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该网格员不负责工单所属网格");
        }

        String requirement =
                request.requirement().trim();

        // 重复点击同一次指派时直接返回现有结果。
        if ("PENDING".equals(order.getStatus())
                && Objects.equals(
                order.getAssigneeId(),
                worker.getId())
                && Objects.equals(
                order.getRequirement(),
                requirement)) {

            return toResponse(order);
        }

        if (!"PENDING_CONFIRM".equals(
                order.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前工单状态不允许指派");
        }

        LocalDateTime now = LocalDateTime.now();

        order.setAssigneeId(worker.getId());
        order.setAssignedBy(admin.getId());
        order.setRequirement(requirement);
        order.setStatus("PENDING");
        order.setAssignedAt(now);
        order.setUpdatedAt(now);

        if (baseMapper.updateById(order) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "处置工单指派失败");
        }

        OperationLog log = new OperationLog();
        log.setBusinessType("WORK_ORDER");
        log.setBusinessId(order.getId());
        log.setAction("ASSIGN");
        log.setOperatorId(admin.getId());
        log.setFromStatus("PENDING_CONFIRM");
        log.setToStatus("PENDING");
        log.setToAssigneeId(worker.getId());
        log.setRemark(requirement);

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单指派记录保存失败");
        }

        return toResponse(order);
    }

    @Override
    @PreAuthorize("hasRole('GRID')")
    public List<WorkOrderResponse> listMine() {

        User worker = currentUser("GRID");

        return lambdaQuery()
                .eq(
                        WorkOrder::getAssigneeId,
                        worker.getId())
                .orderByDesc(WorkOrder::getAssignedAt)
                .orderByDesc(WorkOrder::getId)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('GRID')")
    public WorkOrderResponse getMine(
            Long workOrderId) {

        requirePositiveId(workOrderId);

        User worker = currentUser("GRID");

        WorkOrder order = lambdaQuery()
                .eq(WorkOrder::getId, workOrderId)
                .eq(
                        WorkOrder::getAssigneeId,
                        worker.getId())
                .one();

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "处置工单不存在");
        }

        return toResponse(order);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('GRID')")
    public WorkOrderResponse submitResult(
            Long workOrderId,
            SubmitWorkOrderResultRequest request) {

        requirePositiveId(workOrderId);

        User worker = currentUser("GRID");

        WorkOrder order = baseMapper.selectOne(
                Wrappers.<WorkOrder>lambdaQuery()
                        .eq(
                                WorkOrder::getId,
                                workOrderId)
                        .last("FOR UPDATE"));

        if (order == null
                || !Objects.equals(
                order.getAssigneeId(),
                worker.getId())) {

            // 对非负责人返回404，避免泄露别人的工单。
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "处置工单不存在");
        }

        if (!"PENDING".equals(order.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前工单状态不允许提交处置结果");
        }

        if (order.getAssignedAt() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单缺少指派时间");
        }

        if (request.handledAt()
                .isBefore(order.getAssignedAt())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "处理时间不能早于工单指派时间");
        }

        LocalDateTime now = LocalDateTime.now();

        order.setHandledAt(request.handledAt());
        order.setMeasures(request.measures().trim());
        order.setResult(request.result().trim());
        order.setSubmittedAt(now);
        order.setStatus("PENDING_REVIEW");
        order.setUpdatedAt(now);

        if (baseMapper.updateById(order) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "处置结果提交失败");
        }

        OperationLog log = new OperationLog();
        log.setBusinessType("WORK_ORDER");
        log.setBusinessId(order.getId());
        log.setAction("SUBMIT_RESULT");
        log.setOperatorId(worker.getId());
        log.setFromStatus("PENDING");
        log.setToStatus("PENDING_REVIEW");
        log.setFromAssigneeId(worker.getId());
        log.setToAssigneeId(worker.getId());
        log.setRemark("网格员提交处置结果");

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "处置结果操作记录保存失败");
        }

        return toResponse(order);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public WorkOrderResponse review(
            Long workOrderId,
            ReviewWorkOrderRequest request) {

        requirePositiveId(workOrderId);

        User admin = currentUser("ADMIN");

        WorkOrder order = baseMapper.selectOne(
                Wrappers.<WorkOrder>lambdaQuery()
                        .eq(
                                WorkOrder::getId,
                                workOrderId)
                        .last("FOR UPDATE"));

        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "处置工单不存在");
        }

        requireAccess(admin, order);

        if (!"PENDING_REVIEW".equals(
                order.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前工单状态不允许复核");
        }

        Feedback feedback = feedbackMapper.selectOne(
                Wrappers.<Feedback>lambdaQuery()
                        .eq(
                                Feedback::getId,
                                order.getFeedbackId())
                        .last("FOR UPDATE"));

        AnomalyEvent event =
                anomalyEventMapper.selectOne(
                        Wrappers
                                .<AnomalyEvent>lambdaQuery()
                                .eq(
                                        AnomalyEvent::getId,
                                        order.getAnomalyEventId())
                                .last("FOR UPDATE"));

        Warning warning = warningMapper.selectOne(
                Wrappers.<Warning>lambdaQuery()
                        .eq(
                                Warning::getAnomalyEventId,
                                order.getAnomalyEventId())
                        .last("FOR UPDATE"));

        if (feedback == null
                || event == null
                || warning == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单关联的反馈、异常事件或预警不完整");
        }

        if (!"PROCESSING".equals(event.getStatus())
                || !"ACTIVE".equals(warning.getStatus())
                || !"PROCESSING".equals(feedback.getStatus())) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "关联业务状态不允许复核工单");
        }

        String decision = request.decision()
                .trim()
                .toUpperCase(Locale.ROOT);

        String opinion = request.opinion().trim();

        String publicReply =
                request.publicReply() == null
                        || request.publicReply().isBlank()
                        ? null
                        : request.publicReply().trim();

        if ("CLOSE".equals(decision)
                && publicReply == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "关闭工单时必须填写公众办理说明");
        }

        LocalDateTime now = LocalDateTime.now();

        saveReviewHistory(
                order,
                admin,
                decision,
                opinion,
                publicReply,
                now);

        if ("RETURN".equals(decision)) {
            returnWorkOrder(
                    order,
                    admin,
                    opinion,
                    now);
        } else {
            closeWorkOrder(
                    order,
                    event,
                    warning,
                    feedback,
                    admin,
                    opinion,
                    publicReply,
                    now);
        }

        return toResponse(
                baseMapper.selectById(order.getId()));
    }

    private void saveReviewHistory(
            WorkOrder order,
            User admin,
            String decision,
            String opinion,
            String publicReply,
            LocalDateTime now) {

        if (order.getHandledAt() == null
                || order.getMeasures() == null
                || order.getResult() == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单缺少处置结果");
        }

        WorkOrderReview review =
                new WorkOrderReview();

        review.setWorkOrderId(order.getId());
        review.setReviewerId(admin.getId());
        review.setDecision(decision);
        review.setHandledAt(order.getHandledAt());
        review.setMeasures(order.getMeasures());
        review.setResult(order.getResult());
        review.setOpinion(opinion);
        review.setPublicReply(publicReply);
        review.setReviewedAt(now);

        if (workOrderReviewMapper.insert(review) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单复核历史保存失败");
        }
    }

    private void returnWorkOrder(
            WorkOrder order,
            User admin,
            String opinion,
            LocalDateTime now) {

        order.setStatus("PENDING");
        order.setReviewedBy(admin.getId());
        order.setReviewedAt(now);
        order.setReviewOpinion(opinion);
        order.setUpdatedAt(now);

        if (baseMapper.updateById(order) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单退回失败");
        }

        saveWorkOrderReviewLog(
                order,
                admin,
                "RETURN_RESULT",
                "PENDING_REVIEW",
                "PENDING",
                opinion);
    }

    private void closeWorkOrder(
            WorkOrder order,
            AnomalyEvent event,
            Warning warning,
            Feedback feedback,
            User admin,
            String opinion,
            String publicReply,
            LocalDateTime now) {

        order.setStatus("CLOSED");
        order.setReviewedBy(admin.getId());
        order.setReviewedAt(now);
        order.setReviewOpinion(opinion);
        order.setUpdatedAt(now);

        event.setStatus("CLOSED");
        event.setUpdatedAt(now);

        warning.setStatus("CLOSED");
        warning.setClosedAt(now);
        warning.setUpdatedAt(now);

        if (baseMapper.updateById(order) != 1
                || anomalyEventMapper.updateById(event) != 1
                || warningMapper.updateById(warning) != 1) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单、异常事件或预警关闭失败");
        }

        Long unfinishedCount =
                baseMapper.selectCount(
                        Wrappers
                                .<WorkOrder>lambdaQuery()
                                .eq(
                                        WorkOrder::getFeedbackId,
                                        feedback.getId())
                                .ne(
                                        WorkOrder::getId,
                                        order.getId())
                                .ne(
                                        WorkOrder::getStatus,
                                        "CLOSED"));

        if (unfinishedCount == null
                || unfinishedCount == 0) {

            feedback.setStatus("COMPLETED");
            feedback.setPublicReply(publicReply);

            if (feedbackMapper.updateById(feedback) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "公众反馈完成状态更新失败");
            }

            saveFeedbackCloseLog(
                    feedback,
                    admin,
                    publicReply);
        }

        saveWorkOrderReviewLog(
                order,
                admin,
                "CLOSE",
                "PENDING_REVIEW",
                "CLOSED",
                opinion);
    }

    private void saveWorkOrderReviewLog(
            WorkOrder order,
            User admin,
            String action,
            String fromStatus,
            String toStatus,
            String opinion) {

        OperationLog log = new OperationLog();
        log.setBusinessType("WORK_ORDER");
        log.setBusinessId(order.getId());
        log.setAction(action);
        log.setOperatorId(admin.getId());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setFromAssigneeId(order.getAssigneeId());
        log.setToAssigneeId(order.getAssigneeId());
        log.setRemark(opinion);

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单复核操作记录保存失败");
        }
    }

    private void saveFeedbackCloseLog(
            Feedback feedback,
            User admin,
            String publicReply) {

        OperationLog log = new OperationLog();
        log.setBusinessType("FEEDBACK");
        log.setBusinessId(feedback.getId());
        log.setAction("CLOSE_DISPOSAL");
        log.setOperatorId(admin.getId());
        log.setFromStatus("PROCESSING");
        log.setToStatus("COMPLETED");
        log.setRemark(publicReply);

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "反馈完成操作记录保存失败");
        }
    }

    private WorkOrderResponse toResponse(
            WorkOrder order) {

        Feedback feedback =
                feedbackMapper.selectById(
                        order.getFeedbackId());

        AnomalyEvent event =
                anomalyEventMapper.selectById(
                        order.getAnomalyEventId());

        if (feedback == null || event == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单关联数据不完整");
        }

        String assigneeName = null;

        if (order.getAssigneeId() != null) {
            User assignee =
                    userMapper.selectById(
                            order.getAssigneeId());

            if (assignee != null) {
                assigneeName =
                        assignee.getDisplayName();
            }
        }

        return new WorkOrderResponse(
                order.getId(),
                order.getAnomalyEventId(),
                order.getFeedbackId(),
                order.getGridId(),
                order.getAssigneeId(),
                assigneeName,
                order.getAssignedBy(),
                order.getRequirement(),
                order.getPriority(),
                order.getStatus(),
                order.getAssignedAt(),
                order.getHandledAt(),
                order.getMeasures(),
                order.getResult(),
                order.getSubmittedAt(),
                order.getReviewedBy(),
                order.getReviewedAt(),
                order.getReviewOpinion(),
                feedback.getAddress(),
                feedback.getDescription(),
                event.getTriggerReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private boolean isAccessible(
            User admin,
            WorkOrder order) {

        Grid grid =
                gridMapper.selectById(
                        order.getGridId());

        if (grid == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "工单关联的网格不存在");
        }

        return userRegionMapper.countAccessibleRegion(
                admin.getId(),
                grid.getRegionId()) > 0;
    }

    private void requireAccess(
            User admin,
            WorkOrder order) {

        if (!isAccessible(admin, order)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "工单不在当前管理员授权范围内");
        }
    }

    private User currentUser(String role) {
        String phone = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, phone)
                        .eq(User::getRole, role)
                        .eq(User::getEnabled, 1));

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "当前账号不可用");
        }

        return user;
    }

    private void requirePositiveId(
            Long workOrderId) {

        if (workOrderId == null
                || workOrderId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "工单ID必须是正整数");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim()
                .toUpperCase(Locale.ROOT);
    }
}
