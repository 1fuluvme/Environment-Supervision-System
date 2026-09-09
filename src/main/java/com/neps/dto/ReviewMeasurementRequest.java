package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewMeasurementRequest(

        @NotBlank(message = "复核决定不能为空")
        @Pattern(
                regexp = "RETURN|COMPLETE",
                message = "复核决定只能是RETURN或COMPLETE")
        String decision,

        @NotBlank(message = "复核意见不能为空")
        @Size(max = 1000, message = "复核意见不能超过1000个字符")
        String opinion,

        @Size(max = 1000, message = "公众办理说明不能超过1000个字符")
        String publicReply) {
}
