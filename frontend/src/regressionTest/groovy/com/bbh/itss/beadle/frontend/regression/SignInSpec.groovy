package com.bbh.itss.beadle.frontend.regression

import com.bbh.itss.beadle.frontend.support.BeadleSpecification
import com.bbh.itss.beadle.frontend.support.BeadleStubApi
import com.microsoft.playwright.options.Cookie

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class SignInSpec extends BeadleSpecification {

    static final String SIGN_IN = '/oauth2/authorization/ping'
    static final String RETURN_KEY = 'beadle.signIn.returnTo'

    def "the header names the signed-in user next to the sections, with a Sign out control"() {
        when:
        open('/changes')

        then:
        assertThat(page.locator('header .account .user-name')).hasText(BeadleStubApi.SIGNED_IN_USER)
        assertThat(buttonIn(page.locator('header .account'), 'Sign out')).isEnabled()
        assertThat(menuLinks()).hasText(MENU as String[])
        ownErrors().isEmpty()
    }

    def "signing out ends the session with the CSRF token of the cookie and starts again from the first page"() {
        given: 'Beadle has set the CSRF cookie of the session, as it does when signing in is oidc'
        context.addCookies([new Cookie('XSRF-TOKEN', 'browser-xsrf-token').setUrl(baseUrl())])
        open('/action-history')

        when:
        buttonIn(page.locator('header .account'), 'Sign out').click()

        then: 'the browser posted the token, came back to / without a session and Ping signed it in again'
        awaitRequest('POST', '/logout').header('X-XSRF-TOKEN') == 'browser-xsrf-token'
        awaitRequest('GET', SIGN_IN)
        page.waitForURL('**/changes')
        assertThat(page.locator('h1')).hasText('ProTech Changes')
        assertThat(page.locator('header .account .user-name')).hasText(BeadleStubApi.SIGNED_IN_USER)

        and: 'in that order'
        def calls = api.requests().collect { "$it.method $it.path".toString() }
        def signedOut = calls.indexOf('POST /logout')
        def signedIn = calls.indexOf("GET $SIGN_IN".toString())
        signedOut >= 0 && signedIn > signedOut
        calls.subList(signedOut, signedIn).contains('GET /api/me')
        calls.subList(signedIn, calls.size()).contains('GET /api/me')
        api.requests('POST', '/logout').size() == 1
    }

    def "an API call that finds the session ended sends the browser to sign in and back to the page it was on"() {
        given:
        open('/admin/departments')
        assertThat(gridRows().first()).isVisible()

        when: 'the session ends at Beadle and the next page asks the API'
        beadle.signedIn = false
        tab('Products').click()

        then: 'Ping signs the browser in again and the app comes back to the products'
        awaitRequest('GET', SIGN_IN)
        page.waitForURL('**/admin/products')
        assertThat(page.locator('h1')).hasText('Beadle Admin')
        assertThat(gridRows().first()).isVisible()
        api.requests('GET', '/api/products').size() >= 2
        page.evaluate("() => sessionStorage.getItem('$RETURN_KEY')") == null
        page.url().startsWith(baseUrl())
    }

    def "a page opened without a session goes to sign in and comes back to that page"() {
        given:
        beadle.signedIn = false

        when:
        page.navigate(url('/action-history'))

        then:
        awaitRequest('GET', SIGN_IN)
        page.waitForURL('**/action-history')
        assertThat(page.locator('h1')).hasText('Action History')
        assertThat(gridRows().first()).isVisible()
        api.requests('GET', SIGN_IN).size() == 1
    }
}
