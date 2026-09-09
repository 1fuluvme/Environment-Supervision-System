package com.neps.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserEnabledRequest(
        @NotNull(message = "必须指定是否启用")
        Boolean enabled
) {
}
