package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record SubmitWorkOrderResultRequest(

        @NotNull(message = "处理时间不能为空")
        @PastOrPresent(message = "处理时间不能晚于当前时间")
        LocalDateTime handledAt,

        @NotBlank(message = "处置措施不能为空")
        @Size(
                max = 2000,
                message = "处置措施不能超过2000个字符")
        String measures,

        @NotBlank(message = "处置结果不能为空")
        @Size(
                max = 2000,
                message = "处置结果不能超过2000个字符")
        String result) {
}
