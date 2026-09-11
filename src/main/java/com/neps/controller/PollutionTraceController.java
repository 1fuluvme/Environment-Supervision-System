package com.neps.controller;

import com.neps.dto.PollutionTraceGenerateRequest;
import com.neps.dto.PollutionTraceResponse;
import com.neps.service.PollutionTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pollution-traces")
@PreAuthorize("hasAnyRole('ADMIN','DECISION')")
@Tag(name = "疑似污染溯源")
public class PollutionTraceController {

    private final PollutionTraceService traceService;

    public PollutionTraceController(
            PollutionTraceService traceService) {

        this.traceService = traceService;
    }

    @Operation(summary = "生成疑似污染溯源结果")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PollutionTraceResponse generate(
            @Valid
            @RequestBody
            PollutionTraceGenerateRequest request) {

        return traceService.generate(request);
    }

    @Operation(summary = "查询授权区域内的疑似溯源结果")
    @GetMapping
    public List<PollutionTraceResponse> list(
            @RequestParam(
                    value = "anomalyEventId",
                    required = false)
            Long anomalyEventId) {

        return traceService.list(anomalyEventId);
    }

    @Operation(summary = "查询疑似溯源详情")
    @GetMapping("/{traceId}")
    public PollutionTraceResponse get(
            @PathVariable("traceId")
            Long traceId) {

        return traceService.get(traceId);
    }
}
