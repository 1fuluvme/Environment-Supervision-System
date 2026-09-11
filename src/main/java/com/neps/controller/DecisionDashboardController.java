package com.neps.controller;

import com.neps.dto.DecisionDashboardResponse;
import com.neps.service.DecisionDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/decision/dashboard")
@PreAuthorize("hasRole('DECISION')")
@Tag(name = "决策大屏聚合")
public class DecisionDashboardController {

    private final DecisionDashboardService dashboardService;

    public DecisionDashboardController(
            DecisionDashboardService dashboardService) {

        this.dashboardService =
                dashboardService;
    }

    @GetMapping
    @Operation(summary = "查询授权区域决策汇总")
    public DecisionDashboardResponse query(
            @RequestParam("regionId")
            Long regionId,

            @RequestParam("startDate")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate) {

        return dashboardService.query(
                regionId,
                startDate,
                endDate);
    }
}
