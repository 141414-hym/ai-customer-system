package com.aiagent.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String TRACE_ID = "traceId";
    public static final String SPAN_ID = "spanId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank() || traceId.length() > 128) {
            traceId = UUID.randomUUID().toString();
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(TRACE_ID, traceId)) {
            String spanId = UUID.randomUUID().toString();
            try (MDC.MDCCloseable ignoredSpan = MDC.putCloseable(SPAN_ID, spanId)) {
            response.setHeader("X-Trace-Id", traceId);
            response.setHeader("X-Span-Id", spanId);
            filterChain.doFilter(request, response);
            }
        }
    }
}
