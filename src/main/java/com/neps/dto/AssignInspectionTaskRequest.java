package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AssignInspectionTaskRequest(
        @NotNull(message = "请选择网格员")
        @Positive(message = "网格员ID必须是正整数")
        Long assigneeId,

        @NotBlank(message = "核查要求不能为空")
        @Size(max = 500, message = "核查要求不能超过500个字符")
        String requirement,

        @NotBlank(message = "优先级不能为空")
        @Pattern(
                regexp = "LOW|MEDIUM|HIGH",
                message = "优先级只能是LOW、MEDIUM或HIGH")
        String priority
) {
}
