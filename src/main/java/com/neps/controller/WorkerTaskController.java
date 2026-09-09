package com.neps.controller;

import com.neps.dto.InspectionTaskResponse;
import com.neps.service.InspectionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "网格员核查任务")
@RestController
@RequestMapping("/api/tasks")
public class WorkerTaskController {

    private final InspectionTaskService inspectionTaskService;

    public WorkerTaskController(
            InspectionTaskService inspectionTaskService) {

        this.inspectionTaskService = inspectionTaskService;
    }

    @Operation(summary = "查询本人核查任务")
    @GetMapping("/mine")
    public List<InspectionTaskResponse> listMine() {
        return inspectionTaskService.listMine();
    }
}
