package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.adapter.ServiceHeaders;
import com.bbh.itss.beadle.application.CallContext;
import com.bbh.itss.beadle.application.CallContext.Scope;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static com.bbh.itss.beadle.domain.shared.Text.bytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.Strings.CS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;

/**
 * Lets only Beadle-Internal and Beadle-ProTech call back: every request to {@value #CALLBACKS} must carry the service
 * token the three Beadle services share as {@code Authorization: Bearer <token>}, or it is answered {@code 401}; a
 * blank token refuses every callback. As in Beadle-Internal and Beadle-ProTech, an {@code X-Correlation-Id} longer than
 * {@value #CORRELATION_MAX} bytes in UTF-8 is answered {@code 400}. What an accepted callback causes runs for the
 * system that called, named after its path: {@code system:protech} for {@code /api/callbacks/protech}. A callback
 * without the token is only logged; one refused for its correlation id is a {@code FAILURE} row of the audit trail by
 * the system ({@link RefusedRequests}).
 */
@Slf4j
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String CALLBACKS = "/api/callbacks/**";
    public static final String BEARER = "Bearer ";
    public static final String DETAIL = "The call needs the Beadle service token as Authorization: Bearer <token>.";
    static final String PROBLEM = "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"" + DETAIL + "\"}";
    public static final int CORRELATION_MAX = 64;
    static final String CORRELATION_TOO_LONG = "is too long: it may take at most " + CORRELATION_MAX + " bytes";
    static final String CORRELATION_PROBLEM = "{\"title\":\"Validation failed\","
            + "\"status\":400,\"detail\":\"" + ServiceHeaders.CORRELATION_ID + " " + CORRELATION_TOO_LONG
            + "\",\"problems\":[{\"field\":\"" + ServiceHeaders.CORRELATION_ID + "\",\"message\":\""
            + CORRELATION_TOO_LONG + "\"}]}";

    private static final RequestMatcher CALLBACK = PathPatternRequestMatcher.withDefaults().matcher(CALLBACKS);
    private static final RequestMatcher SYSTEM = PathPatternRequestMatcher.withDefaults()
            .matcher("/api/callbacks/{system}");

    private final byte[] token;
    private final RefusedRequests refusals;

    public ServiceTokenFilter(String token, RefusedRequests refusals) {
        this.token = trimToEmpty(token).getBytes(UTF_8);
        this.refusals = refusals;
        if (this.token.length == 0) {
            log.warn("beadle.services.token (BEADLE_SERVICE_TOKEN) is blank: every callback is refused");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CALLBACK.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!accepts(request.getHeader(AUTHORIZATION))) {
            log.warn("Refused {} {}: missing or wrong service token", request.getMethod(), request.getRequestURI());
            response.setHeader(WWW_AUTHENTICATE, "Bearer");
            problem(response, UNAUTHORIZED.value(), PROBLEM);
            return;
        }
        String system = SYSTEM.matcher(request).getVariables().getOrDefault("system", "unknown");
        try (Scope actor = CallContext.actingAs(CallContext.SYSTEM + ":" + system)) {
            if (bytes(trimToEmpty(request.getHeader(ServiceHeaders.CORRELATION_ID))) > CORRELATION_MAX) {
                log.warn("Refused {} {}: X-Correlation-Id {}", request.getMethod(), request.getRequestURI(),
                        CORRELATION_TOO_LONG);
                refusals.record(request, forStatusAndDetail(BAD_REQUEST,
                        ServiceHeaders.CORRELATION_ID + " " + CORRELATION_TOO_LONG));
                problem(response, BAD_REQUEST.value(), CORRELATION_PROBLEM);
                return;
            }
            chain.doFilter(request, response);
        }
    }

    private boolean accepts(String authorization) {
        if (token.length == 0 || isBlank(authorization) || !authorization.startsWith(BEARER)) {
            return false;
        }
        return isEqual(token, CS.removeStart(authorization, BEARER).trim().getBytes(UTF_8));
    }

    private static void problem(HttpServletResponse response, int status, String problem) throws IOException {
        response.setStatus(status);
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(problem);
    }
}
