package com.bbh.itss.beadle.regression

import java.time.Instant

class CallbackRegressionSpec extends ChangeRegressionSpecification {

    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'

    def "Beadle-ProTech and Beadle-Internal call back with the service token, are recorded as the system under the correlation id they pass, and are refused 401 without it"() {
        given:
        def raised = raise(createProduct(product(code: uniqueCode(), name: "Callback ${uniqueCode()}")))
        def moved = SERVICES.proTechChange(raised.number as String)
        moved.state = 'BUSINESS_APPROVAL'
        moved.workflow = moved.workflow + [[state: 'BUSINESS_APPROVAL', enteredAt: Instant.now().toString()]]
        def headers = ['X-Correlation-Id': CORRELATION]

        when:
        def proTech = callback('/api/callbacks/protech', [number: raised.number, event: 'STATE_CHANGED',
                                                          state: 'BUSINESS_APPROVAL', at: Instant.now().toString(),
                                                          correlationId: CORRELATION], headers)
        def internal = callback('/api/callbacks/internal', [system: 'CYBERTRACK', key: 'SCP-8888', status: 'Done',
                                                            at: Instant.now().toString(), correlationId: CORRELATION],
                headers)

        then:
        [proTech, internal]*.status == [202, 202]
        [proTech, internal]*.header('X-Correlation-Id') == [CORRELATION, CORRELATION]
        jdbc.queryForObject('SELECT STATE FROM BEADLE_PRODUCTION_CHANGE WHERE ID = ?', String, raised.id) ==
                'BUSINESS_APPROVAL'
        rows(CORRELATION) == [
                [USER_NAME: 'system:protech', ACTION: 'CALLBACK_PROTECH', OBJECT_KEY: raised.number, OUTCOME: 'SUCCESS'],
                [USER_NAME: 'system:internal', ACTION: 'CALLBACK_INTERNAL', OBJECT_KEY: 'SCP-8888', OUTCOME: 'SUCCESS']]
        SERVICES.requests.findAll { it.header('X-Correlation-Id') == CORRELATION }*.header('X-Beadle-User')
                .unique() == ['system']

        when:
        def missing = api.post('/api/callbacks/protech', [number: raised.number, event: 'TASK_CHANGED'])
        def wrong = api.post('/api/callbacks/internal', [system: 'CYBERTRACK', key: 'SCP-1'],
                [Authorization: 'Bearer not-the-token'])

        then:
        [missing, wrong]*.status == [401, 401]
        missing.json.detail == 'The call needs the Beadle service token as Authorization: Bearer <token>.'
        missing.header('WWW-Authenticate') == 'Bearer'
    }

    private List<Map> rows(String correlationId) {
        jdbc.queryForList('''SELECT USER_NAME, ACTION, OBJECT_KEY, OUTCOME FROM BEADLE_ACTION_HISTORY
                WHERE CORRELATION_ID = ? ORDER BY ID''', correlationId)
    }
}
