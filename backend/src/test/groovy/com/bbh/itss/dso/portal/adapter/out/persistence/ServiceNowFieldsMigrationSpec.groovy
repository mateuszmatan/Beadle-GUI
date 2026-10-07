package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Planning
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Specification

import javax.sql.DataSource

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.FIRST_USE_PLAN
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.NORMAL
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.VALIDATION_PLAN
import static com.bbh.itss.dso.portal.support.ChangeFixtures.at
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:servicenow-fields;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter])
class ServiceNowFieldsMigrationSpec extends Specification {

    static final List<String> DROPPED = ['RISK', 'IMPACT', 'RISK_ASSESSMENT', 'APPROVERS', 'TEST_PLAN']

    @Autowired
    DataSource dataSource

    @Autowired
    JdbcTemplate jdbc

    @Autowired
    PlatformTransactionManager transactionManager

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    Liquibase liquibase

    def setup() {
        System.setProperty('liquibase.analytics.enabled', 'false')
        liquibase = new Liquibase('db/changelog/db.changelog-master.yaml', new ClassLoaderResourceAccessor(),
                DatabaseFactory.instance.findCorrectDatabaseImplementation(new JdbcConnection(dataSource.connection)))
    }

    def cleanup() {
        liquibase.close()
    }

    def "profiles and changes stored before the ServiceNow fields keep their data, roll back and migrate again"() {
        given:
        liquibase.update('')
        liquibase.rollback(executedSince('014-'), '')

        expect:
        columns('DSO_CHANGE_PROFILE').containsAll(DROPPED)
        columns('DSO_PRODUCTION_CHANGE').containsAll(DROPPED + ['PLANNED_START', 'PLANNED_END'])
        !columns('DSO_PRODUCTION_CHANGE').any { it in ['FIX_VERSION', 'FIRST_USAGE', 'TEST_SUMMARY', 'L1_MANAGER'] }
        jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE '%PRIVILEGED_USER'",
                Integer) == 0

        when:
        long productId = storedAsIn012()
        liquibase.update('')
        def profile = profiles.find(productId).get()
        def change = new TransactionTemplate(transactionManager).execute { changes.findAll().first() }

        then:
        profile.version() == 2
        profile.template() == ChangeTemplate.builder().jiraProjectKey('CERT')
                .assignmentGroup('Technology Architecture').category('Software').type(NORMAL)
                .configurationItem('CertScanner').description('Watches TLS certificates.')
                .approvers(new Approvers('Olivia Bennett', 'James Carter', 'Emma Brooks')).downtime(false)
                .timing(Timing.SUGGESTED)
                .planning(new Planning('Pipeline tests passed on QC.', 'Deploy the services.', VALIDATION_PLAN,
                        'Redeploy the previous release.', FIRST_USE_PLAN))
                .privilegedAccess(PrivilegedAccess.NONE).riskAssessment(impact('Low')).build()
        change.number() == 'CHG0030001'
        change.fixVersion() == 'Not recorded'
        change.schedule() == new ChangeSchedule(at('2026-03-02T06:00:00Z'), at('2026-03-02T08:00:00Z'),
                at('2026-03-02T08:00:00Z'), at('2026-03-02T08:00:00Z'), at('2026-03-02T08:00:00Z'))
        change.template().approvers() == new Approvers('Ann Lee', null, null)
        change.template().riskAssessment() == impact('Medium')
        change.template().planning() == new Planning('Tested.', 'Deploy.', 'Not recorded', 'Back out.',
                'Not recorded')
        change.template().description() == 'About CertScanner.'
        [change.epicKeys(), change.storyKeys()] == [['CERT-1'], ['CERT-2', 'CERT-3']]
        change.tasks()*.number() == ['CTASK0040001']
        DROPPED.every { !(it in columns('DSO_CHANGE_PROFILE')) && !(it in columns('DSO_PRODUCTION_CHANGE')) }
        ['FIX_VERSION', 'VALIDATION_START', 'VALIDATION_END', 'FIRST_USAGE', 'VALIDATION_PLAN', 'FIRST_USE_PLAN',
         'TIMING_INSTALLATION_START', 'DOWNTIME'].every { !nullable('DSO_PRODUCTION_CHANGE', it) }
        ['VALIDATION_PLAN', 'FIRST_USE_PLAN', 'TIMING_INSTALLATION_HOURS', 'PRIVILEGED_ACCESS_REQUIRED'].every {
            !nullable('DSO_CHANGE_PROFILE', it)
        }

        when:
        def changed = profiles.save(profile.change(2L, template(privilegedAccess: privileged(2))))

        then:
        changed.version() == 3
        profiles.find(productId).get().template() == template(privilegedAccess: privileged(2))
    }

    private long storedAsIn012() {
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES ('CERTSCANNER', 'CertScanner', 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'CERTSCANNER'", Long)
        jdbc.update('''INSERT INTO DSO_CHANGE_PROFILE (PRODUCT_ID, JIRA_PROJECT_KEY, CONFIGURATION_ITEM,
                ASSIGNMENT_GROUP, CHANGE_TYPE, CATEGORY, RISK, IMPACT, RISK_ASSESSMENT, APPROVERS, DESCRIPTION,
                IMPLEMENTATION_PLAN, BACKOUT_PLAN, TEST_PLAN, CREATED_AT, UPDATED_AT, VERSION)
                VALUES (?, 'CERT', 'CertScanner', 'Technology Architecture', 'NORMAL', 'Software', 'MODERATE', 'LOW',
                'Tested on QC.', ?, 'Watches TLS certificates.', 'Deploy the services.',
                'Redeploy the previous release.', 'Pipeline tests passed on QC.', CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP, 2)''', productId, 'Olivia Bennett\nJames Carter\nEmma Brooks')
        jdbc.update('''INSERT INTO DSO_PRODUCTION_CHANGE (CHANGE_NUMBER, PRODUCT_ID, PRODUCT_CODE, PRODUCT_NAME,
                DEPARTMENT_NAME, PLANNED_START, PLANNED_END, SHORT_DESCRIPTION, DESCRIPTION, JIRA_PROJECT_KEY,
                CONFIGURATION_ITEM, ASSIGNMENT_GROUP, CHANGE_TYPE, CATEGORY, RISK, IMPACT, RISK_ASSESSMENT,
                APPROVERS, PRODUCT_DESCRIPTION, IMPLEMENTATION_PLAN, BACKOUT_PLAN, TEST_PLAN, EPIC_KEYS,
                STORY_KEYS, URL, CREATED_AT, UPDATED_AT, VERSION)
                VALUES ('CHG0030001', ?, 'CERTSCANNER', 'CertScanner', 'Corporate Technology',
                TIMESTAMP '2026-03-02 06:00:00', TIMESTAMP '2026-03-02 08:00:00', 'CertScanner release',
                'Production release of CertScanner.', 'CERT', 'CertScanner', 'Technology Architecture', 'NORMAL',
                'Software', 'LOW', 'MEDIUM', 'Routine.', 'Ann Lee', 'About CertScanner.', 'Deploy.', 'Back out.',
                'Tested.', 'CERT-1', ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)''', productId,
                'CERT-2\nCERT-3')
        jdbc.update('''INSERT INTO DSO_PRODUCTION_CHANGE_TASK (CHANGE_ID, TASK_ORDER, TASK_NUMBER, SERVICE_NAME,
                SHORT_DESCRIPTION, DESCRIPTION) SELECT ID, 0, 'CTASK0040001', 'gui', 'Deploy gui', 'Deploy gui.'
                FROM DSO_PRODUCTION_CHANGE WHERE CHANGE_NUMBER = ?''', 'CHG0030001')
        productId
    }

    private int executedSince(String id) {
        jdbc.queryForObject('''SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ORDEREXECUTED >=
                (SELECT MIN(ORDEREXECUTED) FROM DATABASECHANGELOG WHERE ID LIKE ?)''', Integer, id + '%')
    }

    private List<String> columns(String table) {
        jdbc.queryForList('SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?', String, table)
    }

    private boolean nullable(String table, String column) {
        jdbc.queryForObject('SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?',
                String, table, column) == 'YES'
    }

    private static RiskAssessment impact(String businessImpact) {
        RiskAssessment.builder().businessImpact(businessImpact).build()
    }
}
