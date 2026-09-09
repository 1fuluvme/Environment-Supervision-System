package com.neps.rule;

import java.util.ArrayList;
import java.util.List;

public final class AnomalyRuleEngine {

    private AnomalyRuleEngine() {
    }

    public record Policy(
            int aqiThreshold,
            int highAqiThreshold,
            int jumpThreshold,
            int highJumpThreshold,
            int duplicateFeedbackCount,
            int highDuplicateFeedbackCount) {

        public Policy {
            if (aqiThreshold < 0
                    || highAqiThreshold < aqiThreshold
                    || jumpThreshold < 0
                    || highJumpThreshold < jumpThreshold
                    || duplicateFeedbackCount < 1
                    || highDuplicateFeedbackCount
                    < duplicateFeedbackCount) {

                throw new IllegalArgumentException(
                        "异常检测规则参数不正确");
            }
        }
    }

    public record Input(
            boolean statisticallyValid,
            boolean aqiCalculable,
            Integer aqi,
            Integer previousComparableAqi,
            int recentFeedbackCount) {
    }

    public record Result(
            String status,
            boolean pollutionSuspected,
            String suggestedPriority,
            List<String> qualityReasons,
            List<String> triggerReasons) {
    }

    public static Result evaluate(
            Input input,
            Policy policy) {

        if (input == null || policy == null) {
            throw new IllegalArgumentException(
                    "规则输入和规则配置不能为空");
        }

        List<String> qualityReasons =
                new ArrayList<>();

        List<String> triggerReasons =
                new ArrayList<>();

        String priority = null;

        if (!input.statisticallyValid()) {
            qualityReasons.add(
                    "检测数据不满足统计有效性要求");
        }

        if (!input.aqiCalculable()) {
            qualityReasons.add(
                    "当前检测记录不能计算AQI");
        }

        if (input.aqi() != null
                && input.aqi()
                > policy.aqiThreshold()) {

            triggerReasons.add(
                    "AQI为"
                            + input.aqi()
                            + "，超过项目触发值"
                            + policy.aqiThreshold());

            if (input.aqi()
                    > policy.highAqiThreshold()) {
                priority = higher(priority, "HIGH");
            } else if (input.aqi() > 150) {
                priority = higher(priority, "MEDIUM");
            } else {
                priority = higher(priority, "LOW");
            }
        }

        if (input.aqi() != null
                && input.previousComparableAqi() != null) {

            int difference = Math.abs(
                    input.aqi()
                            - input.previousComparableAqi());

            if (difference
                    >= policy.jumpThreshold()) {

                triggerReasons.add(
                        "与同网格上一条同口径有效记录相比，"
                                + "AQI变化"
                                + difference);

                priority = higher(
                        priority,
                        difference
                                >= policy.highJumpThreshold()
                                ? "HIGH"
                                : "MEDIUM");
            }
        }

        if (input.recentFeedbackCount()
                >= policy.duplicateFeedbackCount()) {

            triggerReasons.add(
                    "同网格近期反馈数量为"
                            + input.recentFeedbackCount()
                            + "，达到项目集中反馈触发值"
                            + policy.duplicateFeedbackCount());

            priority = higher(
                    priority,
                    input.recentFeedbackCount()
                            >= policy.highDuplicateFeedbackCount()
                            ? "HIGH"
                            : "MEDIUM");
        }

        boolean pollutionSuspected =
                !triggerReasons.isEmpty();

        String status;

        if (pollutionSuspected) {
            status = "SUSPECTED";
        } else if (!qualityReasons.isEmpty()) {
            status = "QUALITY_ISSUE";
        } else {
            status = "NORMAL";
        }

        return new Result(
                status,
                pollutionSuspected,
                priority,
                List.copyOf(qualityReasons),
                List.copyOf(triggerReasons));
    }

    private static String higher(
            String current,
            String candidate) {

        if (current == null) {
            return candidate;
        }

        return rank(candidate) > rank(current)
                ? candidate
                : current;
    }

    private static int rank(String priority) {
        return switch (priority) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            default -> 0;
        };
    }
}