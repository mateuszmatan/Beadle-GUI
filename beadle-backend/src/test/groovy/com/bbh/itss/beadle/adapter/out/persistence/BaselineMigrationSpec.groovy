package com.bbh.itss.beadle.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle-baseline;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class BaselineMigrationSpec extends MigrationSpecification {

    static final List<String> TABLES = ['BEADLE_BUSINESS_SERVICE', 'BEADLE_CHANGE_PROFILE',
                                        'BEADLE_CHANGE_PROFILE_PRIVILEGED_USER', 'BEADLE_CHANGE_PROFILE_TASK',
                                        'BEADLE_PRODUCT', 'BEADLE_PRODUCTION_CHANGE',
                                        'BEADLE_PRODUCTION_CHANGE_APPROVAL',
                                        'BEADLE_PRODUCTION_CHANGE_PRIVILEGED_USER', 'BEADLE_PRODUCTION_CHANGE_STAGE',
                                        'BEADLE_PRODUCTION_CHANGE_TASK']

    /** The baseline alone; ActionHistoryMigrationSpec runs the whole changelog, with 002 on top of it. */
    @Override
    protected String changelogFile() {
        'db/changelog/oracle/001-baseline.sql'
    }

    def "the baseline creates Beadle's tables and the BBH business services"() {
        when:
        liquibase.update('')

        then:
        beadleTables() == TABLES
        jdbc.queryForList('SELECT NAME FROM BEADLE_BUSINESS_SERVICE ORDER BY ID', String) ==
                ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']
        columns('BEADLE_PRODUCT').sort() == ['BUSINESS_SERVICE_ID', 'CODE', 'CONTACT_EMAIL', 'CREATED_AT', 'ID', 'NAME',
                                             'OWNER_TEAM', 'UPDATED_AT', 'VERSION']
    }

    def "the baseline rolls back to an empty schema"() {
        given:
        liquibase.update('')

        when:
        rollBackSince('001-')

        then:
        beadleTables() == []
    }

    private List<String> beadleTables() {
        tables().findAll { it.startsWith('BEADLE_') }.sort()
    }
}
