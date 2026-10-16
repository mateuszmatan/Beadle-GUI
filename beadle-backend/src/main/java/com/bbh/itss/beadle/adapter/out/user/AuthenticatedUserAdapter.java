package com.bbh.itss.beadle.adapter.out.user;

import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToNull;

/**
 * The signed-in user is the authenticated principal of the request: with Ping its {@code name} claim, else its
 * {@code preferred_username}, else its {@code sub}, and its {@code email}; in the auth mode {@code local} the name
 * everybody is signed in as.
 */
@Component
public class AuthenticatedUserAdapter implements SignedInUserPort {

    static final List<String> NAME_CLAIMS = List.of("name", "preferred_username", "sub");
    static final String EMAIL_CLAIM = "email";

    @Override
    public String name() {
        return authentication().map(AuthenticatedUserAdapter::nameOf).orElse(UNKNOWN);
    }

    @Override
    public Optional<String> email() {
        return authentication().map(AuthenticatedUserAdapter::claimsOf).map(claims -> textOf(claims.get(EMAIL_CLAIM)));
    }

    private static Optional<Authentication> authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken ? Optional.empty() : Optional.of(authentication);
    }

    private static String nameOf(Authentication authentication) {
        Map<String, Object> claims = claimsOf(authentication);
        return NAME_CLAIMS.stream().map(claim -> textOf(claims.get(claim))).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> isNotBlank(authentication.getName()) ? authentication.getName().trim() : UNKNOWN);
    }

    private static Map<String, Object> claimsOf(Authentication authentication) {
        return authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal
                && principal.getAttributes() != null ? principal.getAttributes() : Map.of();
    }

    private static String textOf(Object value) {
        return value == null ? null : trimToNull(value.toString());
    }
}
