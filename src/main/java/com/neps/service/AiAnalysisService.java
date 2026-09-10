package com.neps.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.neps.dto.AiAnalysisResponse;
import com.neps.entity.AiAnalysis;

import java.util.List;

public interface AiAnalysisService
        extends IService<AiAnalysis> {

    List<AiAnalysisResponse> listForAdmin(
            String targetType,
            String status);

    AiAnalysisResponse getForAdmin(Long analysisId);

    void retry(Long analysisId);
}
