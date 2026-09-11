package com.neps.controller;

import com.neps.dto.AiQuestionRequest;
import com.neps.dto.AiQuestionResponse;
import com.neps.service.AiQuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/questions")
@PreAuthorize("hasAnyRole('ADMIN','DECISION')")
@Tag(name = "受限只读AI问答")
public class AiQuestionController {

    private final AiQuestionService aiQuestionService;

    public AiQuestionController(
            AiQuestionService aiQuestionService) {

        this.aiQuestionService =
                aiQuestionService;
    }

    @Operation(summary = "根据授权统计数据进行只读问答")
    @PostMapping
    public AiQuestionResponse answer(
            @Valid
            @RequestBody
            AiQuestionRequest request) {

        return aiQuestionService.answer(request);
    }
}
