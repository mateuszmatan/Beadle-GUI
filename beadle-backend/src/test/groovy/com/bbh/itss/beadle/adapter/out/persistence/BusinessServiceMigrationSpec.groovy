package com.bbh.itss.beadle.adapter.out.persistence

import liquibase.Contexts
import liquibase.LabelExpression
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:business-service-migration;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class BusinessServiceMigrationSpec extends MigrationSpecification {

    def "003 turns the departments of an existing database into business services, keeping their data, and rolls back"() {
        given: 'a database that ran 001 and 002 before 003 existed'
        liquibase.update(liquibase.listUnrunChangeSets(new Contexts(), new LabelExpression())
                .findIndexOf { it.id.startsWith('003-') }, '')
        jdbc.update('''INSERT INTO BEADLE_PRODUCT (CODE, NAME, DEPARTMENT_ID, CREATED_AT, UPDATED_AT)
                VALUES ('DOCSENSE', 'DocSense', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        audit('DEPARTMENT_CREATED', 'DEPARTMENT')

        when:
        liquibase.update('')

        then:
        !('BEADLE_DEPARTMENT' in tables())
        jdbc.queryForList('SELECT NAME FROM BEADLE_BUSINESS_SERVICE ORDER BY ID', String) ==
                ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']
        jdbc.queryForObject("SELECT BUSINESS_SERVICE_ID FROM BEADLE_PRODUCT WHERE CODE = 'DOCSENSE'", Long) == 1
        columns('BEADLE_PRODUCTION_CHANGE').containsAll(['BUSINESS_SERVICE_ID', 'BUSINESS_SERVICE_NAME'])
        audit('BUSINESS_SERVICE_CREATED', 'BUSINESS_SERVICE') == 1

        when:
        rollBackSince('003-')

        then:
        jdbc.queryForObject("SELECT DEPARTMENT_ID FROM BEADLE_PRODUCT WHERE CODE = 'DOCSENSE'", Long) == 1
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_DEPARTMENT', Integer) == 5
        jdbc.queryForList('SELECT ACTION FROM BEADLE_ACTION_HISTORY', String) == ['DEPARTMENT_CREATED'] * 2
    }

    private int audit(String action, String objectType) {
        jdbc.update('''INSERT INTO BEADLE_ACTION_HISTORY (AT, USER_NAME, ACTION, OBJECT_TYPE, OBJECT_KEY, OUTCOME,
                DURATION_MS) VALUES (CURRENT_TIMESTAMP, 'Mateusz Matan', ?, ?, 'Treasury', 'SUCCESS', 5)''',
                action, objectType)
    }
}
