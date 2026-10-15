package com.bbh.itss.beadle.application.audit

import com.bbh.itss.beadle.application.CallContext
import com.bbh.itss.beadle.application.audit.port.in.RefusedRequest
import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort
import com.bbh.itss.beadle.application.audit.port.out.ObjectKeyPort
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.domain.audit.ActionObject
import com.bbh.itss.beadle.domain.audit.ActionRecord
import spock.lang.Specification

import java.time.Clock
import java.time.Instant

import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_PROTECH
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_RAISED
import static com.bbh.itss.beadle.domain.audit.Action.DEPARTMENT_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.REMINDER_SENT
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.audit.ObjectType.DEPARTMENT
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT
import static java.time.ZoneOffset.UTC

class RefusedRequestServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-15T09:00:00Z')
    static final String CORRELATION = '6f1c2a9e-3b7d-4c55-9a51-2f0e8d4b7c10'

    ActionHistoryPort history = Mock()
    ObjectKeyPort keys = Mock()
    SignedInUserPort users = Mock()
    def service = new RefusedRequestService(history, keys, users, Clock.fixed(NOW, UTC))

    def "a refused request is one FAILURE row of its action by the signed-in user, under the correlation id"() {
        given:
        users.name() >> 'Łukasz Wójcik'
        keys.keyOf(PRODUCT, 6) >> Optional.of('CERT')

        when:
        CallContext.correlatedBy(CORRELATION).withCloseable {
            service.refused(new RefusedRequest(CHANGE_RAISED, ActionObject.of(PRODUCT, 6),
                    '{"request":{"productId":6,"fixVersion":""}}', 'fixVersion must not be blank'))
        }

        then:
        1 * history.record(ActionRecord.builder().at(NOW).user('Łukasz Wójcik').action(CHANGE_RAISED)
                .objectType(PRODUCT).objectId(6).objectKey('CERT')
                .details('{"request":{"productId":6,"fixVersion":""}}').outcome(FAILURE)
                .message('fixVersion must not be blank').correlationId(CORRELATION).durationMs(0).build())
    }

    def "a refused callback is filed under the system it acts for, and a known key is not looked up"() {
        when:
        CallContext.actingAs('system:protech').withCloseable {
            service.refused(new RefusedRequest(CALLBACK_PROTECH, ActionObject.keyed(CHANGE, 'CHG0031001'), '{}',
                    'event must not be null'))
        }

        then:
        0 * users._
        0 * keys._
        1 * history.record({ ActionRecord row ->
            row.user() == 'system:protech' && row.objectKey() == 'CHG0031001' && row.outcome() == FAILURE &&
                    row.correlationId() == null
        })
    }

    def "a request that names no object is filed under the type of its action"() {
        given:
        users.name() >> name

        when:
        service.refused(new RefusedRequest(action, object, null, 'name must not be blank'))

        then:
        1 * history.record({ ActionRecord row ->
            row.user() == user && row.objectType() == type && row.objectId() == null && row.objectKey() == null
        })

        where:
        action             | object                             | name   || user      | type
        DEPARTMENT_CREATED | null                               | 'Ann'  || 'Ann'     | DEPARTMENT
        DEPARTMENT_CREATED | ActionObject.keyed(DEPARTMENT, null) | ' ' || 'unknown' | DEPARTMENT
        REMINDER_SENT      | ActionObject.of(CHANGE, null)      | null   || 'unknown' | CHANGE
    }

    def "a user that cannot be read is recorded as unknown"() {
        given:
        users.name() >> { throw new IllegalStateException('no security context') }

        when:
        service.refused(new RefusedRequest(DEPARTMENT_CREATED, null, null, 'name must not be blank'))

        then:
        1 * history.record({ ActionRecord row -> row.user() == 'unknown' })
    }
}
