package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.adapter.in.web.PingSignOut
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.support.Json.parse

class PingSignOutSpec extends Specification {

    static final String PING = 'https://ping.example.test'

    def "the browser is sent to #endpoint of Ping and comes back to the first page of Beadle"() {
        given:
        def handler = new PingSignOut(registrations((endpoint): "$PING/idp/$path".toString()))
        def request = new MockHttpServletRequest('POST', '/logout')
        request.scheme = 'https'
        request.serverName = 'beadle.apps.bbh.com'
        request.serverPort = 443
        def response = new MockHttpServletResponse()

        when:
        handler.onLogoutSuccess(request, response, signedIn())

        then:
        response.status == 200
        response.contentType.startsWith('application/json')
        parse(response.contentAsString) == [redirectUrl: "$PING/idp/$path?$query".toString()]

        where:
        endpoint                    | path                   | query
        'end_session_endpoint'      | 'init_logout.openid'   | 'id_token_hint=id-token&post_logout_redirect_uri=https://beadle.apps.bbh.com/'
        'ping_end_session_endpoint' | 'startSLO.ping'        | 'TargetResource=https://beadle.apps.bbh.com/'
    }

    def "without an address where Ping ends a session, or without a Ping sign-in, the browser starts again at /"() {
        given:
        def handler = new PingSignOut(registrations(metadata))
        def response = new MockHttpServletResponse()

        when:
        handler.onLogoutSuccess(new MockHttpServletRequest('POST', '/logout'), response, authentication)

        then:
        response.status == 200
        parse(response.contentAsString) == [redirectUrl: '/']

        where:
        metadata                                     | authentication
        [:]                                          | signedIn()
        [end_session_endpoint: "$PING/idp/x".toString()] | new TestingAuthenticationToken('Ann Lee', null)
        [end_session_endpoint: "$PING/idp/x".toString()] | null
    }

    private static InMemoryClientRegistrationRepository registrations(Map<String, Object> metadata) {
        new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId('ping')
                .clientId('beadle-gui')
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri('{baseUrl}/login/oauth2/code/{registrationId}')
                .scope('openid')
                .authorizationUri("$PING/as/authorization.oauth2")
                .tokenUri("$PING/as/token.oauth2")
                .jwkSetUri("$PING/pf/JWKS")
                .providerConfigurationMetadata(metadata)
                .build())
    }

    private static OAuth2AuthenticationToken signedIn() {
        def authorities = AuthorityUtils.createAuthorityList('OIDC_USER')
        def token = new OidcIdToken('id-token', Instant.now(), Instant.now().plusSeconds(600), [sub: 'u-7'])
        new OAuth2AuthenticationToken(new DefaultOidcUser(authorities, token), authorities, 'ping')
    }
}
