package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignWorkOrderRequest(

        @NotNull(message = "请选择网格员")
        @Positive(message = "网格员ID必须是正整数")
        Long assigneeId,

        @NotBlank(message = "处置要求不能为空")
        @Size(
                max = 500,
                message = "处置要求不能超过500个字符")
        String requirement) {
}
