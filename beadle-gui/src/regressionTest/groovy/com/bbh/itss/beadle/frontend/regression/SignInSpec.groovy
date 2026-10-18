package com.bbh.itss.beadle.frontend.regression

import com.bbh.itss.beadle.frontend.support.BeadleSpecification
import com.bbh.itss.beadle.frontend.support.BeadleStubApi
import com.microsoft.playwright.Page
import com.microsoft.playwright.options.Cookie

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class SignInSpec extends BeadleSpecification {

    static final String SIGN_IN = '/oauth2/authorization/ping'

    def "signing out ends the session at Beadle and at Ping, so Ping asks for the sign-in again"() {
        given: 'Beadle has set the CSRF cookie of the session, as it does when signing in is oidc'
        context.addCookies([new Cookie('XSRF-TOKEN', 'browser-xsrf-token').setUrl(baseUrl())])
        open('/action-history')

        when:
        buttonIn(page.locator('header .account'), 'Sign out').click()

        then: 'the browser posted the token, ended its Ping session and came back to / without a session'
        awaitRequest('POST', '/logout').header('X-XSRF-TOKEN') == 'browser-xsrf-token'
        awaitRequest('GET', BeadleStubApi.PING_END_SESSION).params().post_logout_redirect_uri == '/'
        awaitRequest('GET', SIGN_IN)

        and: 'Ping shows its sign-in instead of signing the browser straight back in'
        assertThat(page.locator('h1')).hasText('Sign on to Ping')
        !beadle.signedIn
        !beadle.pingSession

        and: 'in that order, with the session asked for in between'
        def calls = api.requests().collect { "$it.method $it.path".toString() }
        def signedOut = calls.indexOf('POST /logout')
        def endedAtPing = calls.indexOf("GET $BeadleStubApi.PING_END_SESSION".toString())
        def signIn = calls.indexOf("GET $SIGN_IN".toString())
        signedOut >= 0 && endedAtPing > signedOut && signIn > endedAtPing
        calls.subList(endedAtPing, signIn).contains('GET /api/me')
        api.requests('POST', '/logout').size() == 1

        when: 'the user signs on at Ping again'
        page.locator('a', new Page.LocatorOptions().setHasText('Sign On')).click()

        then:
        page.waitForURL('**/changes')
        assertThat(page.locator('header .account .user-name')).hasText(BeadleStubApi.SIGNED_IN_USER)
    }
}
