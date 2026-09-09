package com.neps.controller;

import com.neps.dto.MeasurementResponse;
import com.neps.dto.MeasurementReviewResponse;
import com.neps.dto.ReviewMeasurementRequest;
import com.neps.service.MeasurementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/measurements")
@Tag(name = "管理员检测复核")
public class AdminMeasurementController {

    private final MeasurementService measurementService;

    public AdminMeasurementController(
            MeasurementService measurementService) {

        this.measurementService =
                measurementService;
    }

    @Operation(summary = "查询授权区域内的检测记录")
    @GetMapping
    public List<MeasurementResponse> list(
            @RequestParam(
                    value = "reviewStatus",
                    required = false)
            String reviewStatus) {

        return measurementService.listForAdmin(
                reviewStatus);
    }

    @Operation(summary = "查询检测记录详情")
    @GetMapping("/{measurementId}")
    public MeasurementResponse get(
            @PathVariable("measurementId")
            Long measurementId) {

        return measurementService.getForAdmin(
                measurementId);
    }

    @Operation(summary = "退回检测或普通结案")
    @PostMapping("/{measurementId}/review")
    public MeasurementReviewResponse review(
            @PathVariable("measurementId")
            Long measurementId,
            @Valid @RequestBody
            ReviewMeasurementRequest request) {

        return measurementService.review(
                measurementId,
                request);
    }
}
