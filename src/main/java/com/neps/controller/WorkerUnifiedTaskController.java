package com.neps.controller;

import com.neps.dto.UnifiedTaskResponse;
import com.neps.service.WorkerUnifiedTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grid/tasks")
@Tag(name = "网格员统一任务")
public class WorkerUnifiedTaskController {

    private final WorkerUnifiedTaskService
            workerUnifiedTaskService;

    public WorkerUnifiedTaskController(
            WorkerUnifiedTaskService
                    workerUnifiedTaskService) {

        this.workerUnifiedTaskService =
                workerUnifiedTaskService;
    }

    @Operation(summary = "查询本人全部核查和处置任务")
    @GetMapping
    public List<UnifiedTaskResponse> listMine(
            @RequestParam(
                    value = "taskType",
                    required = false)
            String taskType,

            @RequestParam(
                    value = "status",
                    required = false)
            String status) {

        return workerUnifiedTaskService.listMine(
                taskType,
                status);
    }
}
