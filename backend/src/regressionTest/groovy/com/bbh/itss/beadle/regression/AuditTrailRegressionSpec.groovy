package com.bbh.itss.beadle.regression

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

    def "reads leave no row"() {
        given:
        int before = jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY', Integer)

        when:
        api.get('/api/products')
        api.get('/api/departments')
        api.get('/api/changes')

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY', Integer) == before
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
