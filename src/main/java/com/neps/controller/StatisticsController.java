package com.neps.controller;

import com.neps.dto.StatisticsResponse;
import com.neps.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statistics")
@PreAuthorize("hasAnyRole('ADMIN','DECISION')")
@Tag(name = "共用空气质量统计")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(
            StatisticsService statisticsService) {

        this.statisticsService = statisticsService;
    }

    @Operation(summary = "查询授权区域空气质量统计")
    @GetMapping
    public StatisticsResponse query(
            @RequestParam("regionId")
            Long regionId,

            @RequestParam("startDate")
            @DateTimeFormat(iso =
                    DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam("endDate")
            @DateTimeFormat(iso =
                    DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam("reportType")
            String reportType) {

        return statisticsService.query(
                regionId,
                startDate,
                endDate,
                reportType);
    }
}
