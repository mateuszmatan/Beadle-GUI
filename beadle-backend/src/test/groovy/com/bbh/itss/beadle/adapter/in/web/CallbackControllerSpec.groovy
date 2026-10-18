package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase
import com.bbh.itss.beadle.domain.audit.ActionObject
import com.bbh.itss.beadle.domain.audit.ActionRecord.ActionRecordBuilder
import com.bbh.itss.beadle.domain.callback.InternalCallback
import com.bbh.itss.beadle.domain.callback.ProTechCallback
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

import java.time.Clock
import java.time.Instant

import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.callback.InternalSystem.CYBERTRACK
import static com.bbh.itss.beadle.domain.callback.ProTechEvent.STATE_CHANGED
import static com.bbh.itss.beadle.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.beadle.support.Json.parse
import static com.bbh.itss.beadle.support.Json.toJson
import static org.springframework.http.MediaType.APPLICATION_JSON
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class CallbackControllerSpec extends Specification {

    static final String TOKEN = 'spec-service-token'
    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'
    static final Map PROTECH = [number: ' CHG0031001 ', event: 'STATE_CHANGED', state: 'IMPLEMENTATION',
                                at    : '2026-10-15T09:00:00Z']
    static final Map INTERNAL = [system: 'CYBERTRACK', key: 'SCP-123', status: 'Done', at: '2026-10-15T09:00:00Z']

    CallbacksUseCase callbacks = Mock()
    ActionHistoryUseCase history = Mock()
    List<String> calledAs = []

    MockMvc mvc = mvc(TOKEN)

    def "a ProTech callback with the service token is accepted and read again for system:protech, in its correlation"() {
        when:
        def response = callback('/api/callbacks/protech', PROTECH, "Bearer $TOKEN")

        then:
        1 * callbacks.proTech(new ProTechCallback('CHG0031001', STATE_CHANGED, IMPLEMENTATION,
                Instant.parse('2026-10-15T09:00:00Z'))) >> {
            calledAs << CallContext.systemActor().orElse(null) << CallContext.correlationId().orElse(null)
            Optional.empty()
        }
        response.status == 202
        response.getHeader('X-Correlation-Id') == CORRELATION
        calledAs == ['system:protech', CORRELATION]
        CallContext.systemActor().empty
    }

    def "an Internal callback is accepted for system:internal"() {
        when:
        def response = callback('/api/callbacks/internal', INTERNAL, "Bearer $TOKEN")

        then:
        1 * callbacks.internal(new InternalCallback(CYBERTRACK, 'SCP-123', 'Done',
                Instant.parse('2026-10-15T09:00:00Z'))) >> {
            calledAs << CallContext.systemActor().orElse(null)
        }
        response.status == 202
        calledAs == ['system:internal']
    }

    def "a callback #without is refused with 401, never reaches Beadle and is filed as refused for nobody known"() {
        when:
        def response = callback(path, PROTECH, authorization)

        then:
        0 * callbacks._
        1 * history.record({ ActionRecordBuilder row -> row.build().message() == ServiceTokenFilter.DETAIL }, _) >> {
            calledAs << CallContext.systemActor().orElse(null)
            null
        }
        calledAs == [null]
        response.status == 401
        response.getHeader('WWW-Authenticate') == 'Bearer'
        response.contentType == 'application/problem+json;charset=UTF-8'
        parse(response.contentAsString).detail == ServiceTokenFilter.DETAIL

        where:
        without                 | path                      | authorization
        'without a token'       | '/api/callbacks/protech'  | null
        'with another token'    | '/api/callbacks/protech'  | 'Bearer another-token'
        'with a basic login'    | '/api/callbacks/internal' | "Basic $TOKEN"
    }

    def "a callback whose correlation id is longer than 64 characters is refused with 400, as the services have it"() {
        when:
        def response = callback('/api/callbacks/protech', PROTECH, "Bearer $TOKEN", 'x' * 65)

        then:
        0 * callbacks._
        1 * history.record({ ActionRecordBuilder row -> row.build().action() == CALLBACK_PROTECH },
                ActionObject.keyed(CHANGE, null)) >> {
            calledAs << CallContext.systemActor().orElse(null)
            null
        }
        calledAs == ['system:protech']
        response.status == 400
        with(parse(response.contentAsString)) {
            problems*.field == ['X-Correlation-Id']
            detail == 'X-Correlation-Id is too long: it may take at most 64 characters'
        }

        expect:
        callback('/api/callbacks/protech', PROTECH, "Bearer $TOKEN", 'x' * 64).status == 202
    }

    def "no callback is accepted while the service token is blank"() {
        given:
        def closed = mvc(' ')

        when:
        def response = closed.perform(post('/api/callbacks/protech').header('Authorization', 'Bearer ')
                .contentType(APPLICATION_JSON).content(toJson(PROTECH))).andReturn().response

        then:
        response.status == 401
        0 * callbacks._
    }

    def "a callback #problem is refused with 400 naming the field under problems, as the contract has it"() {
        when:
        def response = callback(path, body, "Bearer $TOKEN")

        then:
        response.status == 400
        response.contentType.startsWith('application/problem+json')
        0 * callbacks._
        1 * history.record(_, _) >> { ActionRecordBuilder row, ActionObject object ->
            calledAs << CallContext.systemActor().orElse(null) << CallContext.correlationId().orElse(null)
            row.build()
        }
        calledAs == [actor, CORRELATION]
        with(parse(response.contentAsString)) {
            it.status == 400
            problems*.field == [field]
            problems.every { it.message }
            !it.containsKey('errors')
        }

        where:
        problem                  | path                      | body                               || field    | actor
        'without a change'       | '/api/callbacks/protech'  | PROTECH + [number: ' ']            || 'number' | 'system:protech'
        'with an unknown event'  | '/api/callbacks/protech'  | PROTECH + [event: 'MOVED']         || 'event'  | 'system:protech'
        'without an event'       | '/api/callbacks/protech'  | PROTECH + [event: null]            || 'event'  | 'system:protech'
        'of an unknown system'   | '/api/callbacks/internal' | INTERNAL + [system: 'SHAREPOINT']  || 'system' | 'system:internal'
        'without a key'          | '/api/callbacks/internal' | INTERNAL + [key: null]             || 'key'    | 'system:internal'
    }

    def "a refused callback is filed under the change its body names"() {
        when:
        callback('/api/callbacks/protech', PROTECH + [event: null], "Bearer $TOKEN")

        then:
        1 * history.record({ ActionRecordBuilder row -> row.build().action() == CALLBACK_PROTECH },
                ActionObject.keyed(CHANGE, 'CHG0031001'))
    }

    private def callback(String path, Map body, String authorization, String correlation = CORRELATION) {
        def request = post(path).contentType(APPLICATION_JSON).content(toJson(body))
                .header('X-Correlation-Id', correlation)
        if (authorization) {
            request.header('Authorization', authorization)
        }
        mvc.perform(request).andReturn().response
    }

    private MockMvc mvc(String token) {
        def controller = new CallbackController(callbacks)
        def refusals = new RefusedRequests(history, JsonMapper.builder().build(), Clock.systemUTC(),
                RefusedRequestsSpec.mappingsOf(controller))
        standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(refusals), new KeptRequestBodies(),
                        new NormalizedRequestBodies())
                .addFilters(new CorrelationIdFilter(), new ServiceTokenFilter(token, refusals)).build()
    }
}
