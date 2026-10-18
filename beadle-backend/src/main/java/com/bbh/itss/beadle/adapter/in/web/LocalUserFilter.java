package com.bbh.itss.beadle.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Signs every request in as {@code beadle.auth.user}: the auth mode {@code local}, where there is no Ping. */
@RequiredArgsConstructor
public class LocalUserFilter extends OncePerRequestFilter {

    static final String ROLE = "ROLE_USER";

    private final String name;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = holder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken) {
            SecurityContext context = holder.createEmptyContext();
            context.setAuthentication(
                    new PreAuthenticatedAuthenticationToken(name, "", AuthorityUtils.createAuthorityList(ROLE)));
            holder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
