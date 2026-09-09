package com.neps.controller;

import com.neps.dto.MeasurementResponse;
import com.neps.dto.SubmitMeasurementRequest;
import com.neps.service.MeasurementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "网格员检测")
public class WorkerMeasurementController {

    private final MeasurementService measurementService;

    public WorkerMeasurementController(
            MeasurementService measurementService) {

        this.measurementService = measurementService;
    }

    @Operation(summary = "为本人负责的核查任务提交检测")
    @PostMapping("/{taskId}/measurements")
    public ResponseEntity<MeasurementResponse> submit(
            @PathVariable("taskId") Long taskId,
            @Valid @RequestBody SubmitMeasurementRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(measurementService.submit(taskId, request));
    }
}
