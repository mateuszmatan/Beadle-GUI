package com.bbh.itss.dso.portal.smoke

import com.bbh.itss.dso.portal.BeadleApplication
import com.bbh.itss.dso.portal.support.PortalClient
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

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
            started = new SpringApplicationBuilder(BeadleApplication)
                    .run('--server.port=0', '--BEADLE_DEMO_DATA=true',
                            '--DB_URL=jdbc:h2:mem:beadle-smoke;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1')
            baseUrl = "http://localhost:${started.environment.getProperty('local.server.port')}"
        }
        api = new PortalClient(baseUrl)
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
                ['jiraConnected', 'serviceNowConnected', 'cyberTrackConnected'] as Set
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
        tasks.every { it.number ==~ /CTASK\d{7}/ && it.details.assignmentGroup && it.approval }
        tasks*.approval.toSet().every { it in ['Not Yet Requested', 'Requested', 'Approved'] }
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

    def "the DevSecOps Management portal is not part of this application"() {
        expect:
        ['/api/settings', '/api/pipelines/1', '/api/monitoring/status', '/api/dso/config/1'].every {
            api.get(it).status == 404
        }
    }

    @Requires({ BeadleSmokeSpec.uiExpected() })
    def "the web UI is served, also for links into the app"() {
        expect:
        ['/', '/changes', '/new-change', '/admin/products', '/admin/departments'].every { path ->
            def page = api.get(path)
            assert page.status == 200: "$path: $page.status"
            assert page.header('Content-Type').startsWith('text/html')
            assert page.body.contains('<beadle-root')
            true
        }
    }

    static boolean uiExpected() {
        System.getProperty('smoke.ui') != 'false' &&
                (System.getProperty('smoke.baseUrl') || BeadleSmokeSpec.getResource('/static/index.html'))
    }
}
