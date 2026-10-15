package com.bbh.itss.beadle.adapter.out.protech

import com.bbh.itss.beadle.adapter.out.http.ServiceClientSpecification
import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.domain.change.ApprovalRef
import com.bbh.itss.beadle.domain.change.Lookup
import com.bbh.itss.beadle.domain.change.LookupKind
import com.bbh.itss.beadle.domain.change.ProductionChange
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem

import static com.bbh.itss.beadle.domain.change.ApprovalRole.L1
import static com.bbh.itss.beadle.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.beadle.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.support.ChangeFixtures.task
import static java.time.Duration.ofSeconds

class RestProTechAdapterSpec extends ServiceClientSpecification {

    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'

    ProTechPort getProTech() {
        bean(ProTechPort)
    }

    def "a change is raised with the service token, the signed-in user and the correlation id of the user action"() {
        given:
        service.answer('POST', '/api/protech/changes', 201, [number: 'CHG0031001', url: 'https://protech/CHG0031001'])
        def draft = raised(id: null, number: null)

        when:
        def created = CallContext.correlatedBy(CORRELATION).withCloseable { proTech.raise(draft) }

        then:
        created == new ProTechPort.RaisedChange('CHG0031001', 'https://protech/CHG0031001')
        with(service.last) {
            method == 'POST'
            path == '/api/protech/changes'
            header('Authorization') == "Bearer $TOKEN"
            header('X-Beadle-User') == USER
            header('X-Correlation-Id') == CORRELATION
            header('Content-Type').startsWith('application/json')
            json.shortDescription == draft.shortDescription()
            json.template.approvers.l1Manager == 'Olivia Bennett'
            json.schedule.installationStart == '2026-10-10T06:00:00Z'
            json.state == 'DRAFT'
            json.keySet() == ProductionChange.recordComponents*.name as Set
        }
    }

    def "a call no user caused names the system, and a call outside a user action gets a correlation id of its own"() {
        given:
        service.answer('POST', '/api/protech/changes/CHG0031001/tasks', 201, [number: 'CTASK0041003'])

        when:
        def number = CallContext.actingAs('system:protech').withCloseable {
            proTech.createTask('CHG0031001', task(3))
        }

        then:
        number == 'CTASK0041003'
        service.last.header('X-Beadle-User') == 'system'
        CallContext.isCorrelationId(service.last.header('X-Correlation-Id'))
        service.last.json.details.shortDescription == 'Task 3 of the CertScanner release'
    }

    def "the changes Beadle knows are read back as ProTech holds them"() {
        given:
        def known = raised()
        def moved = raised(state: IMPLEMENTATION, syncProblem: null)
        service.answer('POST', '/api/protech/changes/read', 200, '{"CHG0031001":' + json(moved) + '}')

        when:
        def read = proTech.read([known])

        then:
        read.keySet() == ['CHG0031001'] as Set
        read.CHG0031001.state() == IMPLEMENTATION
        read.CHG0031001.tasks()*.number() == ['CTASK0041001', 'CTASK0041002']
        read.CHG0031001.schedule() == known.schedule()
        read.CHG0031001.template() == known.template()
        service.last.json*.number == ['CHG0031001']
        proTech.read([]) == [:]
        service.requests.size() == 1
    }

    def "an update is put on the change and reminders answer the people reminded"() {
        given:
        service.answer('PUT', '/api/protech/changes/CHG0031001', 204)
        service.answer('POST', '/api/protech/changes/CHG0031001/reminders', 200, ['James Carter'])

        when:
        proTech.update(raised())
        def reminded = proTech.remind('CHG0031001', ApprovalRef.of(L1))

        then:
        reminded == ['James Carter']
        service.requests*.method == ['PUT', 'POST']
        service.requests[0].json.number == 'CHG0031001'
        service.requests[1].json == [role: 'L1', task: null]
    }

    def "lookups are found by their kind, the text and the limit"() {
        given:
        service.answer('GET', '/api/protech/lookups/assignment-groups', 200,
                [[value: 'Release Management', detail: 'ITSM']])

        when:
        def found = bean(ProTechLookupPort).find(ASSIGNMENT_GROUPS, 'Release & co', 20)

        then:
        found == [new Lookup('Release Management', 'ITSM')]
        service.last.decodedQuery == 'q=Release & co&limit=20'
    }

    def "the text of a lookup reaches Beadle-ProTech as typed, whatever characters it holds"() {
        given:
        service.answer('GET', '/api/protech/lookups/clients', 200, [])

        when:
        bean(ProTechLookupPort).find(LookupKind.CLIENTS, text, 20)

        then:
        service.last.params == [q: text, limit: '20']

        where:
        text << ['{x}', 'C++ team', 'R&D = 1', '50% {done} & C++']
    }

    def "a lookup without text sends only the limit"() {
        given:
        service.answer('GET', '/api/protech/lookups/clients', 200, [])

        when:
        bean(ProTechLookupPort).find(LookupKind.CLIENTS, null, 20)

        then:
        service.last.query == 'limit=20'
    }

    def "ProTech counts as connected while Beadle-ProTech answers live, asked at most every 30 seconds"() {
        given:
        service.answer('GET', '/api/protech/status', 200, [connected: true, mode: 'live'])

        expect:
        proTech.connected()
        proTech.connected()
        service.requests.size() == 1

        when:
        service.answer('GET', '/api/protech/status', 200, [connected: false, mode: 'demo'])
        clock.advance(ofSeconds(29))

        then:
        proTech.connected()

        when:
        clock.advance(ofSeconds(1))

        then:
        !proTech.connected()
        service.requests.size() == 2
        service.last.header('Authorization') == "Bearer $TOKEN"
    }

    def "an unreachable Beadle-ProTech is not connected and fails a call as a service that did not answer"() {
        given:
        context.close()
        context = start('http://127.0.0.1:1')

        expect:
        !proTech.connected()

        when:
        proTech.read([raised()])

        then:
        def failed = thrown(UncheckedIOException)
        failed.message.startsWith('Beadle-ProTech did not answer when asked to read the changes: ')
    }

    def "an answer #status of Beadle-ProTech fails the call with #type.simpleName"() {
        given:
        service.answer('POST', '/api/protech/changes/CHG0031001/reminders', status, body, 'application/problem+json')

        when:
        proTech.remind('CHG0031001', ApprovalRef.ofTask('CTASK0041001'))

        then:
        def failed = thrown(type)
        failed.message == message

        where:
        status | body                                                       || type                    | message
        502    | [status: 502, detail: 'ProTech could not be reached']      || UncheckedIOException    | 'Beadle-ProTech failed to send reminders on CHG0031001: 502 ProTech could not be reached'
        500    | ''                                                         || UncheckedIOException    | 'Beadle-ProTech failed to send reminders on CHG0031001: 500 Internal Server Error'
        401    | [status: 401, detail: 'The call needs the token']          || UncheckedIOException    | 'Beadle-ProTech refused the service token of Beadle-GUI when asked to send reminders on CHG0031001: BEADLE_SERVICE_TOKEN must be the same in the three services'
        404    | [status: 404, detail: 'ProTech has no change CHG0031001']  || NoSuchElementException  | 'ProTech has no change CHG0031001'
        409    | [status: 409, detail: 'ProTech has nobody to remind']      || IllegalStateException   | 'ProTech has nobody to remind'
        409    | ''                                                         || IllegalStateException   | 'Beadle-ProTech refused to send reminders on CHG0031001 (409)'
        400    | [status: 400, detail: 'task is required']                  || IllegalStateException   | 'task is required'
    }

    def "a validation refusal of Beadle-ProTech keeps its field problems"() {
        given:
        service.answer('POST', '/api/protech/changes', 400, [status: 400, title: 'Validation failed',
                                                            problems: [[field: 'template.risk', message: 'is required']]],
                'application/problem+json')

        when:
        proTech.raise(raised(number: null))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems == [new FieldProblem('template.risk', 'is required')]
    }

    def "an answer without the number of the new change or change task fails the call"() {
        given:
        service.answer('POST', '/api/protech/changes', 201, [url: null])
        service.answer('POST', '/api/protech/changes/CHG0031001/tasks', 201, '{}')

        when:
        proTech.raise(raised(number: null))

        then:
        def change = thrown(UncheckedIOException)
        change.message == 'Beadle-ProTech answered without the number of the new change'

        when:
        proTech.createTask('CHG0031001', task(1))

        then:
        def ctask = thrown(UncheckedIOException)
        ctask.message == 'Beadle-ProTech answered without the number of the new change task'
    }

    private String json(Object value) {
        bean(tools.jackson.databind.json.JsonMapper).writeValueAsString(value)
    }
}
