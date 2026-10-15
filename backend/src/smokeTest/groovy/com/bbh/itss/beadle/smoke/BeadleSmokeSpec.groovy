package com.bbh.itss.beadle.smoke

import com.bbh.itss.beadle.BeadleApplication
import com.bbh.itss.beadle.support.PortalClient
import com.bbh.itss.beadle.support.StubServices
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import java.net.CookieManager

import static java.net.URLEncoder.encode
import static java.nio.charset.StandardCharsets.UTF_8

class BeadleSmokeSpec extends Specification {

    @Shared
    ConfigurableApplicationContext started

    @Shared
    PortalClient api

    def setupSpec() {
        String baseUrl = System.getProperty('smoke.baseUrl')
        if (!baseUrl) {
            started = new SpringApplicationBuilder(BeadleApplication).profiles('local', StubServices.PROFILE)
                    .run('--server.port=0', '--BEADLE_DEMO_DATA=true',
                            '--DB_URL=jdbc:h2:mem:beadle-smoke;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1')
            baseUrl = "http://localhost:${started.environment.getProperty('local.server.port')}"
        }
        api = PortalClient.withCookies(baseUrl, System.getProperty('smoke.cookie'))
    }

    def cleanupSpec() {
        started?.close()
    }

    def "the application reports itself healthy"() {
        when:
        def health = api.get('/actuator/health')

        then:
        health.status == 200
        health.json.status == 'UP'
    }

    def "the products and the departments answer with the changes raised for them"() {
        when:
        def products = api.get('/api/products')
        def departments = api.get('/api/departments')

        then:
        products.status == 200
        products.json instanceof List
        !started || products.json.size() == 10
        departments.status == 200
        departments.json.every { it.containsKey('productCount') && it.containsKey('changeCount') }
        !started || departments.json.sum { it.changeCount } >= 12
    }

    def "every product's ProTech change template and Jira FixVersions and epics can be read for a production change"() {
        given:
        def products = api.get('/api/products').json.take(5)

        expect:
        api.get('/api/changes').status == 200
        api.get('/api/changes/integrations').json.keySet() ==
                ['jiraConnected', 'proTechConnected', 'cyberTrackConnected', 'internalReachable',
                 'proTechReachable'] as Set
        api.get('/api/change-profiles').status == 200
        !started || api.get('/api/change-profiles').json.size() >= products.size()
        products.every { product ->
            def profile = api.get("/api/products/$product.id/change-profile")
            assert profile.status == 200: profile
            assert !started || profile.json.version != null
            assert !profile.json.tasks.isEmpty()
            assert profile.json.tasks.every { it.assignmentGroup && it.shortDescription && it.description }
            assert !started || profile.json.tasks.any { it.assignmentGroup.toLowerCase().contains('release management') }
            if (profile.json.version != null) {
                def versions = api.get("/api/products/$product.id/jira/versions")
                assert versions.status == 200: versions
                versions.json.take(1).each { version ->
                    def epics = api.get("/api/products/$product.id/jira/epics?fixVersion=${encode(version.name, UTF_8)}")
                    assert epics.status == 200: epics
                }
            }
            true
        }
    }

    def "the demo changes are stored, synced with ProTech, spread over its workflow and hold their change tasks"() {
        when:
        def changes = api.get('/api/changes')
        def department = changes.json.find { it.departmentId != null }?.departmentId
        def tasks = changes.json.collectMany { it.tasks }

        then:
        changes.status == 200
        changes.json.every { it.syncProblem == null }
        !started || changes.json.size() >= 12 && changes.json*.state.toSet().size() >= 6 &&
                changes.json*.update.findAll()*.status.toSet() == ['APPLIED', 'NOT_APPLIED'] as Set
        tasks.every { it.number ==~ /CTASK\d{7}/ && it.details.assignmentGroup && it.approval && it.approvers }
        tasks*.approval.toSet().every { it in ['NOT_APPROVED', 'REQUESTED', 'APPROVED'] }
        changes.json.every { it.approvals*.role == ['BUSINESS', 'L1', 'L2', 'SUPPORT'] }
        changes.json.findAll { it.state == 'IMPLEMENTATION' }.every { change ->
            def active = change.tasks.findAll { it.state != 'CANCELED' }
            active && active.every { it.approval == 'APPROVED' }
        }
        department == null || api.get("/api/changes?departmentId=$department").json.every {
            it.departmentId == department
        }
    }

    def "the wizard reads the signed-in user, its options and lookups that know the people of the demo templates"() {
        given:
        def products = api.get('/api/products').json

        expect:
        api.get('/api/me').json.name
        api.get('/api/changes/options').json.categories.contains('Application')
        api.get('/api/changes/options').json.releaseManagement == 'Release Management'
        ['users', 'departments', 'assignment-groups', 'releases', 'configuration-items', 'incidents', 'problems',
         'clients'].every { api.get("/api/lookups/$it").status == 200 }
        !started || products.every { product ->
            def template = api.get("/api/products/$product.id/change-profile").json.template
            def people = (template.approvers.values() + template.privilegedAccess.users*.user).findAll()
            def item = api.get("/api/lookups/configuration-items?q=${encode(template.configurationItem, UTF_8)}").json
            assert people.every { api.get("/api/lookups/users?q=${encode(it, UTF_8)}").json*.value.contains(it) }
            assert template.directBusinessService == item.find { it.value == template.configurationItem }?.detail
            assert template.risk in ['Low', 'Moderate', 'High']
            true
        }
    }

    def "the action history answers its newest rows and the values of its filters"() {
        when:
        def newest = api.get('/api/action-history?size=5')

        then:
        newest.status == 200
        newest.json.keySet() == ['content', 'page', 'size', 'total'] as Set
        newest.json.page == 0
        newest.json.size == 5
        newest.json.content.size() <= 5
        api.get('/api/action-history/users').status == 200
        api.get('/api/action-history/actions').status == 200
    }

    def "the API names the signed-in user, takes a callback only with the service token and leaves health open"() {
        when:
        def me = api.get('/api/me')

        then:
        me.status == 200
        me.json.keySet() == ['name', 'email'] as Set
        !started || me.json == [name: 'Mateusz Matan', email: null]
        new PortalClient(api.baseUrl, new CookieManager()).get('/actuator/health').status == 200
        api.post('/api/callbacks/protech', [number: 'CHG0000001', event: 'STATE_CHANGED']).status == 401
    }

    def "the DevSecOps Management portal is not part of this application"() {
        expect:
        ['/api/settings', '/api/pipelines/1', '/api/monitoring/status', '/api/dso/config/1'].every {
            api.get(it).status == 404
        }
    }

    @Requires({ BeadleSmokeSpec.uiExpected() })
    def "the web UI is served, also for links into the app and for unknown addresses, which the app sends to Changes"() {
        expect:
        ['/', '/changes', '/new-change', '/admin/products', '/admin/departments', '/action-history',
         '/no-such-page', '/no/such/page'].every { path ->
            def page = api.get(path)
            assert page.status == 200: "$path: $page.status"
            assert page.header('Content-Type').startsWith('text/html')
            assert page.body.contains('<beadle-root')
            true
        }
        ['/no-such-file.js', '/api/no-such-endpoint'].every { path -> api.get(path).status == 404 }
    }

    static boolean uiExpected() {
        System.getProperty('smoke.ui') != 'false' &&
                (System.getProperty('smoke.baseUrl') || BeadleSmokeSpec.getResource('/static/index.html'))
    }
}
