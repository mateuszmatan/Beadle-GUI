package com.bbh.itss.beadle.adapter.out.user

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.application.user.port.out.SignedInUserPort.UNKNOWN

class AuthenticatedUserAdapterSpec extends Specification {

    static final List AUTHORITIES = AuthorityUtils.createAuthorityList('OIDC_USER')

    def adapter = new AuthenticatedUserAdapter()

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "a Ping user is named by the name claim, else preferred_username, else sub, with the e-mail claim"() {
        given:
        signedIn(ping(claims))

        expect:
        adapter.name() == name
        adapter.email() == Optional.ofNullable(email)

        where:
        claims                                                                                   || name            | email
        [sub: 'u-7', name: 'Ann Lee', preferred_username: 'alee', email: 'ann.lee@bbh.com']      || 'Ann Lee'       | 'ann.lee@bbh.com'
        [sub: 'u-7', name: '  ', preferred_username: 'alee']                                     || 'alee'          | null
        [sub: 'u-7']                                                                             || 'u-7'           | null
        [sub: 'u-7', name: ' Ann Lee ', email: ' ']                                              || 'Ann Lee'       | null
    }

    def "a user of another OAuth2 provider is named from its attributes the same way"() {
        given:
        def user = new DefaultOAuth2User(AUTHORITIES, [login: 'alee', preferred_username: 'Ann'], 'login')
        signedIn(new OAuth2AuthenticationToken(user, AUTHORITIES, 'ping'))

        expect:
        adapter.name() == 'Ann'
        adapter.email() == Optional.empty()
    }

    def "in the local mode the user is the name everybody is signed in as, without an e-mail address"() {
        given:
        signedIn(new PreAuthenticatedAuthenticationToken('Mateusz Matan', '', AuthorityUtils.createAuthorityList('ROLE_USER')))

        expect:
        adapter.name() == 'Mateusz Matan'
        adapter.email() == Optional.empty()
    }

    def "nobody signed in, an anonymous visitor or an unauthenticated token is unknown"() {
        given:
        if (authentication) {
            signedIn(authentication)
        }

        expect:
        adapter.name() == UNKNOWN
        adapter.email() == Optional.empty()

        where:
        authentication << [null,
                           new AnonymousAuthenticationToken('key', 'anonymousUser', AuthorityUtils.createAuthorityList('ROLE_ANONYMOUS')),
                           new TestingAuthenticationToken('Ann Lee', '')]
    }

    def "an authenticated principal without claims or name is unknown"() {
        given:
        def authentication = new TestingAuthenticationToken(' ', '', 'ROLE_USER')

        when:
        signedIn(authentication)

        then:
        authentication.authenticated
        adapter.name() == UNKNOWN
    }

    private static OAuth2AuthenticationToken ping(Map<String, Object> claims) {
        def token = new OidcIdToken('id-token', Instant.now(), Instant.now().plusSeconds(60), claims)
        new OAuth2AuthenticationToken(new DefaultOidcUser(AUTHORITIES, token), AUTHORITIES, 'ping')
    }

    private static void signedIn(Authentication authentication) {
        def context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.context = context
    }
}
