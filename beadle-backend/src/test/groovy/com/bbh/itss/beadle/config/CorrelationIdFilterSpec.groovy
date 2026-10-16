package com.bbh.itss.beadle.config

import com.bbh.itss.beadle.application.CallContext
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

class CorrelationIdFilterSpec extends Specification {

    static final String CORRELATION = '6F1C2A9E-3B7D-4C55-9A51-2F0E8D4B7C10'

    def filter = new CorrelationIdFilter()
    def response = new MockHttpServletResponse()
    Map seen = [:]
    FilterChain chain = { request, response ->
        seen.bound = CallContext.correlationId().orElse(null)
        seen.logged = MDC.get('correlationId')
    } as FilterChain

    def "every request gets a correlation id of its own, bound for the request, logged and sent back"() {
        when:
        filter.doFilter(new MockHttpServletRequest('GET', '/api/changes'), response, chain)

        then:
        CallContext.isCorrelationId(seen.bound)
        seen.logged == seen.bound
        response.getHeader('X-Correlation-Id') == seen.bound
        CallContext.correlationId().empty
        MDC.get('correlationId') == null
    }

    def "the correlation id a callback brings is kept, a header that is no UUID is not"() {
        given:
        def request = new MockHttpServletRequest('POST', '/api/callbacks/protech')
        request.addHeader('X-Correlation-Id', header)

        when:
        filter.doFilter(request, response, chain)

        then:
        (seen.bound == CORRELATION.toLowerCase()) == kept
        CallContext.isCorrelationId(seen.bound)
        response.getHeader('X-Correlation-Id') == seen.bound

        where:
        header                       || kept
        CORRELATION                  || true
        " ${CORRELATION} ".toString() || true
        'not-a-uuid'                 || false
        "${CORRELATION}; DROP".toString() || false
    }

    def "a request of a user gets a new correlation id even when it sends one"() {
        given:
        def request = new MockHttpServletRequest(method, path)
        request.addHeader('X-Correlation-Id', CORRELATION)

        when:
        filter.doFilter(request, response, chain)

        then:
        CallContext.isCorrelationId(seen.bound)
        seen.bound != CORRELATION.toLowerCase()
        response.getHeader('X-Correlation-Id') == seen.bound

        where:
        method | path
        'POST' | '/api/changes'
        'GET'  | '/api/me'
        'POST' | '/logout'
        'POST' | '/api/callbacksprotech'
    }

    def "two requests get two correlation ids"() {
        when:
        filter.doFilter(new MockHttpServletRequest('GET', '/api/me'), response, chain)
        def first = seen.bound
        filter.doFilter(new MockHttpServletRequest('GET', '/api/me'), new MockHttpServletResponse(), chain)

        then:
        first != seen.bound
    }
}
