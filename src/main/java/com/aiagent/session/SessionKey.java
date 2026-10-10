package com.aiagent.session;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record SessionKey(String userId, String sessionId) {

      public SessionKey {
          userId = requireText(userId, "userId");
          sessionId = requireText(sessionId, "sessionId");
      }

      public String threadId() {
          return encode(userId) + "." + encode(sessionId);
      }

      private static String requireText(String value, String fieldName) {
          if (value == null || value.isBlank()) {
              throw new IllegalArgumentException(fieldName + "不能为空");
          }
          return value.trim();
      }

      private static String encode(String value) {
          return Base64.getUrlEncoder()
                  .withoutPadding()
                  .encodeToString(value.getBytes(StandardCharsets.UTF_8));
      }
  }
