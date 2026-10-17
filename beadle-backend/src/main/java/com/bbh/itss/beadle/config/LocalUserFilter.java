package com.bbh.itss.beadle.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
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

/** Signs every request in as {@code beadle.signed-in-user}: the auth mode {@code local}, where there is no Ping. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class LocalUserFilter extends OncePerRequestFilter {

    static final String[] ROLES = {
            "ROLE_ADMIN",
            "ROLE_USER",
            "ROLE_DEPT_CORPORATE_TECHNOLOGY",
            "ROLE_DEPT_CUSTODY",
            "ROLE_DEPT_FUND_SERVICES",
            "ROLE_DEPT_MARKETS",
            "ROLE_DEPT_INVESTOR_SERVICES"
    };

    private final String name;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = holder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken) {
            SecurityContext context = holder.createEmptyContext();
            context.setAuthentication(
                    new PreAuthenticatedAuthenticationToken(name, "", AuthorityUtils.createAuthorityList(ROLES)));
            holder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
