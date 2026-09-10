package com.neps.controller;

import com.neps.dto.AqiPredictionGenerateRequest;
import com.neps.dto.AqiPredictionResponse;
import com.neps.service.AqiPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/aqi-predictions")
@PreAuthorize("hasAnyRole('ADMIN','DECISION')")
@Tag(name = "AQI趋势预测")
public class AqiPredictionController {

    private final AqiPredictionService predictionService;

    public AqiPredictionController(
            AqiPredictionService predictionService) {

        this.predictionService = predictionService;
    }

    @Operation(summary = "生成MA7次日AQI预测")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AqiPredictionResponse generate(
            @Valid
            @RequestBody
            AqiPredictionGenerateRequest request) {

        return predictionService.generate(request);
    }

    @Operation(summary = "查询授权区域内的AQI预测")
    @GetMapping
    public List<AqiPredictionResponse> list(
            @RequestParam(
                    value = "regionId",
                    required = false)
            Long regionId) {

        return predictionService.list(regionId);
    }

    @Operation(summary = "查询AQI预测详情")
    @GetMapping("/{predictionId}")
    public AqiPredictionResponse get(
            @PathVariable("predictionId")
            Long predictionId) {

        return predictionService.get(predictionId);
    }
}
