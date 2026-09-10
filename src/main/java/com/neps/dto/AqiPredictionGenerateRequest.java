package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AqiPredictionGenerateRequest(
        @NotNull(message = "请选择区域")
        @Positive(message = "区域ID必须是正整数")
        Long regionId,

        @NotNull(message = "请选择预测目标日期")
        LocalDate targetDate,

        @NotBlank(message = "数据来源不能为空")
        @Size(max = 100, message = "数据来源不能超过100个字符")
        String sourceName,

        @NotNull(message = "请选择是否使用演示数据")
        Boolean demo
) {
}
