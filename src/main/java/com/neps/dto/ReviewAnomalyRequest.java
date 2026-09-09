package com.neps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewAnomalyRequest(

        @NotBlank(message = "事件复核决定不能为空")
        @Pattern(
                regexp = "CONFIRM|EXCLUDE",
                message = "事件复核决定只能是CONFIRM或EXCLUDE")
        String decision,

        @Pattern(
                regexp = "LOW|MEDIUM|HIGH",
                message = "确认优先级只能是LOW、MEDIUM或HIGH")
        String confirmedPriority,

        @NotBlank(message = "事件复核理由不能为空")
        @Size(max = 1000, message = "事件复核理由不能超过1000个字符")
        String reviewReason,

        @Size(max = 1000, message = "公众办理说明不能超过1000个字符")
        String publicReply) {
}
