package com.bbh.itss.beadle.regression

import java.time.Instant

class CallbackRegressionSpec extends ChangeRegressionSpecification {

    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'

    def "Beadle-ProTech tells Beadle that a change it raised moved, and Beadle reads it again"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Callback ${uniqueCode()}")))

        when:
        def response = callback('/api/callbacks/protech', [number: raised.number, event: 'STATE_CHANGED',
                                                            state: 'BUSINESS_APPROVAL', at: Instant.now().toString(),
                                                            correlationId: CORRELATION])

        then:
        response.status == 202
        response.body == ''
        api.get("/api/changes/$raised.id").json.number == raised.number
    }

    def "a callback about a change Beadle did not raise is still taken"() {
        expect:
        callback('/api/callbacks/protech', [number: 'CHG9999999', event: 'TASK_CHANGED']).status == 202
    }

    def "Beadle-Internal tells Beadle that a secure coding ticket changed status"() {
        expect:
        callback('/api/callbacks/internal', [system: 'CYBERTRACK', key: 'SCP-1234', status: 'Done',
                                             at: Instant.now().toString(), correlationId: CORRELATION]).status == 202
    }

    def "a callback without the service token is refused with 401"() {
        when:
        def missing = api.post('/api/callbacks/protech', [number: 'CHG9999999', event: 'TASK_CHANGED'])
        def wrong = api.post('/api/callbacks/internal', [system: 'CYBERTRACK', key: 'SCP-1'],
                [Authorization: 'Bearer not-the-token'])

        then:
        [missing, wrong]*.status == [401, 401]
        missing.json.detail == 'The call needs the Beadle service token as Authorization: Bearer <token>.'
        missing.header('WWW-Authenticate') == 'Bearer'
    }

    def "a callback Beadle cannot read is refused with 400"() {
        expect:
        callback('/api/callbacks/protech', [number: 'CHG0000001', event: 'SOMETHING']).status == 400
        callback('/api/callbacks/internal', [system: 'CYBERTRACK']).status == 400
    }

    def "every answer names the correlation id of its user action"() {
        when:
        def first = api.get('/api/me')
        def second = api.get('/api/me')

        then:
        [first, second].every { it.header('X-Correlation-Id') ==~ /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/ }
        first.header('X-Correlation-Id') != second.header('X-Correlation-Id')
    }

    private def callback(String path, Map body) {
        api.post(path, body, [Authorization: "Bearer $SERVICE_TOKEN".toString()])
    }
}
