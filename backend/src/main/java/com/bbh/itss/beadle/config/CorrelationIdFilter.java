package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.CallContext.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.bbh.itss.beadle.application.CallContext.isCorrelationId;
import static java.util.Locale.ROOT;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

/**
 * Gives every request the correlation id of the user action it is: a new UUID, or the one a callback of Beadle-Internal
 * or Beadle-ProTech brings in {@code X-Correlation-Id}. The id is bound to the request's thread for the calls to the
 * other services and the audit trail, put in the log context as {@code correlationId} and sent back in the response
 * header.
 */
@Component
@Order(HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String LOG_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = correlationIdOf(request.getHeader(ServiceHeaders.CORRELATION_ID));
        response.setHeader(ServiceHeaders.CORRELATION_ID, correlationId);
        MDC.put(LOG_KEY, correlationId);
        try (Scope ignored = CallContext.correlatedBy(correlationId)) {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LOG_KEY);
        }
    }

    static String correlationIdOf(String header) {
        return isCorrelationId(header) ? header.trim().toLowerCase(ROOT) : UUID.randomUUID().toString();
    }
}
