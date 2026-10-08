package com.bbh.itss.dso.portal.gui.support

import groovy.json.JsonSlurper
import groovy.transform.TupleConstructor

import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.bbh.itss.dso.portal.gui.support.ApiData.productDetails
import static com.bbh.itss.dso.portal.gui.support.StubResponse.json
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.bbh.itss.dso.portal.gui.support.StubResponse.yaml

class StubApi {

    static final String FIXTURES = '/com/bbh/itss/dso/portal/gui/api/'

    static final String SIGNED_IN_USER = 'Mateusz Matan'

    private final List<Route> routes = new CopyOnWriteArrayList<>()
    private final List<RecordedRequest> recorded = new CopyOnWriteArrayList<>()
    ChangeStubs.ProTech protech

    static Object fixture(String name) {
        new JsonSlurper().parseText(fixtureText(name))
    }

    static String fixtureText(String name) {
        def resource = StubApi.getResource(FIXTURES + name)
        if (!resource) {
            throw new IllegalArgumentException("No API fixture $name")
        }
        resource.getText('UTF-8')
    }

    private void loadDemoData() {
        get('/api/me') { [name: SIGNED_IN_USER] }
        get('/api/products') { RecordedRequest request ->
            def search = request.params().search?.toLowerCase()
            def products = fixture('products.json') as List<Map>
            json(search ? products.findAll {
                [it.code, it.name, it.ownerTeam, it.departmentName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
            } : products)
        }
        def departments = new CopyOnWriteArrayList<Map>(fixture('departments.json') as List<Map>)
        get('/api/departments') { departments.sort(false) { (it.name as String).toLowerCase() } }
        on('POST', '/api/departments') { RecordedRequest request ->
            def added = [id: (departments*.id.max() as int) + 1, name: request.json().name, version: 0, productCount: 0,
                         serviceCount: 0, pipelineCount: 0, activePipelineCount: 0, changeCount: 0]
            departments << added
            json(added, 201)
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
            json([code: request.params().name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')])
        }
        get('/api/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/details') { RecordedRequest request, List<String> ids -> detailsOr404(ids[0]) }
        get('/api/products/(\\d+)/pipelines') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-pipelines.json", "Product ${ids[0]} does not exist") }
        get('/api/products/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("product-${ids[0]}-config.yaml", "Product ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/pipelines/(\\d+)/config') { RecordedRequest request, List<String> ids -> fixtureOr404("pipeline-${ids[0]}-config.yaml", "Pipeline ${ids[0]} does not exist") }
        get('/api/settings') { json(fixture('settings.json')) }
        get('/api/settings/config') { yaml(fixtureText('settings-config.yaml')) }
        get('/api/monitoring/status') { json(fixture('monitoring-status.json')) }
        get('/api/monitoring/products') { json(fixture('monitoring-products.json')) }
        get('/api/monitoring/activity') { json(fixture('monitoring-activity.json')) }
        get('/api/monitoring/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        get('/api/monitoring/pipelines/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("monitoring-pipeline-${ids[0]}.json", "Pipeline ${ids[0]} does not exist") }
        get('/api/evidence/products/(\\d+)') { RecordedRequest request, List<String> ids -> fixtureOr404("evidence-product-${ids[0]}.json", "Product ${ids[0]} does not exist") }
        protech = ChangeStubs.install(this)
    }

    StubApi get(String path, Closure handler) {
        on('GET', path, handler)
    }

    StubApi on(String method, String path, Closure handler) {
        routes.add(0, new Route(method, Pattern.compile(path), handler))
        this
    }

    StubApi respond(String method, String path, Object body, int status = 200) {
        on(method, path) { json(body, status) }
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
                return result instanceof StubResponse ? result : json(result)
            }
        }
        problem(404, 'Not found', "No stub for $method $path")
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

    private static StubResponse detailsOr404(String id) {
        def name = "product-${id}.json"
        StubApi.getResource(FIXTURES + name) ? json(productDetails(fixture(name) as Map))
                : problem(404, 'Not found', "Product $id does not exist")
    }

    private static StubResponse fixtureOr404(String name, String detail) {
        if (!StubApi.getResource(FIXTURES + name)) {
            return problem(404, 'Not found', detail)
        }
        name.endsWith('.yaml') ? yaml(fixtureText(name)) : json(fixture(name))
    }

    @TupleConstructor(defaults = false)
    private static final class Route {

        final String method
        final Pattern path
        final Closure handler

        Matcher match(String requestMethod, String requestPath) {
            if (requestMethod != method) {
                return null
            }
            def matcher = path.matcher(requestPath)
            matcher.matches() ? matcher : null
        }
    }
}
