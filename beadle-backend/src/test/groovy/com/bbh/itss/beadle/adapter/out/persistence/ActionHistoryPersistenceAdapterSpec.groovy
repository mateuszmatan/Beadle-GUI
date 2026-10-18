package com.bbh.itss.beadle.adapter.out.persistence

import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery
import com.bbh.itss.beadle.domain.audit.ActionRecord
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.domain.audit.Action.BUSINESS_SERVICE_RENAMED
import static com.bbh.itss.beadle.domain.audit.Action.CALLBACK_INTERNAL
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_EDITED
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_RAISED
import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_TASKS_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED
import static com.bbh.itss.beadle.domain.audit.Action.SECURE_CODING_TICKET_CREATED
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.FAILURE
import static com.bbh.itss.beadle.domain.audit.ActionOutcome.SUCCESS
import static com.bbh.itss.beadle.domain.audit.ObjectType.BUSINESS_SERVICE
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE_PROFILE
import static com.bbh.itss.beadle.domain.audit.ObjectType.PRODUCT
import static com.bbh.itss.beadle.domain.audit.ObjectType.TICKET
import static com.bbh.itss.beadle.domain.audit.ObjectType.USER
import static com.bbh.itss.beadle.support.ChangeFixtures.raised
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:action-history;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([ActionHistoryPersistenceAdapter, ObjectKeyAdapter, ProductionChangePersistenceAdapter])
class ActionHistoryPersistenceAdapterSpec extends Specification {

    static final String CORRELATION_ID = '4f1c2a8e-7b3d-4e5f-9a6b-1c2d3e4f5a6b'

    @Autowired
    ActionHistoryPersistenceAdapter adapter

    @Autowired
    ObjectKeyAdapter keys

    @Autowired
    ProductionChangePersistenceAdapter changes

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    def "a row is inserted with every column, its details trimmed to 32 KB, and read back as it was written"() {
        given:
        def details = '{"command":{"name":"' + ('é' * 20_000) + '"}}'

        when:
        def stored = adapter.record(row(at: '2026-10-15T09:00:00.123456Z', details: details,
                outcome: FAILURE, message: 'Beadle-ProTech did not answer'))
        entities.flush()
        entities.clear()

        then:
        stored.id() != null
        jdbc.queryForMap('''SELECT USER_NAME, ACTION, OBJECT_TYPE, OBJECT_ID, OBJECT_KEY, OUTCOME, MESSAGE,
                CORRELATION_ID, DURATION_MS FROM BEADLE_ACTION_HISTORY WHERE ID = ?''', stored.id()) == [
                USER_NAME: 'Mateusz Matan', ACTION: 'CHANGE_RAISED', OBJECT_TYPE: 'CHANGE', OBJECT_ID: 7L,
                OBJECT_KEY: 'CHG0031001', OUTCOME: 'FAILURE', MESSAGE: 'Beadle-ProTech did not answer',
                CORRELATION_ID: CORRELATION_ID, DURATION_MS: 120L]
        stored.details().startsWith('{"command":{"name":"éé')
        stored.details().endsWith('é...')
        stored.details().getBytes('UTF-8').length in (ActionRecord.DETAILS_MAX - 4)..ActionRecord.DETAILS_MAX
        def read = adapter.find(ActionHistoryQuery.of(null, null, null, null, null, null, null, null, null)).content()
        read == [stored]
        read[0].at() == Instant.parse('2026-10-15T09:00:00.123456Z')
    }

    def "rows come newest first, filtered by every criterion and paged with the total"() {
        given:
        def first = adapter.record(row(at: '2026-10-13T08:00:00Z', user: 'Ann Lee', action: PRODUCT_CREATED,
                objectType: PRODUCT, objectId: 1L, objectKey: 'CERTSCANNER'))
        def second = adapter.record(row(at: '2026-10-14T08:00:00Z', outcome: FAILURE, message: 'refused'))
        def third = adapter.record(row(at: '2026-10-14T08:00:00Z', objectKey: 'CHG0031002'))
        def fourth = adapter.record(row(at: '2026-10-15T08:00:00Z', user: 'system:internal',
                action: CALLBACK_INTERNAL, objectType: TICKET, objectId: null, objectKey: 'SCP-123'))
        def fifth = adapter.record(row(at: '2026-10-15T09:00:00Z', user: 'Ann Lee', action: BUSINESS_SERVICE_RENAMED,
                objectType: BUSINESS_SERVICE, objectId: 3L, objectKey: 'Corporate Technology'))

        expect:
        found() == [fifth, fourth, third, second, first]
        found(user: 'Ann Lee') == [fifth, first]
        found(action: CHANGE_RAISED) == [third, second]
        found(objectType: TICKET) == [fourth]
        found(objectKey: 'chg003') == [third, second]
        found(objectKey: 'CHG0031002') == [third]
        found(objectKey: 'corporate') == [fifth]
        found(objectKey: '%') == []
        found(objectKey: '_HG') == []
        found(outcome: FAILURE) == [second]
        found(from: '2026-10-14T08:00:00Z', to: '2026-10-15T08:00:00Z') == [third, second]
        found(user: 'Ann Lee', action: PRODUCT_CREATED, outcome: SUCCESS) == [first]

        and:
        with(adapter.find(ActionHistoryQuery.of(null, null, null, null, null, null, null, 1, 2))) {
            content() == [third, second]
            page() == 1
            size() == 2
            total() == 5
        }
        adapter.find(ActionHistoryQuery.of(null, null, null, null, null, null, null, 3, 2)).content() == []
    }

    def "the object-key filter also finds the row of the action that created a CTASK or a ticket in another system"() {
        given:
        def tasks = adapter.record(row(action: CHANGE_TASKS_CREATED, details: '{"id":7,"command":{"tasks":[' +
                '{"details":{"shortDescription":"Mention CTASK0099999"}}]},"result":{"created":["CTASK0041003",' +
                '"CTASK0041004"]}}'))
        def ticket = adapter.record(row(at: '2026-10-15T09:01:00Z', action: SECURE_CODING_TICKET_CREATED,
                details: '{"id":7,"command":{"version":3},"result":{"created":["SCP-123"]}}'))
        def mentioned = adapter.record(row(at: '2026-10-15T09:02:00Z', action: CHANGE_EDITED, objectKey: 'CHG0031002',
                details: '{"id":8,"command":{"shortDescription":"After CTASK0041003 and SCP-123"}}'))
        def raise = adapter.record(row(at: '2026-10-15T09:03:00Z', objectId: null, objectKey: null,
                outcome: FAILURE, details: '{"command":{"epicKeys":["SCP-9"]},"result":{"created":["CHG0031009"]}}'))

        expect:
        found(objectKey: 'CTASK0041004') == [tasks]
        found(objectKey: 'ctask004100') == [tasks]
        found(objectKey: 'CTASK0099999') == []
        found(objectKey: 'SCP-123') == [ticket]
        found(objectKey: 'SCP-9') == []
        found(objectKey: 'CHG0031009') == [raise]
        found(objectKey: 'CHG0031002') == [mentioned]
        found(objectKey: 'CHG003100') == [raise, mentioned, ticket, tasks]
        found(objectKey: '%') == []
    }

    def "the filter lists hold every user and every action of the trail once"() {
        given:
        adapter.record(row(user: 'Mateusz Matan'))
        adapter.record(row(user: 'Ann Lee', action: PRODUCT_CREATED))
        adapter.record(row(user: 'Mateusz Matan', action: PRODUCT_CREATED))
        adapter.record(row(user: 'system:protech', action: BUSINESS_SERVICE_RENAMED))

        expect:
        adapter.users() == ['Ann Lee', 'Mateusz Matan', 'system:protech']
        adapter.actions() == [BUSINESS_SERVICE_RENAMED, CHANGE_RAISED, PRODUCT_CREATED]
    }

    def "a stored row is never updated"() {
        given:
        def stored = adapter.record(row())
        entities.flush()
        entities.clear()

        when:
        def entity = entities.find(ActionHistoryEntity, stored.id())
        entity.@user = 'Someone Else'
        entity.@outcome = FAILURE
        entities.flush()
        entities.clear()

        then:
        jdbc.queryForMap('SELECT USER_NAME, OUTCOME FROM BEADLE_ACTION_HISTORY WHERE ID = ?', stored.id()) ==
                [USER_NAME: 'Mateusz Matan', OUTCOME: 'SUCCESS']
    }

    def "the repository of the audit trail can insert and read, but neither update nor delete"() {
        expect:
        ActionHistoryJpaRepository.interfaces as List == [org.springframework.data.repository.Repository]
        ActionHistoryJpaRepository.methods*.name.toSet() == ['save', 'findAll', 'findUserNames', 'findActions'] as Set
    }

    def "a query asks for at most 200 rows from a range that is not empty"() {
        when:
        ActionHistoryQuery.of(null, null, null, null, null, Instant.parse(from), Instant.parse(to), page, size)

        then:
        def invalid = thrown(InvalidRequestException)
        invalid.problems()*.field() == fields

        where:
        page | size | from                   | to                     || fields
        -1   | 50   | '2026-10-14T00:00:00Z' | '2026-10-15T00:00:00Z' || ['page']
        0    | 201  | '2026-10-14T00:00:00Z' | '2026-10-15T00:00:00Z' || ['size']
        0    | 0    | '2026-10-15T00:00:00Z' | '2026-10-15T00:00:00Z' || ['size', 'from']
    }

    def "the key of a change, a product, its change template and a business service is read by id"() {
        given:
        jdbc.update('''INSERT INTO BEADLE_PRODUCT (CODE, NAME, BUSINESS_SERVICE_ID, CREATED_AT, UPDATED_AT)
                VALUES ('AUDITED', 'Audited Product', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM BEADLE_PRODUCT WHERE CODE = 'AUDITED'", Long)
        long changeId = changes.save(raised(id: null, version: null, number: 'CHG0077001', productId: productId))
                .id()

        expect:
        keys.keyOf(CHANGE, changeId) == Optional.of('CHG0077001')
        keys.keyOf(PRODUCT, productId) == Optional.of('AUDITED')
        keys.keyOf(CHANGE_PROFILE, productId) == Optional.of('AUDITED')
        keys.keyOf(BUSINESS_SERVICE, 3L) == Optional.of('Corporate Technology')
        keys.keyOf(BUSINESS_SERVICE, 9999L) == Optional.empty()
        keys.keyOf(TICKET, 1L) == Optional.empty()
        keys.keyOf(USER, 1L) == Optional.empty()
    }

    private List<ActionRecord> found(Map filters = [:]) {
        adapter.find(ActionHistoryQuery.of(filters.user as String, filters.action, filters.objectType,
                filters.objectKey as String, filters.outcome, filters.from ? Instant.parse(filters.from) : null,
                filters.to ? Instant.parse(filters.to) : null, 0, 50)).content()
    }

    private static ActionRecord row(Map changes = [:]) {
        Map values = [at: '2026-10-15T09:00:00Z', user: 'Mateusz Matan', action: CHANGE_RAISED, objectType: CHANGE,
                      objectId: 7L, objectKey: 'CHG0031001', details: '{"command":{}}', outcome: SUCCESS,
                      message: null, correlationId: CORRELATION_ID, durationMs: 120L] + changes
        ActionRecord.builder().at(Instant.parse(values.at)).user(values.user).action(values.action)
                .objectType(values.objectType).objectId(values.objectId).objectKey(values.objectKey)
                .details(values.details).outcome(values.outcome).message(values.message)
                .correlationId(values.correlationId).durationMs(values.durationMs).build()
    }
}
