package com.neps.controller;

import com.neps.dto.AssignWorkOrderRequest;
import com.neps.dto.WorkOrderResponse;
import com.neps.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/work-orders")
@Tag(name = "管理员处置工单")
public class AdminWorkOrderController {

    private final WorkOrderService workOrderService;

    public AdminWorkOrderController(
            WorkOrderService workOrderService) {

        this.workOrderService = workOrderService;
    }

    @Operation(summary = "查询授权区域内的处置工单")
    @GetMapping
    public List<WorkOrderResponse> list(
            @RequestParam(
                    value = "status",
                    required = false)
            String status,

            @RequestParam(
                    value = "priority",
                    required = false)
            String priority) {

        return workOrderService.listForAdmin(
                status,
                priority);
    }

    @Operation(summary = "查询处置工单详情")
    @GetMapping("/{workOrderId}")
    public WorkOrderResponse get(
            @PathVariable("workOrderId")
            Long workOrderId) {

        return workOrderService.getForAdmin(
                workOrderId);
    }

    @Operation(summary = "确认并指派处置工单")
    @PostMapping("/{workOrderId}/assign")
    public WorkOrderResponse assign(
            @PathVariable("workOrderId")
            Long workOrderId,
            @Valid @RequestBody
            AssignWorkOrderRequest request) {

        return workOrderService.assign(
                workOrderId,
                request);
    }
}
