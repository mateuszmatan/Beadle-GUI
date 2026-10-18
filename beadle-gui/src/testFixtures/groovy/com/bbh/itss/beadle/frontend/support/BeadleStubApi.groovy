package com.bbh.itss.beadle.frontend.support

import groovy.json.JsonSlurperClassic
import groovy.transform.TupleConstructor

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern

import static com.bbh.itss.beadle.frontend.support.StubResponse.empty
import static com.bbh.itss.beadle.frontend.support.StubResponse.json
import static com.bbh.itss.beadle.frontend.support.StubResponse.problem

/**
 * The Beadle API the browser suites run against: business services, products, the action history, the sign-in with
 * a stand-in of Ping, and the changes of {@link ChangeStubs}. It records every request it answers.
 */
class BeadleStubApi {

    static final String FIXTURES = '/com/bbh/itss/beadle/frontend/api/'

    static final String SIGNED_IN_USER = 'Mateusz Matan'

    static final String SAVED_AT = '2026-10-05T10:00:00Z'

    /** Where the stand-in of Ping ends its session, on the stub server as Ping's address is in the browser. */
    static final String PING_END_SESSION = '/ping/idp/init_logout.openid'

    private final List<Route> routes = new CopyOnWriteArrayList<>()
    private final List<RecordedRequest> recorded = new CopyOnWriteArrayList<>()
    List<Map> businessServices
    List<Map> products
    List<Map> actions
    ChangeStubs.ProTech protech
    /** Whether the browser has a session; without one every API call answers 401, as Beadle does when signing in is oidc. */
    volatile boolean signedIn
    /** Whether Ping knows the browser, so that it signs it in at once without asking for a password. */
    volatile boolean pingSession

    /**
     * The parsed fixture as plain {@code LinkedHashMap}s and {@code ArrayList}s. The handlers share these maps across the
     * threads of {@link GuiServer}, and the lazy maps of the default {@code JsonSlurper} are not safe to read from two
     * threads at once: one thread can see a map that another is still filling in and answer a 500.
     */
    static Object fixture(String name) {
        def resource = BeadleStubApi.getResource(FIXTURES + name)
        if (!resource) {
            throw new IllegalArgumentException("No API fixture $name")
        }
        new JsonSlurperClassic().parseText(resource.getText('UTF-8'))
    }

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

    BeadleStubApi get(String path, Closure handler) {
        on('GET', path, handler)
    }

    BeadleStubApi on(String method, String path, Closure handler) {
        routes.add(0, new Route(method, Pattern.compile(path), handler))
        this
    }

    BeadleStubApi respond(String method, String path, Object body, int status = 200) {
        on(method, path) { json(body, status) }
    }

    BeadleStubApi respond(String method, String path, StubResponse response) {
        on(method, path) { response }
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

    /** The 401 of a browser without a session, before the routes; null lets the routes answer. */
    private StubResponse refuse(RecordedRequest request) {
        !signedIn && request.path.startsWith('/api/') ? problem(401, 'Unauthorized', 'Sign in first') : null
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

    void install() {
        installBusinessServices(changeCount: 0)
        products = new CopyOnWriteArrayList<Map>(fixture('products.json') as List<Map>)
        installSignIn()
        installProducts()
        installActionHistory()
        protech = ChangeStubs.install(this)
    }

    /**
     * Who is signed in, Spring Security's {@code POST /logout} that ends the session and answers where Ping ends its
     * own, and a stand-in of Ping: its sign-in signs a browser it knows in at once and sends it back to {@code /}, and
     * shows its sign-in page to one it does not; its end of the session forgets the browser and sends it back to
     * {@code post_logout_redirect_uri}.
     */
    void installSignIn() {
        signedIn = true
        pingSession = true
        get('/api/me') { [name: SIGNED_IN_USER, email: null] }
        on('POST', '/logout') {
            signedIn = false
            json([redirectUrl: PING_END_SESSION + '?id_token_hint=stub-id-token&post_logout_redirect_uri=%2F'])
        }
        get('/ping/idp/init_logout.openid') { RecordedRequest request ->
            pingSession = false
            StubResponse.redirect(request.params().post_logout_redirect_uri ?: '/')
        }
        get('/oauth2/authorization/ping') {
            if (!pingSession) {
                return new StubResponse(status: 200, contentType: 'text/html; charset=utf-8',
                        body: '<!doctype html><title>Ping</title><h1>Sign on to Ping</h1>' +
                                '<a href="/ping/sign-on">Sign On</a>')
            }
            signedIn = true
            StubResponse.redirect('/')
        }
        get('/ping/sign-on') {
            pingSession = true
            signedIn = true
            StubResponse.redirect('/')
        }
    }

    /** The audit trail of {@code action-history.json}, filtered and paged like {@code GET /api/action-history}. */
    void installActionHistory() {
        actions = new CopyOnWriteArrayList<Map>(fixture('action-history.json') as List<Map>)
        get('/api/action-history') { RecordedRequest request ->
            def params = request.params()
            int page = (params.page ?: '0') as int
            int size = (params.size ?: '50') as int
            def matching = actions.findAll { recordedAction(it, params) }
                    .sort { a, b -> Instant.parse(b.at as String) <=> Instant.parse(a.at as String) ?: b.id <=> a.id }
            json([content: matching.drop(page * size).take(size), page: page, size: size, total: matching.size()])
        }
        get('/api/action-history/users') { actions*.user.unique(false).sort(false) }
        get('/api/action-history/actions') { actions*.action.unique(false).sort(false) }
    }

    private static boolean recordedAction(Map row, Map<String, String> params) {
        def at = Instant.parse(row.at as String)
        (!params.user || row.user == params.user) &&
                (!params.action || row.action == params.action) &&
                (!params.outcome || row.outcome == params.outcome) &&
                (!params.objectType || row.objectType == params.objectType) &&
                (!params.objectKey || (row.objectKey as String)?.toUpperCase()?.startsWith(params.objectKey.toUpperCase())) &&
                (!params.from || !at.isBefore(Instant.parse(params.from))) &&
                (!params.to || at.isBefore(Instant.parse(params.to)))
    }

    void installProducts() {
        get('/api/products') { RecordedRequest request ->
            def search = request.params().search?.toLowerCase()
            json(search ? products.findAll {
                [it.code, it.name, it.ownerTeam, it.businessServiceName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
            } : products)
        }
        get('/api/products/code-suggestion') { RecordedRequest request ->
            json([code: request.params().name.toUpperCase().replaceAll(/[^A-Z0-9]/, '')])
        }
        get('/api/products/(\\d+)') { RecordedRequest request, List<String> ids ->
            def product = product(ids[0])
            product ? json(product) : problem(404, 'Not Found', "Product ${ids[0]} was not found")
        }
        on('POST', '/api/products') { RecordedRequest request ->
            def sent = request.json() as Map
            def created = stored(sent, (products*.id.max() as int) + 1, 0)
            products << created
            json(created, 201)
        }
        on('PUT', '/api/products/(\\d+)') { RecordedRequest request, List<String> ids ->
            def sent = request.json() as Map
            def current = product(ids[0])
            if (sent.version != current.version) {
                return problem(409, 'Conflict', 'The product was changed by someone else; reload it and try again')
            }
            def saved = stored(sent + [code: current.code], current.id, (current.version as int) + 1)
            products[products.indexOf(current)] = saved
            json(saved)
        }
        on('DELETE', '/api/products/(\\d+)') { RecordedRequest request, List<String> ids ->
            products.removeIf { it.id == ids[0] as int }
            empty()
        }
    }

    Map product(Object id) {
        products.find { it.id == id as int }
    }

    private Map stored(Map sent, int id, int version) {
        [id          : id, code: sent.code, name: sent.name, ownerTeam: sent.ownerTeam, contactEmail: sent.contactEmail,
         businessServiceId: sent.businessServiceId, businessServiceName: businessServiceName(sent.businessServiceId), version: version,
         updatedAt   : SAVED_AT]
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
