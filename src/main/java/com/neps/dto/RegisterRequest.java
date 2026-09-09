package com.neps.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(
                regexp = "1[3-9][0-9]{9}",
                message = "请输入格式正确的11位手机号")
        String phone,

        @NotBlank(message = "显示名称不能为空")
        @Size(max = 50, message="显示名称不能超过50个字符")
        String displayName,

        @NotBlank
        @Size(min = 8,max = 64,message = "密码需为8～64个字符")
        String password
) {
}