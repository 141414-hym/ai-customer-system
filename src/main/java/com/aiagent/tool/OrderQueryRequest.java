package com.aiagent.tool;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderQueryRequest(
        @NotBlank @Size(max = 32)
        @Pattern(regexp = "^(XP|ORD)\\d{6,}$")
        String orderId) {}
