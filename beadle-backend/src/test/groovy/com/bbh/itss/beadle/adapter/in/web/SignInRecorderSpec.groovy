package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.application.user.port.in.SignedInUser
import com.bbh.itss.beadle.application.user.port.in.SignedInUserUseCase
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

class SignInRecorderSpec extends Specification {

    SignedInUserUseCase users = Mock()
    FilterChain chain = Mock()
    def recorder = new SignInRecorder(users)

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "the first API call of a session records the sign-in and the next ones do not"() {
        given:
        signedIn(new TestingAuthenticationToken('Ann Lee', '', 'ROLE_USER'))
        def session = new MockHttpSession()

        when:
        call('/api/me', session)
        call('/api/products', session)

        then:
        1 * users.signIn() >> new SignedInUser('Ann Lee')
        2 * chain.doFilter(_, _)

        when:
        call('/api/me', new MockHttpSession())

        then:
        1 * users.signIn() >> new SignedInUser('Ann Lee')
        1 * chain.doFilter(_, _)
    }

    def "a page, an anonymous or unauthenticated call records nothing and creates no session"() {
        given:
        if (authentication) {
            signedIn(authentication)
        }
        def request = request(path)

        when:
        recorder.doFilter(request, new MockHttpServletResponse(), chain)

        then:
        0 * users._
        1 * chain.doFilter(_, _)
        request.getSession(false) == null

        where:
        path           | authentication
        '/changes'     | new TestingAuthenticationToken('Ann Lee', '', 'ROLE_USER')
        '/api/me'      | null
        '/api/me'      | new AnonymousAuthenticationToken('key', 'anonymousUser', AuthorityUtils.createAuthorityList('ROLE_ANONYMOUS'))
        '/api/me'      | new TestingAuthenticationToken('Ann Lee', '')
    }

    def "a sign-in that cannot be recorded does not stop the call, and is not tried again in the session"() {
        given:
        signedIn(new TestingAuthenticationToken('Ann Lee', '', 'ROLE_USER'))
        def session = new MockHttpSession()

        when:
        call('/api/me', session)
        call('/api/me', session)

        then:
        1 * users.signIn() >> { throw new IllegalStateException('database down') }
        2 * chain.doFilter(_, _)
    }

    private void call(String path, MockHttpSession session) {
        def request = request(path)
        request.session = session
        recorder.doFilter(request, new MockHttpServletResponse(), chain)
    }

    private static MockHttpServletRequest request(String path) {
        new MockHttpServletRequest('GET', path)
    }

    private static void signedIn(Authentication authentication) {
        def context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.context = context
    }
}
