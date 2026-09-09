package com.neps.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SubmitMeasurementRequest(

        @NotNull(message = "检测时间不能为空")
        @PastOrPresent(message = "检测时间不能晚于当前时间")
        LocalDateTime measuredAt,

        @NotBlank(message = "检测位置不能为空")
        @Size(max = 255, message = "检测位置不能超过255个字符")
        String location,

        @NotBlank(message = "统计时段不能为空")
        @Pattern(
                regexp = "INSTANT|REALTIME|DAILY",
                message = "统计时段只能是INSTANT、REALTIME或DAILY")
        String reportType,

        @NotBlank(message = "数据来源不能为空")
        @Size(max = 100, message = "数据来源不能超过100个字符")
        String dataSource,

        @DecimalMin(value = "0", message = "SO2浓度不能小于0")
        @Digits(integer = 8, fraction = 2, message = "SO2浓度格式不正确")
        BigDecimal so2,

        @DecimalMin(value = "0", message = "NO2浓度不能小于0")
        @Digits(integer = 8, fraction = 2, message = "NO2浓度格式不正确")
        BigDecimal no2,

        @DecimalMin(value = "0", message = "CO浓度不能小于0")
        @Digits(integer = 8, fraction = 2, message = "CO浓度格式不正确")
        BigDecimal co,

        @DecimalMin(value = "0", message = "O3浓度不能小于0")
        @Digits(integer = 8, fraction = 2, message = "O3浓度格式不正确")
        BigDecimal o3,

        @DecimalMin(value = "0", message = "PM10浓度不能小于0")
        @Digits(integer = 8, fraction = 2, message = "PM10浓度格式不正确")
        BigDecimal pm10,

        @DecimalMin(value = "0", message = "PM2.5浓度不能小于0")
        @Digits(integer = 8, fraction = 2, message = "PM2.5浓度格式不正确")
        BigDecimal pm25,

        @Size(max = 500, message = "缺测原因不能超过500个字符")
        String missingReason,

        @NotNull(message = "必须说明数据是否满足统计有效性")
        Boolean statisticallyValid,

        @Size(max = 500, message = "统计无效原因不能超过500个字符")
        String invalidReason,

        @Size(max = 1000, message = "现场说明不能超过1000个字符")
        String siteNote) {
}
