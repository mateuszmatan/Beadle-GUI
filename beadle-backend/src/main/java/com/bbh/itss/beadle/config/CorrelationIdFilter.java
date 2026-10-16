package com.bbh.itss.beadle.config;

import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.CallContext.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.bbh.itss.beadle.application.CallContext.isCorrelationId;
import static java.util.Locale.ROOT;
import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

/**
 * Gives every request the correlation id of the user action it is. A request of a user gets a new UUID, whatever it
 * sends in {@code X-Correlation-Id}: Beadle-GUI mints the id of every user action (docs/CONTRACTS.md), so a user cannot
 * file an action under the id of another. Only a callback of Beadle-Internal or Beadle-ProTech
 * ({@value SecurityConfiguration#CALLBACKS}, which needs the service token) keeps the id it brings, the one Beadle-GUI
 * minted for the action that started the work. The id is bound to the request's thread for the calls to the other
 * services and the audit trail, put in the log context as {@code correlationId} and sent back in the response header.
 */
@Component
@Order(HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String LOG_KEY = "correlationId";

    private static final RequestMatcher CALLBACKS =
            PathPatternRequestMatcher.withDefaults().matcher(SecurityConfiguration.CALLBACKS);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CALLBACKS.matches(request)
                ? correlationIdOf(request.getHeader(ServiceHeaders.CORRELATION_ID)) : minted();
        response.setHeader(ServiceHeaders.CORRELATION_ID, correlationId);
        MDC.put(LOG_KEY, correlationId);
        try (Scope ignored = CallContext.correlatedBy(correlationId)) {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(LOG_KEY);
        }
    }

    /** The id a callback brings when it is a UUID, otherwise a new one. */
    static String correlationIdOf(String header) {
        return isCorrelationId(header) ? header.trim().toLowerCase(ROOT) : minted();
    }

    private static String minted() {
        return UUID.randomUUID().toString();
    }
}
