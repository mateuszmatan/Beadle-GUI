package com.bbh.itss.dso.portal.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.transaction.annotation.Transactional

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle-tables;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
class BeadleTablesMigrationSpec extends MigrationSpecification {

    static final List<String> GONE = ['DSO_GLOBAL_SETTINGS', 'DSO_GLOBAL_SEVERITY_LIMIT', 'DSO_METRIC_POINT', 'DSO_PIPELINE',
                                      'DSO_PIPELINE_KEY', 'DSO_SERVICE', 'DSO_SERVICE_NEXUS_IQ_APP',
                                      'DSO_SERVICE_OPENSHIFT_TARGET', 'DSO_SERVICE_SSH_TARGET', 'DSO_SERVICE_TEMPLATE',
                                      'DSO_SERVICE_TEST_JOB', 'DSO_UCD_APPLICATION', 'DSO_UCD_COMPONENT']

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "Beadle's changelog keeps the department, product and change tables and drops the portal's tables"() {
        when:
        liquibase.update('')

        then:
        tables().containsAll(['DSO_CHANGE_PROFILE', 'DSO_DEPARTMENT', 'DSO_PRODUCT', 'DSO_PRODUCTION_CHANGE',
                              'DSO_PRODUCTION_CHANGE_TASK'])
        tables().intersect(GONE) == []
        jdbc.queryForList('SELECT NAME FROM DSO_DEPARTMENT ORDER BY ID', String) ==
                ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']
        columns('DSO_PRODUCT').sort() == ['CODE', 'CONTACT_EMAIL', 'CREATED_AT', 'DEPARTMENT_ID', 'ID', 'NAME', 'OWNER_TEAM',
                                          'UPDATED_AT', 'VERSION']
    }
}
