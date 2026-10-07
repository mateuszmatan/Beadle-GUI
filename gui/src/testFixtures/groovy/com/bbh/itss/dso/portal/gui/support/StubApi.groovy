package com.bbh.itss.dso.portal.gui.support

import groovy.json.JsonSlurper

import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern

class StubApi {

    static final String FIXTURES = '/com/bbh/itss/dso/portal/gui/api/'

    private final List<Route> routes = new CopyOnWriteArrayList<>()
    private final List<RecordedRequest> recorded = new CopyOnWriteArrayList<>()

    static Object fixture(String name) {
        new JsonSlurper().parseText(fixtureText(name))
    }

    static String fixtureText(String name) {
        def resource = StubApi.getResource(FIXTURES + name)
        if (resource == null) {
            throw new IllegalArgumentException("No API fixture $name")
        }
        resource.getText('UTF-8')
    }

    private void loadDemoData() {
        get('/api/products') { RecordedRequest request ->
            def search = request.params().search?.toLowerCase()
            def products = fixture('products.json') as List<Map>
            StubResponse.json(search ? products.findAll {
                [it.code, it.name, it.ownerTeam, it.departmentName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
            } : products)
        }
        def departments = new CopyOnWriteArrayList<Map>(fixture('departments.json') as List<Map>)
        get('/api/departments') { departments.sort(false) { (it.name as String).toLowerCase() } }
        on('POST', '/api/departments') { RecordedRequest request ->
            def added = [id: (departments*.id.max() as int) + 1, name: request.json().name, version: 0, productCount: 0,
                         serviceCount: 0, pipelineCount: 0, activePipelineCount: 0]
            departments << added
            StubResponse.json(added, 201)
        }
        on('PUT', '/api/departments/(\\d+)') { RecordedRequest request, List<String> ids ->
            def department = departments.find { it.id == ids[0] as int }
            department.putAll(name: request.json().name, version: (department.version as int) + 1)
            department
        }
        on('DELETE', '/api/departments/(\\d+)') { RecordedRequest request, List<String> ids ->
            departments.removeIf { it.id == ids[0] as int }
            StubResponse.empty()
        }
        get('/api/products/code-suggestion') { RecordedRequest request ->
            StubResponse.json([code: request.params().name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')])
        }
        get('/api/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/pipelines') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-pipelines.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-config.yaml", "Product ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}-config.yaml", "Pipeline ${ids[0]} does not exist") }
        get('/api/settings') { StubResponse.json(fixture('settings.json')) }
        get('/api/settings/config') { StubResponse.yaml(fixtureText('settings-config.yaml')) }
        get('/api/monitoring/status') { StubResponse.json(fixture('monitoring-status.json')) }
        get('/api/monitoring/products') { StubResponse.json(fixture('monitoring-products.json')) }
        get('/api/monitoring/activity') { StubResponse.json(fixture('monitoring-activity.json')) }
        get('/api/monitoring/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/monitoring/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/evidence/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("evidence-product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        ChangeStubs.install(this)
    }

    StubApi get(String path, Closure handler) {
        on('GET', path, handler)
    }

    StubApi on(String method, String path, Closure handler) {
        routes.add(0, new Route(method, Pattern.compile(path), handler))
        this
    }

    StubApi respond(String method, String path, Object body, int status = 200) {
        on(method, path) { StubResponse.json(body, status) }
    }

    StubApi respond(String method, String path, StubResponse response) {
        on(method, path) { response }
    }

    StubResponse handle(String method, String path, String query, String body) {
        def request = new RecordedRequest(method, path, query, body)
        recorded << request
        for (route in routes) {
            def matcher = route.match(method, path)
            if (matcher != null) {
                def groups = matcher.groupCount() ? (1..matcher.groupCount()).collect { matcher.group(it) } : []
                def handler = route.handler
                def result = handler.maximumNumberOfParameters >= 2 ? handler.call(request, groups)
                        : handler.maximumNumberOfParameters == 1 ? handler.call(request) : handler.call()
                return result instanceof StubResponse ? result : StubResponse.json(result)
            }
        }
        StubResponse.problem(404, 'Not found', "No stub for $method $path")
    }

    List<RecordedRequest> requests() {
        List.copyOf(recorded)
    }

    List<RecordedRequest> requests(String method, String pathPattern) {
        def pattern = Pattern.compile(pathPattern)
        recorded.findAll { it.method == method && pattern.matcher(it.path).matches() }
    }

    RecordedRequest lastRequest(String method, String pathPattern) {
        def matching = requests(method, pathPattern)
        matching ? matching.last() : null
    }

    void reset() {
        routes.clear()
        recorded.clear()
        loadDemoData()
    }

    private static StubResponse fixtureOr404(String name, String detail) {
        if (!StubApi.getResource(FIXTURES + name)) {
            return StubResponse.problem(404, 'Not found', detail)
        }
        name.endsWith('.yaml') ? StubResponse.yaml(fixtureText(name)) : StubResponse.json(fixture(name))
    }

    private static final class Route {

        final String method
        final Pattern path
        final Closure handler

        Route(String method, Pattern path, Closure handler) {
            this.method = method
            this.path = path
            this.handler = handler
        }

        Matcher match(String requestMethod, String requestPath) {
            if (requestMethod != method) {
                return null
            }
            def matcher = path.matcher(requestPath)
            matcher.matches() ? matcher : null
        }
    }
}
