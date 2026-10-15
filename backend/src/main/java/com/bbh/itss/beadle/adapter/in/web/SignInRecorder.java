package com.bbh.itss.beadle.adapter.in.web;

import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

/**
 * Records {@code USER_SIGNED_IN} once per session: on the first API call a signed-in user makes in it. The security
 * filter chain of the users runs it after the authorization, so it only sees calls that were let through.
 */
@Slf4j
@RequiredArgsConstructor
public class SignInRecorder extends OncePerRequestFilter {

    static final String RECORDED = SignInRecorder.class.getName() + ".RECORDED";
    static final String API = "/api/";

    private final SignedInUserUseCase users;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + API);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (signedIn() && firstCallOf(request.getSession())) {
            try {
                users.signIn();
            } catch (RuntimeException e) {
                log.warn("The sign-in could not be recorded: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean signedIn() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static boolean firstCallOf(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(RECORDED) != null) {
                return false;
            }
            session.setAttribute(RECORDED, Boolean.TRUE);
            return true;
        }
    }
}
