package com.neps.controller;

import com.neps.dto.AnomalyEventResponse;
import com.neps.dto.ReviewAnomalyRequest;
import com.neps.service.AnomalyEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/anomalies")
@Tag(name = "管理员异常事件")
public class AdminAnomalyEventController {

    private final AnomalyEventService anomalyEventService;

    public AdminAnomalyEventController(
            AnomalyEventService anomalyEventService) {

        this.anomalyEventService =
                anomalyEventService;
    }

    @Operation(summary = "查询授权区域内的异常事件")
    @GetMapping
    public List<AnomalyEventResponse> list(
            @RequestParam(
                    value = "status",
                    required = false)
            String status,

            @RequestParam(
                    value = "priority",
                    required = false)
            String priority) {

        return anomalyEventService.listForAdmin(
                status,
                priority);
    }

    @Operation(summary = "查询异常事件详情")
    @GetMapping("/{eventId}")
    public AnomalyEventResponse get(
            @PathVariable("eventId")
            Long eventId) {

        return anomalyEventService.getForAdmin(
                eventId);
    }

    @Operation(summary = "确认或排除异常事件")
    @PostMapping("/{eventId}/review")
    public AnomalyEventResponse review(
            @PathVariable("eventId")
            Long eventId,
            @Valid @RequestBody
            ReviewAnomalyRequest request) {

        return anomalyEventService.review(
                eventId,
                request);
    }
}
