package com.bbh.itss.beadle.smoke

import com.bbh.itss.beadle.BeadleApplication
import com.bbh.itss.beadle.support.BeadleClient
import com.bbh.itss.beadle.support.StandInServices
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.CookieManager

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

/**
 * Checks a running Beadle: the one given by {@code -Dsmoke.baseUrl} (with the session cookies of
 * {@code -Dsmoke.cookie}), or else the jar's application started here on the demo catalogue and the stand-ins of
 * Beadle-Internal and Beadle-ProTech.
 */
class BeadleSmokeSpec extends Specification {

    static final List<String> LOOKUPS = ['users', 'departments', 'assignment-groups', 'releases',
                                         'configuration-items', 'incidents', 'problems', 'clients']

    @Shared
    ConfigurableApplicationContext started

    @Shared
    BeadleClient api

    def setupSpec() {
        String baseUrl = System.getProperty('smoke.baseUrl')
        if (!baseUrl) {
            String services = StandInServices.SHARED.url
            started = new SpringApplicationBuilder(BeadleApplication).profiles('local')
                    .run('--server.port=0', '--beadle.demo-data=true', "--beadle.services.internal.url=$services",
                            "--beadle.services.protech.url=$services",
                            '--DB_URL=jdbc:h2:mem:beadle-smoke;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1')
            baseUrl = "http://localhost:${started.environment.getProperty('local.server.port')}"
        }
        api = BeadleClient.withCookies(baseUrl, System.getProperty('smoke.cookie'))
    }

    def cleanupSpec() {
        started?.close()
    }

    def "Beadle is healthy, names the signed-in user and refuses a callback without the service token"() {
        when:
        def health = new BeadleClient(api.baseUrl, new CookieManager()).get('/actuator/health')
        def me = api.get('/api/me')

        then:
        health.status == 200
        health.json.status == 'UP'
        me.status == 200
        me.json.keySet() == ['name', 'email'] as Set
        !started || me.json == [name: 'Mateusz Matan', email: null]
        api.post('/api/callbacks/protech', [number: 'CHG0000001', event: 'STATE_CHANGED']).status == 401
    }

    def "the products, the departments and the changes answer"() {
        when:
        def products = api.get('/api/products')
        def departments = api.get('/api/departments')
        def changes = api.get('/api/changes')

        then:
        products.status == 200
        !started || products.json.size() == 10
        departments.status == 200
        departments.json.every { it.containsKey('productCount') && it.containsKey('changeCount') }
        changes.status == 200
        changes.json.every { it.syncProblem == null }
        api.get('/api/changes/integrations').json.keySet() ==
                ['jiraConnected', 'proTechConnected', 'cyberTrackConnected', 'internalReachable',
                 'proTechReachable'] as Set
    }

    def "the wizard reads its options, a product's change template, its FixVersions and epics and one value per lookup"() {
        given:
        def product = api.get('/api/products').json[0]

        when:
        def profile = api.get("/api/products/$product.id/change-profile")
        def versions = api.get("/api/products/$product.id/jira/versions")
        def epics = versions.json.take(1).collect { version ->
            api.get("/api/products/$product.id/jira/epics?fixVersion=${encode(version.name as String, UTF_8)}")
        }
        def lookups = LOOKUPS.collect { api.get("/api/lookups/$it") }

        then:
        api.get('/api/changes/options').json.releaseManagement == 'Release Management'
        profile.status == 200
        !started || profile.json.version != null
        !profile.json.tasks.isEmpty()
        versions.status == 200
        epics*.status.every { it == 200 }
        lookups*.status.every { it == 200 }
        !started || lookups.every { !it.json.isEmpty() }
    }

    def "the action history answers its newest rows and the values of its filters"() {
        when:
        def newest = api.get('/api/action-history?size=5')

        then:
        newest.status == 200
        newest.json.keySet() == ['content', 'page', 'size', 'total'] as Set
        newest.json.subMap('page', 'size') == [page: 0, size: 5]
        newest.json.content.size() <= 5
        api.get('/api/action-history/users').status == 200
        api.get('/api/action-history/actions').status == 200
    }

    @Requires({ System.getProperty('smoke.baseUrl') && System.getProperty('smoke.ui') != 'false' })
    def "the jar serves the web UI, also for links into the app and unknown addresses, but not for unknown API calls"() {
        expect:
        ['/', '/changes/4', '/no-such-page'].every { path ->
            def page = api.get(path)
            assert page.status == 200: "$path: $page.status"
            assert page.header('Content-Type').startsWith('text/html')
            assert page.body.contains('<beadle-root')
            true
        }
        api.get('/api/no-such-endpoint').status == 404
    }
}
