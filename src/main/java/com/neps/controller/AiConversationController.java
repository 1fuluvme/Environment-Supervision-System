package com.neps.controller;

import com.neps.service.AiQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import com.neps.dto.AiConversationMessageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/ai/conversations")
@PreAuthorize("hasAnyRole('ADMIN','DECISION')")
@Validated
@Tag(name = "AI Agent会话管理")
public class AiConversationController {

    private final AiQuestionService aiQuestionService;

    public AiConversationController(
            AiQuestionService aiQuestionService) {

        this.aiQuestionService =
                aiQuestionService;
    }

    @Operation(summary = "查询指定范围的Agent会话上下文")
    @GetMapping("/{conversationId}")
    public List<AiConversationMessageResponse> get(
            @PathVariable
            @Pattern(
                    regexp = "^[A-Za-z0-9_-]{1,64}$",
                    message = "会话ID格式不正确")
            String conversationId,

            @RequestParam
            @Positive(message = "区域ID必须是正整数")
            Long regionId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam
            @NotBlank(message = "统计口径不能为空")
            String reportType) {

        return aiQuestionService.getConversation(
                conversationId,
                regionId,
                startDate,
                endDate,
                reportType);
    }

    @Operation(summary = "清空指定查询范围的Agent会话记忆")
    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(
            @PathVariable
            @Pattern(
                    regexp = "^[A-Za-z0-9_-]{1,64}$",
                    message = "会话ID格式不正确")
            String conversationId,

            @RequestParam
            @Positive(message = "区域ID必须是正整数")
            Long regionId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam
            @NotBlank(message = "统计口径不能为空")
            String reportType) {

        aiQuestionService.clearConversation(
                conversationId,
                regionId,
                startDate,
                endDate,
                reportType);
    }
}
