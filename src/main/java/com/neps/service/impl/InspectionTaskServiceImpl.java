package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.AssignInspectionTaskRequest;
import com.neps.dto.InspectionTaskResponse;
import com.neps.entity.Feedback;
import com.neps.entity.Grid;
import com.neps.entity.InspectionTask;
import com.neps.entity.OperationLog;
import com.neps.entity.User;
import com.neps.entity.UserGrid;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.GridMapper;
import com.neps.mapper.InspectionTaskMapper;
import com.neps.mapper.OperationLogMapper;
import com.neps.mapper.UserGridMapper;
import com.neps.mapper.UserMapper;
import com.neps.mapper.UserRegionMapper;
import com.neps.service.InspectionTaskService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class InspectionTaskServiceImpl
        extends ServiceImpl<InspectionTaskMapper, InspectionTask>
        implements InspectionTaskService {

    private final UserMapper userMapper;
    private final FeedbackMapper feedbackMapper;
    private final GridMapper gridMapper;
    private final UserGridMapper userGridMapper;
    private final UserRegionMapper userRegionMapper;
    private final OperationLogMapper operationLogMapper;

    public InspectionTaskServiceImpl(
            UserMapper userMapper,
            FeedbackMapper feedbackMapper,
            GridMapper gridMapper,
            UserGridMapper userGridMapper,
            UserRegionMapper userRegionMapper,
            OperationLogMapper operationLogMapper) {

        this.userMapper = userMapper;
        this.feedbackMapper = feedbackMapper;
        this.gridMapper = gridMapper;
        this.userGridMapper = userGridMapper;
        this.userRegionMapper = userRegionMapper;
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public InspectionTaskResponse assign(
            Long feedbackId,
            AssignInspectionTaskRequest request) {

        if (feedbackId == null || feedbackId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈ID必须是正整数");
        }

        User admin = currentUser("ADMIN");

        Feedback feedback = feedbackMapper.selectOne(
                Wrappers.<Feedback>lambdaQuery()
                        .eq(Feedback::getId, feedbackId)
                        .last("FOR UPDATE"));

        if (feedback == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "反馈不存在");
        }

        Grid grid = gridMapper.selectOne(
                Wrappers.<Grid>lambdaQuery()
                        .eq(Grid::getId, feedback.getGridId())
                        .last("FOR UPDATE"));

        if (grid == null
                || !Byte.valueOf((byte) 1)
                .equals(grid.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "反馈所属网格不存在或已停用");
        }

        if (userRegionMapper.countAccessibleRegion(
                admin.getId(), grid.getRegionId()) == 0) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "反馈不在当前管理员的授权区域内");
        }

        User worker = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getId, request.assigneeId())
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

        Long responsibleCount = userGridMapper.selectCount(
                Wrappers.<UserGrid>lambdaQuery()
                        .eq(UserGrid::getUserId, worker.getId())
                        .eq(UserGrid::getGridId, grid.getId()));

        if (responsibleCount == null || responsibleCount == 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "该网格员不负责反馈所属网格");
        }

        InspectionTask task = lambdaQuery()
                .eq(InspectionTask::getFeedbackId, feedbackId)
                .last("FOR UPDATE")
                .one();

        String requirement = request.requirement().trim();
        String priority = request.priority();
        LocalDateTime now = LocalDateTime.now();

        if (task == null) {
            if (!"PENDING_ASSIGN".equals(feedback.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前反馈状态不允许创建核查任务");
            }

            task = new InspectionTask();
            task.setFeedbackId(feedback.getId());
            task.setAssigneeId(worker.getId());
            task.setAssignedBy(admin.getId());
            task.setRequirement(requirement);
            task.setPriority(priority);
            task.setStatus("PENDING");
            task.setAssignedAt(now);

            if (!save(task)) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "核查任务创建失败");
            }

            feedback.setStatus("CHECKING");

            if (feedbackMapper.updateById(feedback) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "反馈状态更新失败");
            }

            saveOperationLog(
                    feedback,
                    admin,
                    "ASSIGN",
                    "PENDING_ASSIGN",
                    "CHECKING",
                    null,
                    worker.getId(),
                    requirement);
        } else {
            if (!"CHECKING".equals(feedback.getStatus())
                    || !"PENDING".equals(task.getStatus())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "当前任务状态不允许改派");
            }

            boolean sameAssignment =
                    Objects.equals(
                            task.getAssigneeId(),
                            worker.getId())
                            && Objects.equals(
                            task.getRequirement(),
                            requirement)
                            && Objects.equals(
                            task.getPriority(),
                            priority);

            if (sameAssignment) {
                return toResponse(task, feedback);
            }

            Long previousAssignee = task.getAssigneeId();

            task.setAssigneeId(worker.getId());
            task.setAssignedBy(admin.getId());
            task.setRequirement(requirement);
            task.setPriority(priority);
            task.setAssignedAt(now);

            if (baseMapper.updateById(task) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "核查任务更新失败");
            }

            String action = Objects.equals(
                    previousAssignee,
                    worker.getId())
                    ? "UPDATE_ASSIGNMENT"
                    : "REASSIGN";

            saveOperationLog(
                    feedback,
                    admin,
                    action,
                    "CHECKING",
                    "CHECKING",
                    previousAssignee,
                    worker.getId(),
                    requirement);
        }

        return toResponse(task, feedback);
    }

    @Override
    @PreAuthorize("hasRole('GRID')")
    public List<InspectionTaskResponse> listMine() {
        User worker = currentUser("GRID");

        return lambdaQuery()
                .eq(InspectionTask::getAssigneeId, worker.getId())
                .orderByDesc(InspectionTask::getAssignedAt)
                .orderByDesc(InspectionTask::getId)
                .list()
                .stream()
                .map(task -> {
                    Feedback feedback =
                            feedbackMapper.selectById(
                                    task.getFeedbackId());

                    if (feedback == null) {
                        throw new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "核查任务关联的反馈不存在");
                    }

                    return toResponse(task, feedback);
                })
                .toList();
    }

    private void saveOperationLog(
            Feedback feedback,
            User admin,
            String action,
            String fromStatus,
            String toStatus,
            Long fromAssigneeId,
            Long toAssigneeId,
            String remark) {

        OperationLog log = new OperationLog();
        log.setBusinessType("FEEDBACK");
        log.setBusinessId(feedback.getId());
        log.setAction(action);
        log.setOperatorId(admin.getId());
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setFromAssigneeId(fromAssigneeId);
        log.setToAssigneeId(toAssigneeId);
        log.setRemark(remark);

        if (operationLogMapper.insert(log) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "操作记录保存失败");
        }
    }

    private User currentUser(String role) {
        String phone = SecurityContextHolder.getContext()
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

    private InspectionTaskResponse toResponse(
            InspectionTask task,
            Feedback feedback) {

        return new InspectionTaskResponse(
                task.getId(),
                feedback.getId(),
                feedback.getGridId(),
                feedback.getAddress(),
                feedback.getObservedAt(),
                feedback.getDescription(),
                feedback.getStatus(),
                task.getAssigneeId(),
                task.getRequirement(),
                task.getPriority(),
                task.getStatus(),
                task.getAssignedAt());
    }
}
