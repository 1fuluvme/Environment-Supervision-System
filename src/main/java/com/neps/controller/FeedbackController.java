package com.neps.controller;

import com.neps.dto.CreateFeedbackRequest;
import com.neps.dto.FeedbackResponse;
import com.neps.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "公众反馈")
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @Operation(summary = "提交公众反馈")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedbackResponse create(
            @Valid @RequestBody CreateFeedbackRequest request) {

        return feedbackService.createFeedback(request);
    }

    @Operation(summary = "查询本人反馈")
    @GetMapping("/mine")
    public List<FeedbackResponse> listMine() {
        return feedbackService.listMine();
    }

    @Operation(summary = "查询本人反馈详情")
    @GetMapping("/{id}")
    public FeedbackResponse getMine(
            @PathVariable("id") Long id) {

        return feedbackService.getMine(id);
    }
}
