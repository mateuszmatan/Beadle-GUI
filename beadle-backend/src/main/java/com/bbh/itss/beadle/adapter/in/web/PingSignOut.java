package com.bbh.itss.beadle.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.util.UrlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Ends the session at Ping too when a user signs out (OpenID Connect RP-initiated logout), so that Ping does not sign
 * the browser straight back in. The Angular app posts {@code /logout} by XHR and cannot follow a redirect to Ping, so
 * the address where Ping ends its session is answered as {@code 200 {"redirectUrl": "..."}} and the app goes there.
 * The address is the {@code end_session_endpoint} of Ping's OpenID configuration with {@code id_token_hint} and
 * {@code post_logout_redirect_uri} ({@value #POST_LOGOUT_REDIRECT_URI}, registered at Ping), or, from a
 * PingFederate that advertises only {@value #PING_END_SESSION}, that one with {@code TargetResource}. Without either
 * the answer is {@code /}: Beadle's session has ended, Ping's has not.
 */
public final class PingSignOut extends OidcClientInitiatedLogoutSuccessHandler {

    static final String POST_LOGOUT_REDIRECT_URI = "{baseUrl}/";
    static final String PING_END_SESSION = "ping_end_session_endpoint";
    static final String REDIRECT_URL = "redirectUrl";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ClientRegistrationRepository registrations;

    public PingSignOut(ClientRegistrationRepository registrations) {
        super(registrations);
        this.registrations = registrations;
        setPostLogoutRedirectUri(POST_LOGOUT_REDIRECT_URI);
        setRedirectStrategy(PingSignOut::answer);
    }

    @Override
    protected String determineTargetUrl(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) {
        String target = super.determineTargetUrl(request, response, authentication);
        return getDefaultTargetUrl().equals(target) ? pingEndSession(request, authentication).orElse(target)
                : target;
    }

    private Optional<String> pingEndSession(HttpServletRequest request, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            return Optional.empty();
        }
        ClientRegistration registration = registrations.findByRegistrationId(
                token.getAuthorizedClientRegistrationId());
        Object endpoint = registration == null ? null
                : registration.getProviderDetails().getConfigurationMetadata().get(PING_END_SESSION);
        return endpoint == null ? Optional.empty()
                : Optional.of(UriComponentsBuilder.fromUriString(endpoint.toString())
                .queryParam("TargetResource", baseUrl(request) + "/").encode(UTF_8).build().toUriString());
    }

    private static String baseUrl(HttpServletRequest request) {
        return UriComponentsBuilder.fromUriString(UrlUtils.buildFullRequestUrl(request))
                .replacePath(request.getContextPath()).replaceQuery(null).fragment(null).build().toUriString();
    }

    private static void answer(HttpServletRequest request, HttpServletResponse response, String url)
            throws IOException {
        response.setStatus(SC_OK);
        response.setContentType(APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(UTF_8);
        response.getWriter().write(JSON.writeValueAsString(Map.of(REDIRECT_URL, url)));
    }
}
