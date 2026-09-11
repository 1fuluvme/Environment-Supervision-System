package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AiQuestionRequest(

        @NotBlank(message = "问题不能为空")
        @Size(max = 300, message = "问题不能超过300个字符")
        String question,

        @NotNull(message = "区域ID不能为空")
        @Positive(message = "区域ID必须是正整数")
        Long regionId,

        @NotNull(message = "开始日期不能为空")
        LocalDate startDate,

        @NotNull(message = "结束日期不能为空")
        LocalDate endDate,

        @NotBlank(message = "统计口径不能为空")
        String reportType,

        @Positive(message = "异常事件ID必须是正整数")
        Long anomalyEventId
) {
}
