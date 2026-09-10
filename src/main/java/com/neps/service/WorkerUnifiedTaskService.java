package com.neps.service;

import com.neps.dto.InspectionTaskResponse;
import com.neps.dto.UnifiedTaskResponse;
import com.neps.dto.WorkOrderResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class WorkerUnifiedTaskService {

    private static final Set<String> TASK_TYPES =
            Set.of("INSPECTION", "DISPOSAL");

    private static final Set<String> STATUSES =
            Set.of(
                    "PENDING",
                    "PENDING_REVIEW",
                    "COMPLETED",
                    "CLOSED");

    private final InspectionTaskService
            inspectionTaskService;

    private final WorkOrderService
            workOrderService;

    public WorkerUnifiedTaskService(
            InspectionTaskService inspectionTaskService,
            WorkOrderService workOrderService) {

        this.inspectionTaskService =
                inspectionTaskService;

        this.workOrderService =
                workOrderService;
    }

    @PreAuthorize("hasRole('GRID')")
    public List<UnifiedTaskResponse> listMine(
            String taskType,
            String status) {

        String normalizedType =
                normalize(taskType);

        String normalizedStatus =
                normalize(status);

        if (normalizedType != null
                && !TASK_TYPES.contains(
                normalizedType)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "任务类型不正确");
        }

        if (normalizedStatus != null
                && !STATUSES.contains(
                normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "任务状态不正确");
        }

        List<UnifiedTaskResponse> result =
                new ArrayList<>();

        if (normalizedType == null
                || "INSPECTION".equals(
                normalizedType)) {

            inspectionTaskService.listMine()
                    .stream()
                    .map(this::fromInspection)
                    .filter(task ->
                            normalizedStatus == null
                                    || normalizedStatus.equals(
                                    task.status()))
                    .forEach(result::add);
        }

        if (normalizedType == null
                || "DISPOSAL".equals(
                normalizedType)) {

            workOrderService.listMine()
                    .stream()
                    .map(this::fromWorkOrder)
                    .filter(task ->
                            normalizedStatus == null
                                    || normalizedStatus.equals(
                                    task.status()))
                    .forEach(result::add);
        }

        result.sort(
                Comparator
                        .comparing(
                                UnifiedTaskResponse::assignedAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()))
                        .thenComparing(
                                UnifiedTaskResponse::id,
                                Comparator.reverseOrder()));

        return result;
    }

    private UnifiedTaskResponse fromInspection(
            InspectionTaskResponse task) {

        return new UnifiedTaskResponse(
                "INSPECTION",
                task.taskId(),
                task.feedbackId(),
                null,
                task.gridId(),
                task.address(),
                task.observedAt(),
                task.description(),
                task.requirement(),
                task.priority(),
                task.taskStatus(),
                task.assignedAt());
    }

    private UnifiedTaskResponse fromWorkOrder(
            WorkOrderResponse order) {

        return new UnifiedTaskResponse(
                "DISPOSAL",
                order.id(),
                order.feedbackId(),
                order.anomalyEventId(),
                order.gridId(),
                order.address(),
                null,
                order.description(),
                order.requirement(),
                order.priority(),
                order.status(),
                order.assignedAt());
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? null
                : value.trim()
                .toUpperCase(Locale.ROOT);
    }
}
