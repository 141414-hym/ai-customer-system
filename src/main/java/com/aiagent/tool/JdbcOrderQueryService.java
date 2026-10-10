package com.aiagent.tool;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JdbcOrderQueryService implements OrderQueryService {
    private final JdbcTemplate jdbc;

    public JdbcOrderQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<OrderQueryResult> query(String userId, OrderQueryRequest request) {
        return jdbc.query("""
                SELECT order_no, order_status, estimated_delivery_time, product_name
                FROM customer_order
                WHERE user_id=? AND order_no=? LIMIT 1
                """, rs -> rs.next() ? Optional.of(new OrderQueryResult(
                rs.getString("order_no"), rs.getString("order_status"),
                rs.getString("estimated_delivery_time"), rs.getString("product_name")))
                : Optional.empty(), userId, request.orderId());
    }
}
