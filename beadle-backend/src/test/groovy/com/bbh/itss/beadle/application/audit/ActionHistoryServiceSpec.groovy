package com.bbh.itss.beadle.application.audit

import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery
import com.bbh.itss.beadle.domain.audit.ActionObject
import com.bbh.itss.beadle.domain.audit.ActionRecord
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ObjectType.BUSINESS_SERVICE
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT

class ActionHistoryServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'

    ActionHistoryPort history = Mock()
    ObjectKeyPort keys = Mock()
    SignedInUserPort users = Mock()
    def service = new ActionHistoryService(history, keys, users)

    def "a row is written by the signed-in user under the correlation id, with the key of an object known by id"() {
        given:
        users.name() >> 'Łukasz Wójcik'
        keys.keyOf(PRODUCT, 6) >> Optional.of('CERT')
        def row = ActionRecord.builder().at(NOW).action(CHANGE_CREATED).details('{"request":{"fixVersion":""}}')
                .outcome(FAILURE).message('fixVersion must not be blank')

        when:
        CallContext.correlatedBy(CORRELATION).withCloseable { service.record(row, ActionObject.of(PRODUCT, 6)) }

        then:
        1 * history.record(ActionRecord.builder().at(NOW).user('Łukasz Wójcik').action(CHANGE_CREATED)
                .objectType(PRODUCT).objectId(6).objectKey('CERT').details('{"request":{"fixVersion":""}}')
                .outcome(FAILURE).message('fixVersion must not be blank').correlationId(CORRELATION).build())
    }

    def "a row of a callback is filed under the system it acts for, and a known key is not looked up"() {
        when:
        CallContext.actingAs('system:protech').withCloseable {
            service.record(ActionRecord.builder().action(CALLBACK_PROTECH), ActionObject.keyed(CHANGE, 'CHG0031001'))
        }

        then:
        0 * users._
        0 * keys._
        1 * history.record({ ActionRecord row ->
            row.user() == 'system:protech' && row.objectKey() == 'CHG0031001' && row.correlationId() == null
        })
    }

    def "a user that is blank or cannot be read is recorded as unknown"() {
        given:
        users.name() >> { name() }

        when:
        service.record(ActionRecord.builder().action(BUSINESS_SERVICE_CREATED), ActionObject.of(BUSINESS_SERVICE, null))

        then:
        1 * history.record({ ActionRecord row -> row.user() == 'unknown' && row.objectType() == BUSINESS_SERVICE })

        where:
        name << [{ ' ' }, { null }, { throw new IllegalStateException('no security context') }]
    }

    def "the audit trail is read through its port, with the filter lists"() {
        given:
        def query = ActionHistoryQuery.of('Ann Lee', CHANGE_CREATED, null, 'CHG', null, null, null, 2, 20)
        def page = new ActionHistoryPage([], 2, 20, 41)

        when:
        def found = service.find(query)
        def users = service.users()
        def actions = service.actions()

        then:
        found == page
        users == ['Ann Lee', 'Mateusz Matan']
        actions == [CHANGE_CREATED, PRODUCT_CREATED]
        1 * history.find(query) >> page
        1 * history.users() >> ['Ann Lee', 'Mateusz Matan']
        1 * history.actions() >> [CHANGE_CREATED, PRODUCT_CREATED]
    }

    def "a query without paging asks for the first 50 rows"() {
        expect:
        with(ActionHistoryQuery.of('  ', null, null, ' CHG0031001 ', null, null, null, null, null)) {
            page() == 0
            size() == ActionHistoryQuery.DEFAULT_SIZE
            user() == null
            objectKey() == 'CHG0031001'
        }
    }
}
