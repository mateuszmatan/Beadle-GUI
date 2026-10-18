package com.bbh.itss.beadle.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.servlet.mvc.ParameterizableViewController;

import java.util.List;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

/**
 * Serves the Angular index page for the addresses of the interface, so that a page opened directly or reloaded
 * starts the app on it: every address that is neither the API, the actuator, the sign-in nor a file (no extension in
 * its last segment), so that an unknown address reaches the wildcard route of the app, which falls back to Changes,
 * instead of a 404 of the service.
 */
@Configuration
public class SinglePageAppForwarding {

    static final String INDEX_PAGE = "forward:/index.html";
    /** Just before the static resources, which answer 404 for every address they do not hold. */
    static final int FALLBACK_ORDER = LOWEST_PRECEDENCE - 2;
    private static final List<String> SERVICE_PATHS = List.of("/api", "/actuator", "/error", "/oauth2", "/login",
            "/logout");

    @Bean
    HandlerMapping singlePageAppFallback() {
        return new IndexFallback();
    }

    /** Whether a request for this path is one the app answers: a GET of an address without a file extension. */
    static boolean isAppAddress(String method, String path) {
        if (!"GET".equals(method) && !"HEAD".equals(method) || path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        if (SERVICE_PATHS.stream().anyMatch(service -> path.equals(service) || path.startsWith(service + "/"))) {
            return false;
        }
        return !substringAfterLast(path, "/").contains(".");
    }

    private static final class IndexFallback extends AbstractHandlerMapping {

        private final ParameterizableViewController index = new ParameterizableViewController();

        IndexFallback() {
            index.setViewName(INDEX_PAGE);
            setOrder(FALLBACK_ORDER);
        }

        @Override
        protected Object getHandlerInternal(HttpServletRequest request) {
            return isAppAddress(request.getMethod(), initLookupPath(request)) ? index : null;
        }
    }
}
