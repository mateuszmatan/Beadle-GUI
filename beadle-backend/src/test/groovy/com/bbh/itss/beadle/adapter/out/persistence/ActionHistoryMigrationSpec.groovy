package com.bbh.itss.beadle.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:action-history-migration;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class ActionHistoryMigrationSpec extends MigrationSpecification {

    static final List<String> COLUMNS = ['ACTION', 'AT', 'CORRELATION_ID', 'DETAILS', 'DURATION_MS', 'ID', 'MESSAGE',
                                         'OBJECT_ID', 'OBJECT_KEY', 'OBJECT_TYPE', 'OUTCOME', 'USER_NAME']
    static final List<String> INDEXES = ['IX_BEADLE_ACTION_HISTORY_ACTION', 'IX_BEADLE_ACTION_HISTORY_AT',
                                         'IX_BEADLE_ACTION_HISTORY_OBJECT_KEY', 'IX_BEADLE_ACTION_HISTORY_USER']

    def "002 adds the audit trail on top of the baseline, holds only known actions and outcomes, and rolls back"() {
        when:
        liquibase.update('')

        then:
        columns('BEADLE_ACTION_HISTORY').sort() == COLUMNS
        ['AT', 'USER_NAME', 'ACTION', 'OUTCOME', 'DURATION_MS'].every { !nullable('BEADLE_ACTION_HISTORY', it) }
        ['OBJECT_TYPE', 'OBJECT_ID', 'OBJECT_KEY', 'DETAILS', 'MESSAGE', 'CORRELATION_ID']
                .every { nullable('BEADLE_ACTION_HISTORY', it) }
        indexes().containsAll(INDEXES)
        jdbc.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'BEADLE_ACTION_HISTORY'" +
                " AND COLUMN_NAME = 'DETAILS'", String) == 'CHARACTER LARGE OBJECT'

        when:
        int before = jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY', Integer)
        insert('CHANGE_RAISED', 'CHANGE', 'SUCCESS')
        insert('CALLBACK_INTERNAL', 'TICKET', 'FAILURE')

        then:
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_ACTION_HISTORY', Integer) == before + 2

        when:
        insert(action, objectType, outcome)

        then:
        thrown(DataIntegrityViolationException)

        where:
        action          | objectType | outcome
        'CHANGE_VIEWED' | 'CHANGE'   | 'SUCCESS'
        'CHANGE_RAISED' | 'INVOICE'  | 'SUCCESS'
        'CHANGE_RAISED' | 'CHANGE'   | 'UNKNOWN'
    }

    def "002 rolls back to the baseline"() {
        given:
        liquibase.update('')

        when:
        rollBackSince('002-')

        then:
        !('BEADLE_ACTION_HISTORY' in tables())
        'BEADLE_PRODUCTION_CHANGE_APPROVAL' in tables()

        when:
        liquibase.update('')

        then:
        'BEADLE_ACTION_HISTORY' in tables()
    }

    private void insert(String action, String objectType, String outcome) {
        jdbc.update('''INSERT INTO BEADLE_ACTION_HISTORY (AT, USER_NAME, ACTION, OBJECT_TYPE, OBJECT_KEY, DETAILS,
                OUTCOME, CORRELATION_ID, DURATION_MS) VALUES (CURRENT_TIMESTAMP, 'Mateusz Matan', ?, ?, 'CHG0031001',
                '{}', ?, '4f1c2a8e-7b3d-4e5f-9a6b-1c2d3e4f5a6b', 5)''', action, objectType, outcome)
    }

    private List<String> indexes() {
        jdbc.queryForList("SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'BEADLE_ACTION_HISTORY'",
                String)
    }
}
