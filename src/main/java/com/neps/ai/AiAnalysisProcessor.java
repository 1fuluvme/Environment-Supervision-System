package com.neps.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.neps.entity.AiAnalysis;
import com.neps.entity.Feedback;
import com.neps.entity.Measurement;
import com.neps.mapper.AiAnalysisMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.MeasurementMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class AiAnalysisProcessor {

    private final AiAnalysisMapper aiAnalysisMapper;
    private final FeedbackMapper feedbackMapper;
    private final MeasurementMapper measurementMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.mode:mock}")
    private String mode;

    public AiAnalysisProcessor(
            AiAnalysisMapper aiAnalysisMapper,
            FeedbackMapper feedbackMapper,
            MeasurementMapper measurementMapper,
            ObjectMapper objectMapper) {

        this.aiAnalysisMapper = aiAnalysisMapper;
        this.feedbackMapper = feedbackMapper;
        this.measurementMapper = measurementMapper;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void recoverInterruptedAnalysis() {
        aiAnalysisMapper.update(
                null,
                Wrappers.<AiAnalysis>lambdaUpdate()
                        .eq(AiAnalysis::getStatus, "RUNNING")
                        .set(AiAnalysis::getStatus, "PENDING")
                        .set(
                                AiAnalysis::getUpdatedAt,
                                LocalDateTime.now()));
    }

    @Scheduled(
            initialDelayString = "${app.ai.poll-delay-ms:1000}",
            fixedDelayString = "${app.ai.poll-delay-ms:1000}")
    public void processPendingBatch() {

        // 单机演示一次最多处理50条，防止历史待处理记录阻塞测试。
        for (int i = 0; i < 50; i++) {
            if (!processOne()) {
                return;
            }
        }
    }

    private boolean processOne() {
        AiAnalysis analysis = aiAnalysisMapper.selectOne(
                Wrappers.<AiAnalysis>lambdaQuery()
                        .eq(AiAnalysis::getStatus, "PENDING")
                        .orderByAsc(AiAnalysis::getId)
                        .last("LIMIT 1"));

        if (analysis == null) {
            return false;
        }

        LocalDateTime startedAt = LocalDateTime.now();

        int claimed = aiAnalysisMapper.update(
                null,
                Wrappers.<AiAnalysis>lambdaUpdate()
                        .eq(AiAnalysis::getId, analysis.getId())
                        .eq(AiAnalysis::getStatus, "PENDING")
                        .set(AiAnalysis::getStatus, "RUNNING")
                        .set(AiAnalysis::getStartedAt, startedAt)
                        .set(AiAnalysis::getUpdatedAt, startedAt)
                        .setSql("attempt_count = attempt_count + 1"));

        if (claimed == 0) {
            return true;
        }

        analysis.setStatus("RUNNING");
        analysis.setStartedAt(startedAt);
        analysis.setUpdatedAt(startedAt);
        analysis.setAttemptCount(
                analysis.getAttemptCount() == null
                        ? 1
                        : analysis.getAttemptCount() + 1);

        try {
            if (!"mock".equalsIgnoreCase(mode)) {
                throw new IllegalStateException(
                        "AI服务未配置，当前模式：" + mode);
            }

            MockResult result = analyzeWithMock(analysis);

            analysis.setProvider("MOCK");
            analysis.setModelName("LOCAL-DEMO");
            analysis.setInputSnapshot(result.inputSnapshot());
            analysis.setResultText(result.resultText());
            analysis.setIsDemo((byte) 1);
            analysis.setStatus("SUCCEEDED");
            analysis.setFailureReason(null);
        } catch (Exception exception) {
            analysis.setStatus("FAILED");
            analysis.setFailureReason(
                    truncate(exception.getMessage(), 500));
        }

        LocalDateTime completedAt = LocalDateTime.now();
        analysis.setCompletedAt(completedAt);
        analysis.setUpdatedAt(completedAt);
        aiAnalysisMapper.updateById(analysis);

        return true;
    }

    private MockResult analyzeWithMock(
            AiAnalysis analysis) throws Exception {

        ObjectNode input = objectMapper.createObjectNode();
        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode checkItems = output.putArray("checkItems");

        if ("FEEDBACK".equals(analysis.getTargetType())) {
            Feedback feedback =
                    feedbackMapper.selectById(analysis.getTargetId());

            if (feedback == null) {
                throw new IllegalStateException("公众反馈不存在");
            }

            input.put("targetType", "FEEDBACK");
            input.put("targetId", feedback.getId());
            input.put("address", feedback.getAddress());
            input.put(
                    "observedAt",
                    String.valueOf(feedback.getObservedAt()));
            input.put("description", feedback.getDescription());

            output.put(
                    "summary",
                    "演示初判：" + truncate(
                            feedback.getDescription(), 200));

            output.put(
                    "suspectedPhenomenon",
                    feedback.getDescription().contains("烟")
                            ? "疑似烟尘现象"
                            : feedback.getDescription().contains("异味")
                            ? "疑似异味现象"
                            : "暂未识别明确污染现象");

            checkItems.add("核对现场位置");
            checkItems.add("查看公众提交的图片");
            checkItems.add("确认污染现象是否持续");
        } else if ("MEASUREMENT".equals(
                analysis.getTargetType())) {

            Measurement measurement =
                    measurementMapper.selectById(
                            analysis.getTargetId());

            if (measurement == null) {
                throw new IllegalStateException("检测记录不存在");
            }

            input.put("targetType", "MEASUREMENT");
            input.put("targetId", measurement.getId());
            input.put("location", measurement.getLocation());
            input.put("reportType", measurement.getReportType());
            input.put("dataSource", measurement.getDataSource());
            input.put("qualityFlag", measurement.getQualityFlag());

            if (measurement.getAqi() == null) {
                input.putNull("aqi");
            } else {
                input.put("aqi", measurement.getAqi());
            }

            input.put(
                    "aqiCategory",
                    measurement.getAqiCategory());
            input.put(
                    "primaryPollutants",
                    measurement.getPrimaryPollutants());
            input.put("siteNote", measurement.getSiteNote());

            output.put(
                    "summary",
                    measurement.getAqi() == null
                            ? "演示初判：当前数据不能计算AQI"
                            : "演示初判：检测AQI为"
                            + measurement.getAqi());

            output.put(
                    "suspectedPhenomenon",
                    !"VALID".equals(measurement.getQualityFlag())
                            ? "检测数据质量需要人工核查"
                            : measurement.getAqi() != null
                            && measurement.getAqi() > 100
                            ? "疑似存在空气污染异常"
                            : "暂未发现AQI超限");

            checkItems.add("核对检测时间和位置");
            checkItems.add("核对污染物原始浓度");
            checkItems.add("结合现场情况进行人工复核");
        } else {
            throw new IllegalStateException(
                    "不支持的分析对象类型："
                            + analysis.getTargetType());
        }

        /*
         * 这里明确说明：Mock或未来的图片模型都不能从图片
         * 推断真实污染物浓度或真实AQI。
         */
        output.put(
                "safetyNotice",
                "不得根据图片生成实测浓度或真实AQI");

        return new MockResult(
                objectMapper.writeValueAsString(input),
                objectMapper.writeValueAsString(output));
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return "未提供文字说明";
        }

        return value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }

    private record MockResult(
            String inputSnapshot,
            String resultText) {
    }
}
