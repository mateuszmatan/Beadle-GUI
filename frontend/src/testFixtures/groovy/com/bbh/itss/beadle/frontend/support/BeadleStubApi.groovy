package com.bbh.itss.beadle.frontend.support

import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

import static com.bbh.itss.beadle.frontend.support.StubResponse.empty
import static com.bbh.itss.beadle.frontend.support.StubResponse.json
import static com.bbh.itss.beadle.frontend.support.StubResponse.problem

class BeadleStubApi extends StubApi {

    static final String SIGNED_IN_USER = 'Mateusz Matan'

    static final String SAVED_AT = '2026-10-05T10:00:00Z'

    List<Map> products
    List<Map> actions
    ChangeStubs.ProTech protech
    /** Whether the browser has a session; without one every API call answers 401, as Beadle does when signing in is oidc. */
    volatile boolean signedIn

    @Override
    void install() {
        installDepartments(changeCount: 0)
        products = new CopyOnWriteArrayList<Map>(fixture('products.json') as List<Map>)
        installSignIn()
        installProducts()
        installActionHistory()
        protech = ChangeStubs.install(this)
    }

    /**
     * Who is signed in, Spring Security's {@code POST /logout} that ends the session, and a stand-in of the Ping sign-in
     * that signs the browser in at once and sends it back to {@code /}, as Ping does for a browser it knows.
     */
    void installSignIn() {
        signedIn = true
        get('/api/me') { [name: SIGNED_IN_USER, email: null] }
        on('POST', '/logout') {
            signedIn = false
            empty()
        }
        get('/oauth2/authorization/ping') {
            signedIn = true
            StubResponse.redirect('/')
        }
    }

    @Override
    protected StubResponse refuse(RecordedRequest request) {
        !signedIn && request.path.startsWith('/api/') ? problem(401, 'Unauthorized', 'Sign in first') : null
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

    /** Adds {@code count} sign-ins of {@code user} older than every row so far, for the paging of the history. */
    void addSignIns(int count, String user = 'Ann Lee') {
        def oldest = actions.collect { Instant.parse(it.at as String) }.min()
        int next = (actions*.id.max() as int) + 1
        (1..count).each { index ->
            actions << [id           : next + index, at: oldest.minusSeconds(60L * index).toString(), user: user,
                        action       : 'USER_SIGNED_IN', objectType: 'USER', objectId: null, objectKey: user,
                        details      : '{}', outcome: 'SUCCESS', message: null,
                        correlationId: null, durationMs: 2]
        }
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
                [it.code, it.name, it.ownerTeam, it.departmentName].any { value -> value?.toString()?.toLowerCase()?.contains(search) }
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
         departmentId: sent.departmentId, departmentName: departmentName(sent.departmentId), version: version,
         updatedAt   : SAVED_AT]
    }
}
