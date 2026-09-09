package com.neps.controller;

import com.neps.dto.AdminFeedbackResponse;
import com.neps.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "管理员反馈查询")
@RestController
@RequestMapping("/api/admin/feedbacks")
public class AdminFeedbackQueryController {

    private final FeedbackService feedbackService;

    public AdminFeedbackQueryController(
            FeedbackService feedbackService) {

        this.feedbackService = feedbackService;
    }

    @Operation(summary = "查询授权范围内的反馈")
    @GetMapping
    public List<AdminFeedbackResponse> list(
            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            Long regionId,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime to) {

        return feedbackService.listForAdmin(
                status,
                regionId,
                from,
                to);
    }

    @Operation(summary = "查询授权范围内的反馈详情")
    @GetMapping("/{id}")
    public AdminFeedbackResponse detail(
            @PathVariable("id") Long id) {

        return feedbackService.getForAdmin(id);
    }
}
