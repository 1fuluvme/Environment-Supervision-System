package com.neps.controller;

import com.neps.dto.SubmitWorkOrderResultRequest;
import com.neps.dto.WorkOrderResponse;
import com.neps.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grid/work-orders")
@Tag(name = "网格员处置工单")
public class WorkerWorkOrderController {

    private final WorkOrderService workOrderService;

    public WorkerWorkOrderController(
            WorkOrderService workOrderService) {

        this.workOrderService = workOrderService;
    }

    @Operation(summary = "查询本人处置工单")
    @GetMapping
    public List<WorkOrderResponse> listMine() {
        return workOrderService.listMine();
    }

    @Operation(summary = "查询本人处置工单详情")
    @GetMapping("/{workOrderId}")
    public WorkOrderResponse getMine(
            @PathVariable("workOrderId")
            Long workOrderId) {

        return workOrderService.getMine(
                workOrderId);
    }

    @Operation(summary = "提交处置措施和结果")
    @PostMapping("/{workOrderId}/result")
    public WorkOrderResponse submitResult(
            @PathVariable("workOrderId")
            Long workOrderId,
            @Valid @RequestBody
            SubmitWorkOrderResultRequest request) {

        return workOrderService.submitResult(
                workOrderId,
                request);
    }
}
