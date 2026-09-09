package com.neps;

import com.neps.aqi.AqiCalculator;
import com.neps.aqi.AqiCalculator.Input;
import com.neps.aqi.AqiCalculator.Pollutant;
import com.neps.aqi.AqiCalculator.ReportType;
import com.neps.aqi.AqiCalculator.Result;

import java.math.BigDecimal;
import java.util.List;

public class AqiCalculatorCheck {

    public static void main(String[] args) {
        checkPm25Endpoint();
        checkIntervalAndCeiling();
        checkTiedPrimaryPollutants();
        checkSpecialUpperLimits();
        checkMissingAndInvalidData();
        checkNegativeValue();

        System.out.println("HJ 633—2026 AQI计算检查全部通过");
    }

    private static void checkPm25Endpoint() {
        Result result = daily(
                null, null, null,
                null, null, "60");

        require(result.aqi() == 100,
                "PM2.5=60时AQI应为100");

        require(result.level() == 2,
                "AQI=100应为二级");

        require("良".equals(result.category()),
                "AQI=100应为良");
    }

    private static void checkIntervalAndCeiling() {
        Result result = daily(
                null, null, null,
                null, "85", null);

        require(result.aqi() == 75,
                "PM10=85时AQI应为75");

        Result ceilingResult = daily(
                null, null, null,
                null, null, "36");

        require(ceilingResult.aqi() == 52,
                "IAQI存在小数时应向上取整");
    }

    private static void checkTiedPrimaryPollutants() {
        Result result = daily(
                "150", null, null,
                null, "120", null);

        require(result.aqi() == 100,
                "并列样例AQI应为100");

        require(
                result.primaryPollutants().containsAll(
                        List.of(Pollutant.SO2, Pollutant.PM10)),
                "SO2和PM10应并列为首要污染物");

        require(result.primaryPollutants().size() == 2,
                "首要污染物应正好有两项");
    }

    private static void checkSpecialUpperLimits() {
        Result dailyO3 = daily(
                null, null, null,
                "801", null, null);

        require(dailyO3.aqi() == 300,
                "O3日报超过800时IAQI应按300计");

        Result realtimeSo2 = AqiCalculator.calculate(
                input(
                        ReportType.REALTIME,
                        true,
                        null,
                        "801", null, null,
                        null, null, null));

        require(realtimeSo2.aqi() == 200,
                "SO2实时报超过800时IAQI应按200计");
    }

    private static void checkMissingAndInvalidData() {
        Result allMissing = daily(
                null, null, null,
                null, null, null);

        require(!allMissing.calculable(),
                "六项全部缺测时不应计算AQI");

        Result invalid = AqiCalculator.calculate(
                input(
                        ReportType.DAILY,
                        false,
                        "未形成完整自然日数据",
                        "50", "40", "2",
                        "100", "50", "35"));

        require(!invalid.calculable(),
                "统计时段无效时不应计算AQI");

        require("未形成完整自然日数据"
                        .equals(invalid.reason()),
                "应当保留不能计算的原因");
    }

    private static void checkNegativeValue() {
        try {
            daily(
                    null, null, null,
                    null, null, "-1");

            throw new IllegalStateException(
                    "负数浓度应当被拒绝");
        } catch (IllegalArgumentException expected) {
            require(
                    expected.getMessage().contains("不能小于0"),
                    "负数错误提示不正确");
        }
    }

    private static Result daily(
            String so2,
            String no2,
            String co,
            String o3,
            String pm10,
            String pm25) {

        return AqiCalculator.calculate(
                input(
                        ReportType.DAILY,
                        true,
                        null,
                        so2,
                        no2,
                        co,
                        o3,
                        pm10,
                        pm25));
    }

    private static Input input(
            ReportType reportType,
            boolean statisticallyValid,
            String invalidReason,
            String so2,
            String no2,
            String co,
            String o3,
            String pm10,
            String pm25) {

        return new Input(
                reportType,
                statisticallyValid,
                invalidReason,
                number(so2),
                number(no2),
                number(co),
                number(o3),
                number(pm10),
                number(pm25));
    }

    private static BigDecimal number(String value) {
        return value == null
                ? null
                : new BigDecimal(value);
    }

    private static void require(
            boolean condition,
            String message) {

        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
