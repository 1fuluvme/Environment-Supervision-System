package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateFeedbackRequest(
        @NotNull(message = "请选择网格")
        @Positive(message = "网格ID必须是正整数")
        Long gridId,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址不能超过255个字符")
        String address,

        @NotNull(message = "观测时间不能为空")
        @PastOrPresent(message = "观测时间不能晚于当前时间")
        LocalDateTime observedAt,

        @NotBlank(message = "问题描述不能为空")
        @Size(max = 1000, message = "问题描述不能超过1000个字符")
        String description
) {
}