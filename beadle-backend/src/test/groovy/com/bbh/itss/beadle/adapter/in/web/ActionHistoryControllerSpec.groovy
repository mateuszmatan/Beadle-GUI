package com.bbh.itss.beadle.adapter.in.web

import com.bbh.itss.beadle.application.audit.port.in.ActionHistoryUseCase
import com.bbh.itss.beadle.domain.audit.Action
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery
import com.bbh.itss.beadle.domain.audit.ActionRecord
import com.bbh.itss.beadle.domain.audit.ObjectType
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.net.URI
import java.time.Instant

import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_INTERNAL
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.support.Json.parse
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

class ActionHistoryControllerSpec extends Specification {

    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'
    static final ActionRecord CREATED = ActionRecord.builder().id(41L).at(Instant.parse('2026-10-15T09:00:00Z'))
            .user('Mateusz Matan').action(CHANGE_CREATED).objectType(CHANGE).objectId(7L).objectKey('CHG0031001')
            .details('{"command":{"productId":1}}').outcome(FAILURE).message('Beadle-ProTech did not answer')
            .correlationId(CORRELATION).durationMs(5012).build()

    ActionHistoryUseCase history = Mock()
    MockMvc mvc = standaloneSetup(new ActionHistoryController(history)).setControllerAdvice(new ApiExceptionHandler(Mock(RefusedRequests)))
            .build()

    def "the audit trail answers a page of rows, newest first, in the shape of the contract"() {
        when:
        def response = call('/api/action-history')

        then:
        1 * history.find(ActionHistoryQuery.of(null, null, null, null, null, null, null, null, null)) >>
                new ActionHistoryPage([CREATED], 0, 50, 1234)
        response.status == 200
        parse(response.contentAsString) == [
                content: [[id           : 41, at: '2026-10-15T09:00:00Z', user: 'Mateusz Matan',
                           action       : 'CHANGE_CREATED', objectType: 'CHANGE', objectId: 7,
                           objectKey    : 'CHG0031001', details: '{"command":{"productId":1}}', outcome: 'FAILURE',
                           message      : 'Beadle-ProTech did not answer', correlationId: CORRELATION,
                           durationMs   : 5012]],
                page   : 0, size: 50, total: 1234]
    }

    def "every filter and the paging reach the query"() {
        when:
        def response = call('/api/action-history?user=Ann%20Lee&action=CHANGE_CREATED&objectType=CHANGE' +
                '&objectKey=CHG003&outcome=FAILURE&from=2026-10-14T00:00:00Z&to=2026-10-15T00:00:00Z&page=2&size=200')

        then:
        1 * history.find(new ActionHistoryQuery('Ann Lee', CHANGE_CREATED, CHANGE, 'CHG003', FAILURE,
                Instant.parse('2026-10-14T00:00:00Z'), Instant.parse('2026-10-15T00:00:00Z'), 2, 200)) >>
                new ActionHistoryPage([], 2, 200, 0)
        response.status == 200
        parse(response.contentAsString) == [content: [], page: 2, size: 200, total: 0]
    }

    def "a page of more than 200 rows or a range that ends before it starts is refused with 400"() {
        when:
        def response = call(path)

        then:
        0 * history._
        response.status == 400
        parse(response.contentAsString).problems*.field == fields

        where:
        path                                                                                  || fields
        '/api/action-history?size=201'                                                        || ['size']
        '/api/action-history?page=-1&size=0'                                                  || ['page', 'size']
        '/api/action-history?from=2026-10-15T00:00:00Z&to=2026-10-14T00:00:00Z'                || ['from']
    }

    def "an unknown action, object type, outcome or time is refused with 400 and the parameter under problems"() {
        when:
        def response = call(path)

        then:
        0 * history._
        response.status == 400
        with(parse(response.contentAsString)) {
            detail == "The value given for '$parameter' is not one this endpoint can read."
            problems == [[field: parameter, message: message]]
        }

        where:
        path                                         || parameter    | message
        '/api/action-history?action=CHANGE_VIEWED'   || 'action'     | 'must be one of ' + Action.values().join(', ')
        '/api/action-history?objectType=FOO'         || 'objectType' | 'must be one of ' + ObjectType.values().join(', ')
        '/api/action-history?outcome=MAYBE'          || 'outcome'    | 'must be one of SUCCESS, FAILURE'
        '/api/action-history?from=yesterday'         || 'from'       | 'must be an instant in UTC, such as 2026-10-15T09:00:00Z'
        '/api/action-history?page=first'             || 'page'       | 'must be a whole number'
    }

    def "the filter lists answer the users and the actions the trail holds"() {
        when:
        def users = call('/api/action-history/users')
        def actions = call('/api/action-history/actions')

        then:
        1 * history.users() >> ['Ann Lee', 'Mateusz Matan', 'system:internal']
        1 * history.actions() >> [CALLBACK_INTERNAL, CHANGE_CREATED, PRODUCT_CREATED]
        parse(users.contentAsString) == ['Ann Lee', 'Mateusz Matan', 'system:internal']
        parse(actions.contentAsString) == ['CALLBACK_INTERNAL', 'CHANGE_CREATED', 'PRODUCT_CREATED']
    }

    private def call(String path) {
        mvc.perform(get(URI.create(path))).andReturn().response
    }
}
