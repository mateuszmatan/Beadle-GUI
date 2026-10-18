package com.bbh.itss.beadle.regression

import com.bbh.itss.beadle.support.BeadleClient

import java.net.CookieManager

import static com.bbh.itss.beadle.support.ChangeFixtures.secureCodingJson

class AuditTrailRegressionSpec extends ChangeRegressionSpecification {

    static final String CORRELATION = '0b7c2f4e-9d1a-4e6b-8c3f-5a2d7e9f1b40'

    def "a created product leaves one SUCCESS row under the request's correlation id, which the user cannot choose"() {
        given:
        def code = uniqueCode()

        when:
        def response = api.post('/api/products', product(code: code, name: "Audited ${code}"),
                ['X-Correlation-Id': CORRELATION])

        then:
        response.status == 201
        response.header('X-Correlation-Id') ==~ /[0-9a-f-]{36}/
        response.header('X-Correlation-Id') != CORRELATION
        rowsAbout(code) == [[USER_NAME: SIGNED_IN, ACTION: 'PRODUCT_CREATED', OBJECT_TYPE: 'PRODUCT',
                             OBJECT_ID: response.json.id as Long, OUTCOME: 'SUCCESS', MESSAGE: null,
                             CORRELATION_ID: response.header('X-Correlation-Id')]]
    }

    def "a refused product is rolled back, but its FAILURE row stays"() {
        given:
        def code = uniqueCode()
        createProduct(product(code: code, name: "First ${code}"))

        when:
        def refused = api.post('/api/products', product(code: code, name: "Second ${code}"))

        then:
        refused.status == 409
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_PRODUCT WHERE CODE = ?', Integer, code) == 1
        rowsAbout(code)*.subMap(['ACTION', 'OUTCOME', 'MESSAGE']) == [
                [ACTION: 'PRODUCT_CREATED', OUTCOME: 'SUCCESS', MESSAGE: null],
                [ACTION: 'PRODUCT_CREATED', OUTCOME: 'FAILURE', MESSAGE: refused.json.detail]]
        rowsAbout(code)[1].CORRELATION_ID == refused.header('X-Correlation-Id')
    }

    def "raising a change, its tasks and its ticket are filed under the change number and found by CTASK or ticket key"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Raised ${uniqueCode()}")))

        when:
        def ticket = api.post("/api/changes/$raised.id/secure-coding", [version: raised.version,
                businessServiceId: raised.businessServiceId, implementationDate: '10202026'] + secureCodingJson())
        def key = ticket.json.template.secureCodingTicket as String

        then:
        ticket.status == 200
        rowsAbout(raised.number as String)*.subMap(['ACTION', 'OBJECT_ID', 'OUTCOME']) == [
                [ACTION: 'CHANGE_RAISED', OBJECT_ID: raised.id as Long, OUTCOME: 'SUCCESS'],
                [ACTION: 'CHANGE_TASKS_CREATED', OBJECT_ID: raised.id as Long, OUTCOME: 'SUCCESS'],
                [ACTION: 'SECURE_CODING_TICKET_CREATED', OBJECT_ID: raised.id as Long, OUTCOME: 'SUCCESS']]
        raised.tasks*.number.every { number ->
            def found = api.get("/api/action-history?objectKey=$number").json.content
            found*.action == ['CHANGE_TASKS_CREATED'] && found[0].objectKey == raised.number &&
                    (found[0].details as String).endsWith(',"result":{"created":["' +
                            raised.tasks*.number.join('","') + '"]}}')
        }
        api.get("/api/action-history?objectKey=$key").json.content*.subMap(['action', 'user', 'objectId', 'objectKey']) ==
                [[action: 'SECURE_CODING_TICKET_CREATED', user: SIGNED_IN, objectId: raised.id, objectKey: raised.number]]
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
        page.json.subMap('page', 'size', 'total') == [page: 0, size: 1, total: 2]
        page.json.content*.action == ['PRODUCT_UPDATED']
        def row = page.json.content[0] as Map
        row.subMap(['user', 'objectType', 'objectId', 'objectKey', 'outcome']) ==
                [user: SIGNED_IN, objectType: 'PRODUCT', objectId: created.id, objectKey: code, outcome: 'SUCCESS']
        (row.details as String).contains("History renamed ${code}")
        row.correlationId ==~ /[0-9a-f-]{36}/
        row.durationMs >= 0
        api.get("/api/action-history?objectKey=$code&page=1&size=1").json.content*.action == ['PRODUCT_CREATED']
        api.get("/api/action-history?objectKey=$code&action=PRODUCT_CREATED&outcome=FAILURE").json.total == 0
        api.get("/api/action-history?objectKey=$code&user=${enc(SIGNED_IN)}&from=${enc(row.at as String)}")
                .json.content*.action == ['PRODUCT_UPDATED']
        SIGNED_IN in api.get('/api/action-history/users').json
        api.get('/api/action-history/actions').json.containsAll(['PRODUCT_CREATED', 'PRODUCT_UPDATED'])
        api.get('/api/action-history?action=CHANGE_VIEWED').status == 400
    }

    def "the first API call of a session records the sign-in, once, and a new session after signing out another"() {
        given:
        def browser = new BeadleClient(api.baseUrl, new CookieManager())
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
}
