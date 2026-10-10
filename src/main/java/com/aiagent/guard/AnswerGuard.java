package com.aiagent.guard;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AnswerGuard {
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)");

    public Decision validate(String answer) {
        if (answer == null || answer.isBlank()) return new Decision(false, "EMPTY_ANSWER");
        if (PHONE.matcher(answer).find()) return new Decision(false, "SENSITIVE_PHONE");
        return new Decision(true, "OK");
    }

    public record Decision(boolean allowed, String reason) {}
}
