package com.neps.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.neps.dto.AiAnalysisResponse;
import com.neps.entity.AiAnalysis;
import com.neps.mapper.AiAnalysisMapper;
import com.neps.service.AiAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AiAnalysisServiceImpl
        extends ServiceImpl<AiAnalysisMapper, AiAnalysis>
        implements AiAnalysisService {

    private static final Set<String> TARGET_TYPES =
            Set.of("FEEDBACK", "MEASUREMENT");

    private static final Set<String> STATUSES =
            Set.of(
                    "PENDING",
                    "RUNNING",
                    "SUCCEEDED",
                    "FAILED");

    @Override
    public List<AiAnalysisResponse> listForAdmin(
            String targetType,
            String status) {

        String normalizedTargetType =
                normalize(targetType);
        String normalizedStatus =
                normalize(status);

        if (normalizedTargetType != null
                && !TARGET_TYPES.contains(
                normalizedTargetType)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "分析对象类型不正确");
        }

        if (normalizedStatus != null
                && !STATUSES.contains(normalizedStatus)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI分析状态不正确");
        }

        return lambdaQuery()
                .eq(
                        normalizedTargetType != null,
                        AiAnalysis::getTargetType,
                        normalizedTargetType)
                .eq(
                        normalizedStatus != null,
                        AiAnalysis::getStatus,
                        normalizedStatus)
                .orderByDesc(AiAnalysis::getId)
                .last("LIMIT 200")
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public AiAnalysisResponse getForAdmin(
            Long analysisId) {

        validateId(analysisId);

        AiAnalysis analysis = getById(analysisId);

        if (analysis == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "AI分析记录不存在");
        }

        return toResponse(analysis);
    }

    @Override
    public void retry(Long analysisId) {
        validateId(analysisId);

        AiAnalysis analysis = getById(analysisId);

        if (analysis == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "AI分析记录不存在");
        }

        if (!"FAILED".equals(analysis.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有失败的AI分析可以重试");
        }

        LocalDateTime now = LocalDateTime.now();

        int changed = baseMapper.update(
                null,
                Wrappers.<AiAnalysis>lambdaUpdate()
                        .eq(AiAnalysis::getId, analysisId)
                        .eq(AiAnalysis::getStatus, "FAILED")
                        .set(AiAnalysis::getStatus, "PENDING")
                        .set(AiAnalysis::getProvider, null)
                        .set(AiAnalysis::getModelName, null)
                        .set(AiAnalysis::getInputSnapshot, null)
                        .set(AiAnalysis::getResultText, null)
                        .set(AiAnalysis::getFailureReason, null)
                        .set(AiAnalysis::getIsDemo, (byte) 0)
                        .set(AiAnalysis::getStartedAt, null)
                        .set(AiAnalysis::getCompletedAt, null)
                        .set(AiAnalysis::getUpdatedAt, now));

        if (changed != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI分析状态已经发生变化，请刷新后重试");
        }
    }

    private void validateId(Long analysisId) {
        if (analysisId == null || analysisId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "AI分析记录ID必须大于0");
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toUpperCase();
    }

    private AiAnalysisResponse toResponse(
            AiAnalysis analysis) {

        return new AiAnalysisResponse(
                analysis.getId(),
                analysis.getTargetType(),
                analysis.getTargetId(),
                analysis.getStatus(),
                analysis.getProvider(),
                analysis.getModelName(),
                analysis.getInputSnapshot(),
                analysis.getResultText(),
                analysis.getFailureReason(),
                Byte.valueOf((byte) 1).equals(
                        analysis.getIsDemo()),
                analysis.getAttemptCount(),
                analysis.getStartedAt(),
                analysis.getCompletedAt(),
                analysis.getCreatedAt(),
                analysis.getUpdatedAt());
    }
}
