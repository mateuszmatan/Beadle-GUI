package com.bbh.itss.beadle.frontend.support

import groovy.json.JsonSlurperClassic
import groovy.transform.TupleConstructor

import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.bbh.itss.beadle.frontend.support.StubResponse.json
import static com.bbh.itss.beadle.frontend.support.StubResponse.problem
import static com.bbh.itss.beadle.frontend.support.StubResponse.yaml

abstract class StubApi {

    static final String FIXTURES = '/com/bbh/itss/beadle/frontend/api/'

    private final List<Route> routes = new CopyOnWriteArrayList<>()
    private final List<RecordedRequest> recorded = new CopyOnWriteArrayList<>()
    List<Map> businessServices

    /**
     * The parsed fixture as plain {@code LinkedHashMap}s and {@code ArrayList}s. The handlers share these maps across the
     * threads of {@link GuiServer}, and the lazy maps of the default {@code JsonSlurper} are not safe to read from two
     * threads at once: one thread can see a map that another is still filling in and answer a 500.
     */
    static Object fixture(String name) {
        new JsonSlurperClassic().parseText(fixtureText(name))
    }

    static String fixtureText(String name) {
        def resource = StubApi.getResource(FIXTURES + name)
        if (!resource) {
            throw new IllegalArgumentException("No API fixture $name")
        }
        resource.getText('UTF-8')
    }

    static boolean hasFixture(String name) {
        StubApi.getResource(FIXTURES + name) != null
    }

    abstract void install()

    void installBusinessServices(Map blank) {
        businessServices = new CopyOnWriteArrayList<Map>(fixture('business-services.json') as List<Map>)
        get('/api/business-services') { businessServices.sort(false) { (it.name as String).toLowerCase() } }
        on('POST', '/api/business-services') { RecordedRequest request ->
            def added = [id: (businessServices*.id.max() as int) + 1, name: request.json().name, version: 0, productCount: 0] + blank
            businessServices << added
            json(added, 201)
        }
        on('PUT', '/api/business-services/(\\d+)') { RecordedRequest request, List<String> ids ->
            def businessService = businessServices.find { it.id == ids[0] as int }
            businessService.putAll(name: request.json().name, version: (businessService.version as int) + 1)
            businessService
        }
        on('DELETE', '/api/business-services/(\\d+)') { RecordedRequest request, List<String> ids ->
            businessServices.removeIf { it.id == ids[0] as int }
            StubResponse.empty()
        }
    }

    String businessServiceName(Object id) {
        businessServices.find { it.id == id }?.name
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

    StubApi failOnce(String method, String path, StubResponse failure) {
        Route route
        route = new Route(method, Pattern.compile(path), { ->
            routes.remove(route)
            failure
        })
        routes.add(0, route)
        this
    }

    StubResponse handle(String method, String path, String query, String body, Map<String, String> headers = [:]) {
        def request = new RecordedRequest(method: method, path: path, query: query, body: body, headers: headers)
        recorded << request
        def refused = refuse(request)
        if (refused) {
            return refused
        }
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

    /** An answer that comes before the routes, such as the 401 of a browser without a session; null lets the routes answer. */
    protected StubResponse refuse(RecordedRequest request) {
        null
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
        install()
    }

    static StubResponse fixtureOr404(String name, String detail) {
        if (!hasFixture(name)) {
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
