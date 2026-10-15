package com.bbh.itss.beadle.regression

import com.bbh.itss.beadle.support.PortalClient

import java.net.CookieManager
import java.time.Instant

class AuditTrailRegressionSpec extends ChangeRegressionSpecification {

    static final String CORRELATION = '0b7c2f4e-9d1a-4e6b-8c3f-5a2d7e9f1b40'

    def "creating a product leaves one SUCCESS row under the correlation id of the request"() {
        given:
        def code = uniqueCode()

        when:
        def response = api.post('/api/products', product(code: code, name: "Audited ${code}"))

        then:
        response.status == 201
        rowsAbout(code) == [[USER_NAME: SIGNED_IN, ACTION: 'PRODUCT_CREATED', OBJECT_TYPE: 'PRODUCT',
                             OBJECT_ID: response.json.id as Long, OUTCOME: 'SUCCESS', MESSAGE: null,
                             CORRELATION_ID: response.header('X-Correlation-Id')]]
        details(code)[0].contains("\"code\":\"${code}\"")
    }

    def "a user cannot file an action under a correlation id of their choosing"() {
        given:
        def code = uniqueCode()

        when:
        def response = api.post('/api/products', product(code: code, name: "Chosen ${code}"),
                ['X-Correlation-Id': CORRELATION])

        then:
        response.status == 201
        response.header('X-Correlation-Id') != CORRELATION
        rowsAbout(code)*.CORRELATION_ID == [response.header('X-Correlation-Id')]
    }

    def "a refused product is rolled back, but its FAILURE row stays"() {
        given:
        def code = uniqueCode()
        createProduct(product(code: code, name: "First ${code}"))

        when:
        def refused = api.post('/api/products', product(code: code, name: "Second ${code}"))

        then:
        refused.status == 409
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCT WHERE CODE = ?', Integer, code) == 1
        rowsAbout(code)*.subMap(['ACTION', 'OUTCOME', 'CORRELATION_ID']) == [
                [ACTION: 'PRODUCT_CREATED', OUTCOME: 'SUCCESS', CORRELATION_ID: rowsAbout(code)[0].CORRELATION_ID],
                [ACTION: 'PRODUCT_CREATED', OUTCOME: 'FAILURE', CORRELATION_ID: refused.header('X-Correlation-Id')]]
        rowsAbout(code)[1].MESSAGE == refused.json.detail
    }

    def "raising a change and creating its tasks is filed under the change number"() {
        given:
        def created = createProduct(product(code: uniqueCode(), name: "Raised ${uniqueCode()}"))

        when:
        def raised = raise(created)

        then:
        rowsAbout(raised.number as String)*.ACTION == ['CHANGE_RAISED', 'CHANGE_TASKS_CREATED']
        rowsAbout(raised.number as String).every { it.OUTCOME == 'SUCCESS' && it.OBJECT_ID == raised.id as Long }
    }

    def "the callbacks are recorded as the system that called back, under the correlation id it passed on"() {
        when:
        callback('/api/callbacks/protech', [number: 'CHG8888888', event: 'TASK_CHANGED', correlationId: CORRELATION])
        callback('/api/callbacks/internal', [system: 'CYBERTRACK', key: 'SCP-8888', status: 'Done',
                                             at: Instant.now().toString(), correlationId: CORRELATION])

        then:
        rowsAbout('CHG8888888')*.subMap(['USER_NAME', 'ACTION', 'OBJECT_TYPE', 'OUTCOME', 'CORRELATION_ID']) == [
                [USER_NAME: 'system:protech', ACTION: 'CALLBACK_PROTECH', OBJECT_TYPE: 'CHANGE', OUTCOME: 'SUCCESS',
                 CORRELATION_ID: CORRELATION]]
        rowsAbout('SCP-8888')*.subMap(['USER_NAME', 'ACTION', 'OBJECT_TYPE', 'OUTCOME', 'CORRELATION_ID']) == [
                [USER_NAME: 'system:internal', ACTION: 'CALLBACK_INTERNAL', OBJECT_TYPE: 'TICKET', OUTCOME: 'SUCCESS',
                 CORRELATION_ID: CORRELATION]]
    }

    def "the action history answers the rows newest first, filtered and paged, with the values of its filters"() {
        given:
        def code = uniqueCode()
        def created = createProduct(product(code: code, name: "History ${code}"))
        api.put("/api/products/$created.id", product(code: code, name: "History renamed ${code}", version: 0))

        when:
        def page = api.get("/api/action-history?objectKey=${code.toLowerCase()}&size=1")

        then:
        page.status == 200
        page.json.page == 0
        page.json.size == 1
        page.json.total == 2
        page.json.content*.action == ['PRODUCT_UPDATED']
        def row = page.json.content[0] as Map
        row.subMap(['user', 'objectType', 'objectId', 'objectKey', 'outcome']) ==
                [user: SIGNED_IN, objectType: 'PRODUCT', objectId: created.id, objectKey: code, outcome: 'SUCCESS']
        (row.details as String).contains("History renamed ${code}")
        row.correlationId ==~ /[0-9a-f-]{36}/
        row.durationMs >= 0
        api.get("/api/action-history?objectKey=$code&page=1&size=1").json.content*.action == ['PRODUCT_CREATED']
        api.get("/api/action-history?objectKey=$code&action=PRODUCT_CREATED&outcome=FAILURE").json.total == 0
        SIGNED_IN in api.get('/api/action-history/users').json
        api.get('/api/action-history/actions').json.containsAll(['PRODUCT_CREATED', 'PRODUCT_UPDATED'])

        and:
        api.get('/api/action-history?size=201').status == 400
        api.get('/api/action-history?action=CHANGE_VIEWED').status == 400
    }

    def "the first API call of a session records the sign-in, once, and a new session after signing out another"() {
        given:
        def browser = new PortalClient(api.baseUrl, new CookieManager())
        int before = signIns()

        when:
        def first = browser.get('/api/me')
        browser.get('/api/products')
        browser.get('/api/action-history?size=1')

        then:
        first.status == 200
        first.json == [name: SIGNED_IN, email: null]
        signIns() == before + 1
        lastSignIn() == [USER_NAME: SIGNED_IN, OBJECT_TYPE: 'USER', OBJECT_KEY: SIGNED_IN, OUTCOME: 'SUCCESS',
                         CORRELATION_ID: first.header('X-Correlation-Id')]

        when:
        def signedOut = browser.post('/logout')
        browser.get('/api/me')

        then:
        signedOut.status == 204
        signIns() == before + 2
    }

    def "reads leave no row"() {
        given:
        api.get('/api/me')
        int before = jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY', Integer)

        when:
        api.get('/api/products')
        api.get('/api/departments')
        api.get('/api/changes')
        api.get('/api/action-history')

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY', Integer) == before
    }

    private int signIns() {
        jdbc.queryForObject("SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY WHERE ACTION = 'USER_SIGNED_IN'", Integer)
    }

    private Map lastSignIn() {
        jdbc.queryForMap('''SELECT USER_NAME, OBJECT_TYPE, OBJECT_KEY, OUTCOME, CORRELATION_ID FROM BEADLE_ACTION_HISTORY
                WHERE ACTION = 'USER_SIGNED_IN' ORDER BY ID DESC FETCH FIRST 1 ROWS ONLY''')
    }

    private List<Map> rowsAbout(String key) {
        jdbc.queryForList('''SELECT USER_NAME, ACTION, OBJECT_TYPE, OBJECT_ID, OUTCOME, MESSAGE, CORRELATION_ID
                FROM BEADLE_ACTION_HISTORY WHERE OBJECT_KEY = ? ORDER BY ID''', key)
                .collect { row -> row.collectEntries { name, value -> [name, value instanceof Number ? value as Long : value] } }
    }

    private List<String> details(String key) {
        jdbc.queryForList('SELECT DETAILS FROM BEADLE_ACTION_HISTORY WHERE OBJECT_KEY = ? ORDER BY ID', String, key)
    }

    private def callback(String path, Map body) {
        def response = api.post(path, body, [Authorization: "Bearer $SERVICE_TOKEN".toString()])
        assert response.status == 202: response
        response
    }
}
