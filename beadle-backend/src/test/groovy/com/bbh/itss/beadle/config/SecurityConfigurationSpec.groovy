package com.bbh.itss.beadle.config

import com.bbh.itss.beadle.BeadleApplication
import com.bbh.itss.beadle.support.RecordingHttpServer
import com.bbh.itss.beadle.support.StubServices
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.TypeExcludeFilter
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.type.classreading.MetadataReader
import org.springframework.core.type.classreading.MetadataReaderFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.web.context.WebApplicationContext
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.support.Json.parse
import static com.bbh.itss.beadle.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.http.MediaType.TEXT_HTML
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup

class SecurityConfigurationSpec extends Specification {

    static final String TOKEN = 'security-spec-service-token'
    static final String PING = 'https://ping.example.test'
    static final List<String> PING_REGISTRATION = [
            '--spring.security.oauth2.client.registration.ping.client-id=beadle-gui',
            '--spring.security.oauth2.client.registration.ping.client-secret=not-a-secret',
            '--spring.security.oauth2.client.registration.ping.authorization-grant-type=authorization_code',
            '--spring.security.oauth2.client.registration.ping.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}',
            '--spring.security.oauth2.client.registration.ping.scope=openid,profile,email',
            "--spring.security.oauth2.client.provider.ping.authorization-uri=$PING/as/authorization.oauth2",
            "--spring.security.oauth2.client.provider.ping.token-uri=$PING/as/token.oauth2",
            "--spring.security.oauth2.client.provider.ping.jwk-set-uri=$PING/pf/JWKS"]*.toString()

    /** Ping's OpenID configuration, read when Beadle starts, with the address where Ping ends a session. */
    @Shared
    RecordingHttpServer ping = new RecordingHttpServer()

    @Shared
    ConfigurableApplicationContext oidcContext = start('oidc', ['--beadle.auth.mode=oidc'] + PING_REGISTRATION +
            discoveredAt(ping))

    @Shared
    ConfigurableApplicationContext localContext = start('local', [])

    @Shared
    MockMvc oidc = mvc(oidcContext)

    @Shared
    MockMvc local = mvc(localContext)

    def cleanupSpec() {
        oidcContext?.close()
        localContext?.close()
        ping?.close()
    }

    def "with Ping an API call without a session is answered 401 and not sent to the sign-in"() {
        when:
        def response = answer(oidc, get('/api/me').header('X-Requested-With', requestedWith))

        then:
        response.status == 401
        response.getHeader('Location') == null

        where:
        requestedWith << ['XMLHttpRequest', 'Angular']
    }

    def "with Ping the session of a signed-in user lets the API through and /api/me names the user"() {
        given:
        def session = signedIn([sub: 'u-7', name: 'Ann Lee', email: 'ann.lee@bbh.com'])

        when:
        def me = answer(oidc, get('/api/me').session(session))

        then:
        me.status == 200
        parse(me.contentAsString) == [name: 'Ann Lee', email: 'ann.lee@bbh.com']

        expect:
        parse(answer(oidc, get('/api/me').with(oidcLogin().idToken { it.subject('u-9') })).contentAsString) ==
                [name: 'u-9', email: null]
    }

    def "with Ping the pages of the app and the sign-in are open, and the sign-in goes to Ping with the scopes"() {
        expect:
        answer(oidc, get('/changes/4')).forwardedUrl == '/index.html'
        answer(oidc, get('/action-history')).forwardedUrl == '/index.html'

        when:
        def signIn = answer(oidc, get(SecurityConfiguration.SIGN_IN))
        def location = URI.create(signIn.getHeader('Location'))
        def query = location.query.split('&').collectEntries { it.split('=', 2) as List }

        then:
        signIn.status == 302
        "$location.scheme://$location.host$location.path" == "$PING/as/authorization.oauth2"
        query.subMap(['response_type', 'client_id', 'scope', 'redirect_uri']) ==
                [response_type: 'code', client_id: 'beadle-gui', scope: 'openid profile email',
                 redirect_uri : 'http://localhost/login/oauth2/code/ping']

        when:
        def page = answer(oidc, get('/actuator/info').accept(TEXT_HTML))

        then:
        page.status == 302
        page.getHeader('Location') == SecurityConfiguration.SIGN_IN
        answer(oidc, get('/actuator/info')).status == 401
    }

    def "with Ping a change needs the token of the XSRF-TOKEN cookie, which Angular reads, in X-XSRF-TOKEN"() {
        given:
        def session = signedIn([sub: 'u-7', name: 'Ann Lee'])
        def cookie = answer(oidc, get('/api/departments').session(session)).getCookie('XSRF-TOKEN')
        def department = { "Security ${System.nanoTime()}".toString() }

        expect:
        cookie.value
        !cookie.httpOnly
        cookie.path == '/'

        when:
        def without = answer(oidc, json(post('/api/departments'), [name: department()]).session(session))
        def withToken = answer(oidc, json(post('/api/departments'), [name: department()]).session(session)
                .cookie(cookie).header('X-XSRF-TOKEN', cookie.value))

        then:
        without.status == 403
        withToken.status == 201
    }

    def "with Ping POST /logout ends the session and answers where the browser ends its session at Ping too"() {
        given:
        def session = signedIn([sub: 'u-7', name: 'Ann Lee'])
        def cookie = answer(oidc, get('/api/me').session(session)).getCookie('XSRF-TOKEN')

        when:
        def signedOut = answer(oidc, post('/logout').session(session).cookie(cookie).header('X-XSRF-TOKEN', cookie.value))
        def target = URI.create(parse(signedOut.contentAsString).redirectUrl as String)

        then:
        signedOut.status == 200
        signedOut.contentType.startsWith('application/json')
        session.invalid
        "$target.scheme://$target.host$target.path" == "$PING/idp/init_logout.openid"
        target.query.split('&').collectEntries { it.split('=', 2) as List } ==
                [id_token_hint: 'id-token', post_logout_redirect_uri: 'http://localhost/']
        answer(oidc, get('/api/me').session(session)).status == 401
    }

    def "the first API call of a session records the sign-in once, in both modes"() {
        given:
        def jdbc = context.getBean(JdbcTemplate)
        def count = { jdbc.queryForObject("SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY WHERE ACTION = 'USER_SIGNED_IN' AND USER_NAME = ?", Integer, user) }
        int before = count()

        when:
        [get('/api/me'), get('/api/departments'), get('/api/me')].each { assert answer(mvc, it.session(session)).status == 200 }

        then:
        count() == before + 1

        where:
        context      | mvc   | session                                         | user
        oidcContext  | oidc  | signedIn([sub: 'u-8', name: 'Ben Ode'])         | 'Ben Ode'
        localContext | local | new MockHttpSession()                           | 'Mateusz Matan'
    }

    def "locally everybody is beadle.signed-in-user without a session or a CSRF token, and can sign out"() {
        when:
        def me = answer(local, get('/api/me'))
        def added = answer(local, json(post('/api/departments'), [name: "Local ${System.nanoTime()}".toString()]))

        then:
        me.status == 200
        parse(me.contentAsString) == [name: 'Mateusz Matan', email: null]
        me.getCookie('XSRF-TOKEN') == null
        added.status == 201
        answer(local, get('/changes')).forwardedUrl == '/index.html'
        answer(local, post('/logout')).status == 204
    }

    def "in both modes a callback needs the service token, keeps no session, and the health probes are open"() {
        when:
        def refused = answer(mvc, json(post('/api/callbacks/protech'), [number: 'CHG7777777', event: 'TASK_CHANGED']))
        def taken = mvc.perform(json(post('/api/callbacks/protech'), [number: 'CHG7777777', event: 'TASK_CHANGED'])
                .header('Authorization', "Bearer $TOKEN")).andReturn()
        def health = mvc.perform(get(path)).andReturn()

        then:
        refused.status == 401
        parse(refused.contentAsString).detail == ServiceTokenFilter.DETAIL
        taken.response.status == 202
        taken.request.getSession(false) == null
        health.response.status == 200
        health.request.getSession(false) == null

        where:
        mvc   | path
        oidc  | '/actuator/health'
        local | '/actuator/health/liveness'
    }

    def "Ping without its registration stops Beadle at the start with what to set"() {
        when:
        start('no-ping', ['--beadle.auth.mode=oidc']).close()

        then:
        def e = thrown(Exception)
        causes(e).any { it.message == SecurityConfiguration.NO_PING }
    }

    private static List<String> discoveredAt(RecordingHttpServer server) {
        server.answer('GET', '/.well-known/openid-configuration', 200, [
                issuer                               : server.url,
                authorization_endpoint               : "$PING/as/authorization.oauth2".toString(),
                token_endpoint                       : "$PING/as/token.oauth2".toString(),
                jwks_uri                             : "$PING/pf/JWKS".toString(),
                end_session_endpoint                 : "$PING/idp/init_logout.openid".toString(),
                response_types_supported             : ['code'],
                subject_types_supported              : ['public'],
                id_token_signing_alg_values_supported: ['RS256'],
                scopes_supported                     : ['openid', 'profile', 'email']])
        ["--spring.security.oauth2.client.provider.ping.issuer-uri=$server.url".toString()]
    }

    private static ConfigurableApplicationContext start(String name, List<String> arguments) {
        new SpringApplicationBuilder(BeadleApplication).profiles('local', StubServices.PROFILE)
                .logStartupInfo(false)
                .initializers({ ConfigurableApplicationContext context ->
                    context.beanFactory.registerSingleton('specClasses', new SpecClasses())
                } as ApplicationContextInitializer<ConfigurableApplicationContext>)
                .run(([
                        '--server.port=0', '--beadle.demo-data=false', "--beadle.services.token=$TOKEN",
                        '--management.endpoint.health.probes.enabled=true',
                        "--DB_URL=jdbc:h2:mem:security-$name;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                ]*.toString() + arguments) as String[])
    }

    private static MockMvc mvc(ConfigurableApplicationContext context) {
        webAppContextSetup(context as WebApplicationContext)
                .addFilters(context.getBean(CorrelationIdFilter))
                .apply(springSecurity())
                .build()
    }

    private static MockHttpServletResponse answer(MockMvc mvc, MockHttpServletRequestBuilder request) {
        mvc.perform(request).andReturn().response
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map body) {
        request.contentType(APPLICATION_JSON).content(toJson(body))
    }

    private static MockHttpSession signedIn(Map<String, Object> claims) {
        def authorities = AuthorityUtils.createAuthorityList('OIDC_USER')
        def token = new OidcIdToken('id-token', Instant.now(), Instant.now().plusSeconds(600), claims)
        def session = new MockHttpSession()
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(
                new OAuth2AuthenticationToken(new DefaultOidcUser(authorities, token), authorities, 'ping')))
        session
    }

    /** Keeps the configurations nested in other specifications out of the component scan of the application. */
    static class SpecClasses extends TypeExcludeFilter {

        @Override
        boolean match(MetadataReader reader, MetadataReaderFactory factory) {
            reader.classMetadata.className ==~ /.*(Spec|Specification)(\$.*)?/
        }
    }

    private static List<Throwable> causes(Throwable e) {
        def all = []
        for (def cause = e; cause != null && !(cause in all); cause = cause.cause) {
            all << cause
        }
        all
    }
}
