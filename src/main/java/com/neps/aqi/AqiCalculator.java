package com.neps.aqi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AqiCalculator {

    private static final int[] IAQI_POINTS =
            {0, 50, 100, 150, 200, 300, 400, 500};

    private AqiCalculator() {
    }

    public enum ReportType {
        DAILY,
        REALTIME
    }

    public enum Pollutant {

        SO2(
                "二氧化硫（SO₂）",
                false,
                "0,50,150,475,800,1600,2100,2620",
                "0,150,500,650,800"),

        NO2(
                "二氧化氮（NO₂）",
                false,
                "0,40,80,180,280,565,750,940",
                "0,100,200,700,1200,2340,3090,3840"),

        CO(
                "一氧化碳（CO）",
                true,
                "0,2,4,14,24,36,48,60",
                "0,5,10,35,60,90,120,150"),

        O3(
                "臭氧（O₃）",
                false,
                "0,100,160,215,265,800",
                "0,160,200,300,400,800,1000,1200"),

        PM10(
                "可吸入颗粒物（PM₁₀）",
                false,
                "0,50,120,250,350,420,500,600",
                "0,50,120,250,350,420,500,600"),

        PM25(
                "细颗粒物（PM₂.₅）",
                false,
                "0,35,60,115,150,250,350,500",
                "0,35,60,115,150,250,350,500");

        private final String displayName;
        private final boolean co;
        private final BigDecimal[] dailyBreakpoints;
        private final BigDecimal[] realtimeBreakpoints;

        Pollutant(
                String displayName,
                boolean co,
                String dailyBreakpoints,
                String realtimeBreakpoints) {

            this.displayName = displayName;
            this.co = co;
            this.dailyBreakpoints = parse(dailyBreakpoints);
            this.realtimeBreakpoints = parse(realtimeBreakpoints);
        }

        public String displayName() {
            return displayName;
        }

        private BigDecimal[] breakpoints(ReportType reportType) {
            return reportType == ReportType.DAILY
                    ? dailyBreakpoints
                    : realtimeBreakpoints;
        }

        private BigDecimal normalize(BigDecimal concentration) {
            int scale = co ? 1 : 0;

            return concentration.setScale(
                    scale,
                    RoundingMode.HALF_EVEN);
        }

        private static BigDecimal[] parse(String text) {
            String[] values = text.split(",");
            BigDecimal[] result = new BigDecimal[values.length];

            for (int i = 0; i < values.length; i++) {
                result[i] = new BigDecimal(values[i]);
            }

            return result;
        }
    }

    public record Input(
            ReportType reportType,
            boolean statisticallyValid,
            String invalidReason,
            BigDecimal so2,
            BigDecimal no2,
            BigDecimal co,
            BigDecimal o3,
            BigDecimal pm10,
            BigDecimal pm25) {
    }

    public record Result(
            boolean calculable,
            Map<Pollutant, Integer> iaqiValues,
            Integer aqi,
            Integer level,
            String category,
            List<Pollutant> primaryPollutants,
            String reason) {
    }

    public static Result calculate(Input input) {
        Objects.requireNonNull(input, "AQI输入不能为空");
        Objects.requireNonNull(
                input.reportType(),
                "统计时段不能为空");

        if (!input.statisticallyValid()) {
            String reason = input.invalidReason();

            if (reason == null || reason.isBlank()) {
                reason = "监测数据不满足统计有效性要求";
            }

            return notCalculable(reason);
        }

        EnumMap<Pollutant, BigDecimal> concentrations =
                new EnumMap<>(Pollutant.class);

        concentrations.put(Pollutant.SO2, input.so2());
        concentrations.put(Pollutant.NO2, input.no2());
        concentrations.put(Pollutant.CO, input.co());
        concentrations.put(Pollutant.O3, input.o3());
        concentrations.put(Pollutant.PM10, input.pm10());
        concentrations.put(Pollutant.PM25, input.pm25());

        EnumMap<Pollutant, Integer> iaqiValues =
                new EnumMap<>(Pollutant.class);

        for (Map.Entry<Pollutant, BigDecimal> entry
                : concentrations.entrySet()) {

            if (entry.getValue() != null) {
                iaqiValues.put(
                        entry.getKey(),
                        calculateIaqi(
                                entry.getKey(),
                                input.reportType(),
                                entry.getValue()));
            }
        }

        if (iaqiValues.isEmpty()) {
            return notCalculable("六项污染物浓度均缺测");
        }

        int aqi = Collections.max(iaqiValues.values());

        List<Pollutant> primaryPollutants =
                aqi <= 50
                        ? List.of()
                        : iaqiValues.entrySet()
                        .stream()
                        .filter(entry -> entry.getValue() == aqi)
                        .map(Map.Entry::getKey)
                        .toList();

        return new Result(
                true,
                Collections.unmodifiableMap(iaqiValues),
                aqi,
                level(aqi),
                category(aqi),
                primaryPollutants,
                null);
    }

    private static int calculateIaqi(
            Pollutant pollutant,
            ReportType reportType,
            BigDecimal originalConcentration) {

        if (originalConcentration.signum() < 0) {
            throw new IllegalArgumentException(
                    pollutant.displayName() + "浓度不能小于0");
        }

        BigDecimal concentration =
                pollutant.normalize(originalConcentration);

        BigDecimal[] breakpoints =
                pollutant.breakpoints(reportType);

        int last = breakpoints.length - 1;

        /*
         * SO₂实时报超过800时按200计；
         * O₃日报超过800时按300计；
         * 其他污染物超过最高分段时按500计。
         */
        if (concentration.compareTo(breakpoints[last]) >= 0) {
            return IAQI_POINTS[last];
        }

        for (int i = 1; i < breakpoints.length; i++) {
            BigDecimal high = breakpoints[i];

            if (concentration.compareTo(high) <= 0) {
                BigDecimal low = breakpoints[i - 1];

                BigDecimal result = concentration
                        .subtract(low)
                        .multiply(BigDecimal.valueOf(
                                IAQI_POINTS[i]
                                        - IAQI_POINTS[i - 1]))
                        .divide(
                                high.subtract(low),
                                12,
                                RoundingMode.HALF_UP)
                        .add(BigDecimal.valueOf(
                                IAQI_POINTS[i - 1]));

                return result.setScale(
                        0,
                        RoundingMode.CEILING).intValue();
            }
        }

        throw new IllegalStateException("没有找到对应的AQI分段");
    }

    private static Result notCalculable(String reason) {
        return new Result(
                false,
                Map.of(),
                null,
                null,
                null,
                List.of(),
                reason);
    }

    private static int level(int aqi) {
        if (aqi <= 50) {
            return 1;
        }
        if (aqi <= 100) {
            return 2;
        }
        if (aqi <= 150) {
            return 3;
        }
        if (aqi <= 200) {
            return 4;
        }
        if (aqi <= 300) {
            return 5;
        }
        return 6;
    }

    private static String category(int aqi) {
        return switch (level(aqi)) {
            case 1 -> "优";
            case 2 -> "良";
            case 3 -> "轻度污染";
            case 4 -> "中度污染";
            case 5 -> "重度污染";
            default -> "严重污染";
        };
    }
}