package com.aiagent.tool;

import java.util.Optional;

public interface OrderQueryService {
    Optional<OrderQueryResult> query(String userId, OrderQueryRequest request);
}
