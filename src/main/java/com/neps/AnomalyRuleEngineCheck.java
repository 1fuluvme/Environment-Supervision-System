package com.neps;

import com.neps.rule.AnomalyRuleEngine;

public class AnomalyRuleEngineCheck {

    private static final AnomalyRuleEngine.Policy POLICY =
            new AnomalyRuleEngine.Policy(
                    100,
                    200,
                    80,
                    150,
                    3,
                    5);

    public static void main(String[] args) {
        checkNormal();
        checkQualityIssue();
        checkAqiThreshold();
        checkJump();
        checkDuplicateFeedback();
        checkCombinedPriority();

        System.out.println(
                "异常规则引擎检查全部通过");
    }

    private static void checkNormal() {
        var result = evaluate(
                true,
                true,
                80,
                70,
                1);

        require(
                "NORMAL".equals(result.status()),
                "正常数据不应触发异常");

        require(
                !result.pollutionSuspected(),
                "正常数据不应生成疑似污染事件");
    }

    private static void checkQualityIssue() {
        var result = evaluate(
                false,
                false,
                null,
                null,
                1);

        require(
                "QUALITY_ISSUE".equals(
                        result.status()),
                "无效数据应产生质量问题");

        require(
                !result.pollutionSuspected(),
                "质量问题不应自动当作污染事件");
    }

    private static void checkAqiThreshold() {
        var result = evaluate(
                true,
                true,
                160,
                150,
                1);

        require(
                "SUSPECTED".equals(
                        result.status()),
                "AQI超限应触发疑似污染");

        require(
                "MEDIUM".equals(
                        result.suggestedPriority()),
                "AQI=160应建议中优先级");
    }

    private static void checkJump() {
        var result = evaluate(
                true,
                true,
                140,
                50,
                1);

        require(
                result.pollutionSuspected(),
                "AQI突变应触发疑似污染");

        require(
                result.triggerReasons()
                        .stream()
                        .anyMatch(reason ->
                                reason.contains("变化90")),
                "应记录AQI变化幅度");
    }

    private static void checkDuplicateFeedback() {
        var result = evaluate(
                true,
                true,
                70,
                65,
                3);

        require(
                result.pollutionSuspected(),
                "集中反馈应触发疑似污染");

        require(
                "MEDIUM".equals(
                        result.suggestedPriority()),
                "三条集中反馈应建议中优先级");
    }

    private static void checkCombinedPriority() {
        var result = evaluate(
                true,
                true,
                260,
                50,
                5);

        require(
                "HIGH".equals(
                        result.suggestedPriority()),
                "多个规则触发时应取最高优先级");

        require(
                result.triggerReasons().size() == 3,
                "应同时保留三个触发理由");
    }

    private static AnomalyRuleEngine.Result evaluate(
            boolean statisticallyValid,
            boolean aqiCalculable,
            Integer aqi,
            Integer previousAqi,
            int recentFeedbackCount) {

        return AnomalyRuleEngine.evaluate(
                new AnomalyRuleEngine.Input(
                        statisticallyValid,
                        aqiCalculable,
                        aqi,
                        previousAqi,
                        recentFeedbackCount),
                POLICY);
    }

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
