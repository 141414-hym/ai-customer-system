package com.aiagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(
          @NotBlank(message = "sessionId不能为空")
          @Size(max = 64, message = "sessionId长度不能超过64")
          String sessionId,

          @NotBlank(message = "clientMessageId不能为空")
          @Size(max = 128, message = "clientMessageId长度不能超过128")
          String clientMessageId,

          @NotBlank(message = "message不能为空")
          @Size(max = 16000, message = "message长度不能超过16000")
          String message,

          String locale,
          String tenantId,
          String productCode,
          List<String> tags
  ) {
  }
