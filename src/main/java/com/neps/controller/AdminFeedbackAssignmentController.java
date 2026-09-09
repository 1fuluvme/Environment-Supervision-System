package com.neps.controller;

import com.neps.dto.AssignInspectionTaskRequest;
import com.neps.dto.InspectionTaskResponse;
import com.neps.service.InspectionTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员反馈指派")
@RestController
@RequestMapping("/api/admin/feedbacks")
public class AdminFeedbackAssignmentController {

    private final InspectionTaskService inspectionTaskService;

    public AdminFeedbackAssignmentController(
            InspectionTaskService inspectionTaskService) {

        this.inspectionTaskService = inspectionTaskService;
    }

    @Operation(summary = "指派或改派反馈核查任务")
    @PostMapping("/{feedbackId}/assign")
    public InspectionTaskResponse assign(
            @PathVariable("feedbackId") Long feedbackId,
            @Valid @RequestBody
            AssignInspectionTaskRequest request) {

        return inspectionTaskService.assign(
                feedbackId,
                request);
    }
}