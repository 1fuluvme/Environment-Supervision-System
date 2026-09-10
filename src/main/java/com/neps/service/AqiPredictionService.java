package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.AqiPredictionGenerateRequest;
import com.neps.dto.AqiPredictionResponse;
import com.neps.entity.AqiPrediction;

import java.util.List;

public interface AqiPredictionService
        extends IService<AqiPrediction> {

    AqiPredictionResponse generate(
            AqiPredictionGenerateRequest request);

    List<AqiPredictionResponse> list(
            Long regionId);

    AqiPredictionResponse get(
            Long predictionId);
}
