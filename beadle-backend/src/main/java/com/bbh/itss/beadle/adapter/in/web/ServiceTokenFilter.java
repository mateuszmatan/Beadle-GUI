package com.bbh.itss.beadle.adapter.in.web;

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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.security.MessageDigest.isEqual;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.Strings.CS;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;

/**
 * Lets only Beadle-Internal and Beadle-ProTech call back: every request to {@value #CALLBACKS} must carry the service
 * token the three Beadle services share as {@code Authorization: Bearer <token>}, or it is answered {@code 401}; a
 * blank token refuses every callback. What an accepted callback causes runs for the system that called, named after
 * its path: {@code system:protech} for {@code /api/callbacks/protech}.
 */
@Slf4j
public class ServiceTokenFilter extends OncePerRequestFilter {

    public static final String CALLBACKS = "/api/callbacks/**";
    public static final String BEARER = "Bearer ";
    public static final String DETAIL = "The call needs the Beadle service token as Authorization: Bearer <token>.";
    static final String PROBLEM = "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401,\"detail\":\""
            + DETAIL + "\"}";

    private static final RequestMatcher CALLBACK = PathPatternRequestMatcher.withDefaults().matcher(CALLBACKS);
    private static final RequestMatcher SYSTEM = PathPatternRequestMatcher.withDefaults()
            .matcher("/api/callbacks/{system}");

    private final byte[] token;

    public ServiceTokenFilter(String token) {
        this.token = trimToEmpty(token).getBytes(UTF_8);
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
            refuse(response);
            return;
        }
        String system = SYSTEM.matcher(request).getVariables().getOrDefault("system", "unknown");
        try (Scope actor = CallContext.actingAs(CallContext.SYSTEM + ":" + system)) {
            chain.doFilter(request, response);
        }
    }

    private boolean accepts(String authorization) {
        if (token.length == 0 || isBlank(authorization) || !authorization.startsWith(BEARER)) {
            return false;
        }
        return isEqual(token, CS.removeStart(authorization, BEARER).trim().getBytes(UTF_8));
    }

    private static void refuse(HttpServletResponse response) throws IOException {
        response.setStatus(UNAUTHORIZED.value());
        response.setHeader(WWW_AUTHENTICATE, "Bearer");
        response.setContentType(APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(UTF_8.name());
        response.getWriter().write(PROBLEM);
    }
}
