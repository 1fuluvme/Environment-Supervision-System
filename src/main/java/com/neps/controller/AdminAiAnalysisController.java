package com.neps.controller;

import com.neps.dto.AiAnalysisResponse;
import com.neps.service.AiAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-analyses")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理员AI初判")
public class AdminAiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AdminAiAnalysisController(
            AiAnalysisService aiAnalysisService) {

        this.aiAnalysisService = aiAnalysisService;
    }

    @Operation(summary = "查询AI初判记录")
    @GetMapping
    public List<AiAnalysisResponse> list(
            @RequestParam(
                    value = "targetType",
                    required = false)
            String targetType,

            @RequestParam(
                    value = "status",
                    required = false)
            String status) {

        return aiAnalysisService.listForAdmin(
                targetType,
                status);
    }

    @Operation(summary = "查询AI初判详情")
    @GetMapping("/{analysisId}")
    public AiAnalysisResponse get(
            @PathVariable("analysisId")
            Long analysisId) {

        return aiAnalysisService.getForAdmin(
                analysisId);
    }

    @Operation(summary = "重新执行失败的AI初判")
    @PostMapping("/{analysisId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(
            @PathVariable("analysisId")
            Long analysisId) {

        aiAnalysisService.retry(analysisId);
    }
}
