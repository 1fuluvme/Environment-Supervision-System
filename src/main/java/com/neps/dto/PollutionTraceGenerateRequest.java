package com.neps.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PollutionTraceGenerateRequest(
        @NotNull(message = "请选择异常事件")
        @Positive(message = "异常事件ID必须是正整数")
        Long anomalyEventId,

        @NotNull(message = "请选择是否使用演示数据")
        Boolean demo
) {
}
