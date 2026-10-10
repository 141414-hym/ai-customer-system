package com.aiagent.tool;

public record OrderQueryResult(String orderId, String status, String estimatedDeliveryTime,
                               String productName) {}
