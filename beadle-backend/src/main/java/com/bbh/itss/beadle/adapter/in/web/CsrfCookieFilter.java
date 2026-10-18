package com.bbh.itss.beadle.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Loads the CSRF token on every request, so the {@code XSRF-TOKEN} cookie the Angular app copies into
 * {@code X-XSRF-TOKEN} is there before its first change; Spring Security would otherwise only write it on demand.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
