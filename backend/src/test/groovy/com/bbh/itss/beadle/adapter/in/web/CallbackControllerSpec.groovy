package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.callback.port.in.CallbacksUseCase
import com.bbh.itss.beadle.config.CorrelationIdFilter
import com.bbh.itss.beadle.config.ServiceTokenFilter
import com.bbh.itss.beadle.config.ServicesProperties
import com.bbh.itss.beadle.config.ServicesProperties.Endpoint
import com.bbh.itss.beadle.domain.callback.InternalCallback
import com.bbh.itss.beadle.domain.callback.ProTechCallback
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.Instant

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
    static final Map PROTECH = [number: 'CHG0031001', event: 'STATE_CHANGED', state: 'IMPLEMENTATION',
                                at    : '2026-10-15T09:00:00Z', correlationId: CORRELATION]
    static final Map INTERNAL = [system: 'CYBERTRACK', key: 'SCP-123', status: 'Done', at: '2026-10-15T09:00:00Z',
                                 correlationId: CORRELATION]

    CallbacksUseCase callbacks = Mock()
    List<String> calledAs = []

    MockMvc mvc = mvc(TOKEN)

    def "a ProTech callback with the service token is accepted and read again for system:protech"() {
        when:
        def response = callback('/api/callbacks/protech', PROTECH, "Bearer $TOKEN")

        then:
        1 * callbacks.proTech(new ProTechCallback('CHG0031001', STATE_CHANGED, IMPLEMENTATION,
                Instant.parse('2026-10-15T09:00:00Z'), CORRELATION)) >> {
            calledAs << CallContext.systemActor().orElse(null) << CallContext.correlationId().orElse(null)
            Optional.empty()
        }
        response.status == 202
        response.getHeader('X-Correlation-Id') != null
        calledAs == ['system:protech', CORRELATION]
        CallContext.systemActor().empty
    }

    def "an Internal callback is accepted for system:internal"() {
        when:
        def response = callback('/api/callbacks/internal', INTERNAL, "Bearer $TOKEN")

        then:
        1 * callbacks.internal(new InternalCallback(CYBERTRACK, 'SCP-123', 'Done',
                Instant.parse('2026-10-15T09:00:00Z'), CORRELATION)) >> {
            calledAs << CallContext.systemActor().orElse(null)
        }
        response.status == 202
        calledAs == ['system:internal']
    }

    def "a callback #without is refused with 401 and never reaches Beadle"() {
        when:
        def response = callback(path, PROTECH, authorization)

        then:
        0 * callbacks._
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

    def "a callback #problem is refused with 400"() {
        when:
        def response = callback(path, body, "Bearer $TOKEN")

        then:
        response.status == 400
        0 * callbacks._

        where:
        problem                  | path                      | body
        'without a change'       | '/api/callbacks/protech'  | PROTECH + [number: ' ']
        'with an unknown event'  | '/api/callbacks/protech'  | PROTECH + [event: 'MOVED']
        'without an event'       | '/api/callbacks/protech'  | PROTECH + [event: null]
        'of an unknown system'   | '/api/callbacks/internal' | INTERNAL + [system: 'SHAREPOINT']
        'without a key'          | '/api/callbacks/internal' | INTERNAL + [key: null]
    }

    private def callback(String path, Map body, String authorization) {
        def request = post(path).contentType(APPLICATION_JSON).content(toJson(body))
        if (authorization) {
            request.header('Authorization', authorization)
        }
        mvc.perform(request).andReturn().response
    }

    private MockMvc mvc(String token) {
        def properties = new ServicesProperties(token, new Endpoint(null), new Endpoint(null))
        standaloneSetup(new CallbackController(callbacks)).setControllerAdvice(new ApiExceptionHandler())
                .addFilters(new CorrelationIdFilter(), new ServiceTokenFilter(properties)).build()
    }
}
