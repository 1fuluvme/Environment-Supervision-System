package com.neps.controller;

import com.neps.dto.WarningResponse;
import com.neps.service.WarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/warnings")
@Tag(name = "管理员内部预警")
public class AdminWarningController {

    private final WarningService warningService;

    public AdminWarningController(
            WarningService warningService) {

        this.warningService = warningService;
    }

    @Operation(summary = "查询授权区域内的内部预警")
    @GetMapping
    public List<WarningResponse> list(
            @RequestParam(
                    value = "status",
                    required = false)
            String status,

            @RequestParam(
                    value = "level",
                    required = false)
            String level) {

        return warningService.listForAdmin(
                status,
                level);
    }

    @Operation(summary = "查询内部预警详情")
    @GetMapping("/{warningId}")
    public WarningResponse get(
            @PathVariable("warningId")
            Long warningId) {

        return warningService.getForAdmin(
                warningId);
    }
}