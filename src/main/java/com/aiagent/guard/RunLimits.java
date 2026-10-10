package com.aiagent.guard;

public record RunLimits(int maxGraphNodes, int maxToolCalls, int maxInputChars) {
    public RunLimits {
        if (maxGraphNodes < 1 || maxToolCalls < 0 || maxInputChars < 1) {
            throw new IllegalArgumentException("运行预算必须为正数");
        }
    }
    public static RunLimits defaults() { return new RunLimits(12, 3, 16000); }
    public void checkInput(String input) {
        if (input == null || input.length() > maxInputChars) {
            throw new IllegalArgumentException("请求上下文超过单次执行预算");
        }
    }
}
