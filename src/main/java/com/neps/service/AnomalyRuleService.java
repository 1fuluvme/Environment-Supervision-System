package com.neps.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neps.entity.AnomalyEvent;
import com.neps.entity.Feedback;
import com.neps.entity.Measurement;
import com.neps.mapper.AnomalyEventMapper;
import com.neps.mapper.FeedbackMapper;
import com.neps.mapper.MeasurementMapper;
import com.neps.rule.AnomalyRuleEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnomalyRuleService {

    private final MeasurementMapper measurementMapper;
    private final FeedbackMapper feedbackMapper;
    private final AnomalyEventMapper anomalyEventMapper;
    private final AnomalyRuleEngine.Policy policy;
    private final int duplicateWindowHours;
    private final String ruleVersion;

    public AnomalyRuleService(
            MeasurementMapper measurementMapper,
            FeedbackMapper feedbackMapper,
            AnomalyEventMapper anomalyEventMapper,
            @Value("${app.anomaly.aqi-threshold}")
            int aqiThreshold,
            @Value("${app.anomaly.high-aqi-threshold}")
            int highAqiThreshold,
            @Value("${app.anomaly.jump-threshold}")
            int jumpThreshold,
            @Value("${app.anomaly.high-jump-threshold}")
            int highJumpThreshold,
            @Value("${app.anomaly.duplicate-window-hours}")
            int duplicateWindowHours,
            @Value("${app.anomaly.duplicate-feedback-count}")
            int duplicateFeedbackCount,
            @Value("${app.anomaly.high-duplicate-feedback-count}")
            int highDuplicateFeedbackCount,
            @Value("${app.anomaly.rule-version}")
            String ruleVersion) {

        this.measurementMapper = measurementMapper;
        this.feedbackMapper = feedbackMapper;
        this.anomalyEventMapper = anomalyEventMapper;
        this.duplicateWindowHours = duplicateWindowHours;
        this.ruleVersion = ruleVersion;

        this.policy = new AnomalyRuleEngine.Policy(
                aqiThreshold,
                highAqiThreshold,
                jumpThreshold,
                highJumpThreshold,
                duplicateFeedbackCount,
                highDuplicateFeedbackCount);
    }

    public void evaluate(
            Measurement measurement,
            Feedback feedback) {

        Integer previousAqi =
                measurementMapper
                        .selectPreviousComparableAqi(
                                feedback.getGridId(),
                                measurement.getReportType(),
                                measurement.getId());

        Long recentCount =
                feedbackMapper.selectCount(
                        Wrappers.<Feedback>lambdaQuery()
                                .eq(
                                        Feedback::getGridId,
                                        feedback.getGridId())
                                .ge(
                                        Feedback::getCreatedAt,
                                        LocalDateTime.now()
                                                .minusHours(
                                                        duplicateWindowHours)));

        AnomalyRuleEngine.Result result =
                AnomalyRuleEngine.evaluate(
                        new AnomalyRuleEngine.Input(
                                Byte.valueOf((byte) 1)
                                        .equals(measurement
                                                .getStatisticallyValid()),
                                Byte.valueOf((byte) 1)
                                        .equals(measurement
                                                .getAqiCalculable()),
                                measurement.getAqi(),
                                previousAqi,
                                Math.toIntExact(recentCount)),
                        policy);

        LocalDateTime now = LocalDateTime.now();

        measurement.setRuleStatus(
                result.status());

        measurement.setRuleReason(
                combinedReason(result));

        measurement.setSuggestedPriority(
                result.suggestedPriority());

        measurement.setRuleVersion(ruleVersion);
        measurement.setRuleEvaluatedAt(now);

        if (measurementMapper.updateById(
                measurement) != 1) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "检测规则结果保存失败");
        }

        if (result.pollutionSuspected()) {
            createEventIfAbsent(
                    measurement,
                    feedback,
                    result,
                    now);
        }
    }

    private void createEventIfAbsent(
            Measurement measurement,
            Feedback feedback,
            AnomalyRuleEngine.Result result,
            LocalDateTime now) {

        Long existingCount =
                anomalyEventMapper.selectCount(
                        Wrappers
                                .<AnomalyEvent>lambdaQuery()
                                .eq(
                                        AnomalyEvent::getMeasurementId,
                                        measurement.getId()));

        if (existingCount != null
                && existingCount > 0) {
            return;
        }

        AnomalyEvent event = new AnomalyEvent();
        event.setFeedbackId(feedback.getId());
        event.setTaskId(measurement.getTaskId());
        event.setMeasurementId(
                measurement.getId());
        event.setGridId(feedback.getGridId());
        event.setStatus("PENDING_REVIEW");
        event.setSuggestedPriority(
                result.suggestedPriority());
        event.setTriggerReason(
                String.join(
                        "；",
                        result.triggerReasons()));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        if (anomalyEventMapper.insert(event) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "异常事件保存失败");
        }
    }

    private String combinedReason(
            AnomalyRuleEngine.Result result) {

        List<String> reasons = new ArrayList<>();

        for (String reason
                : result.qualityReasons()) {
            reasons.add("数据质量：" + reason);
        }

        for (String reason
                : result.triggerReasons()) {
            reasons.add("疑似污染：" + reason);
        }

        return reasons.isEmpty()
                ? "未触发异常规则"
                : String.join("；", reasons);
    }
}
