package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.RiskAssessment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.BUSINESS_CRITICAL
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.EMERGENCY
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Type.STANDARD
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:beadle-wizard;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter])
class BeadleWizardMigrationSpec extends MigrationSpecification {

    static final List<String> WIZARD_COLUMNS = ['REQUESTED_FOR', 'REQUESTED_BY', 'REQUEST_DEPARTMENT', 'ASSIGNED_TO',
                                                'DIRECT_BUSINESS_SERVICE', 'USERS_AFFECTED', 'SECURE_CODING_TICKET']
    static final List<String> CHANGE_COLUMNS = WIZARD_COLUMNS + ['OPENED_BY', 'DOWNTIME_START', 'DOWNTIME_END']
    static final String OLD_VALUES = '''CHANGE_TYPE = 'NORMAL', CATEGORY = ?, RISK_BBH_WORKGROUPS = ?,
            RISK_BBH_USERS = ?, RISK_BBH_APPLICATIONS = ?, RISK_CLIENTS = ?, RISK_CLIENTS_OUTSIDE_BBH = ?,
            RISK_CHANGE_COMPLEXITY = ?, RISK_VALIDATION_COMPLEXITY = ?, RISK_BACKOUT_TESTING = ?,
            RISK_PLATFORM_STATUS = ?, RISK_BUSINESS_IMPACT = ?'''
    static final String RISK_VALUES = '''SELECT CHANGE_TYPE, CATEGORY, RISK_BBH_WORKGROUPS, RISK_BBH_USERS,
            RISK_BBH_APPLICATIONS, RISK_CLIENTS, RISK_CLIENTS_OUTSIDE_BBH, RISK_CHANGE_COMPLEXITY,
            RISK_VALIDATION_COMPLEXITY, RISK_BACKOUT_TESTING, RISK_PLATFORM_STATUS, RISK_BUSINESS_IMPACT FROM '''

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    def "a template written before the wizard as #category, #numbers and #texts reads as #listed and #answers"() {
        given:
        liquibase.update('')
        def (long productId, long changeId) = stored(template(), raised().schedule())
        rollBackSince('017-')
        def values = [category] + numbers + texts
        jdbc.update("UPDATE DSO_CHANGE_PROFILE SET $OLD_VALUES WHERE PRODUCT_ID = ?", *values, productId)
        jdbc.update("UPDATE DSO_PRODUCTION_CHANGE SET DOWNTIME = ?, $OLD_VALUES WHERE ID = ?", downtime, *values,
                changeId)

        when:
        liquibase.update('')
        def profile = profiles.find(productId).get().template()
        def change = inTransaction { changes.load(changeId).get() }

        then:
        [profile, change.template()].every {
            it.type() == STANDARD && it.category() == listed && it.riskAssessment() == answers
        }
        change.template().downtime() == (downtime == 1)
        [change.schedule().downtimeStart(), change.schedule().downtimeEnd()] == (downtime == 1
                ? [change.schedule().installationStart(), change.schedule().installationEnd()] : [null, null])
        change.openedBy() == null
        [change.template().requestedFor(), change.template().department(), profile.secureCodingTicket()] ==
                [null, null, null]

        where:
        category       | numbers                 | texts                                                                   | downtime || listed        | answers
        'Software'     | [0, 4, 0, 9, 0]         | ['Low', 'low', 'Less than 30 minutes', 'Existing platform', 'Low']      | 1        || 'Application' | new RiskAssessment('Single', 'Simple', 'Less than 5', 'Simple', 'Single', 'Less than 30 minutes', 'No clients', 'Existing', 'Low')
        ' apps '       | [1, 5, 1, 0, 1]         | ['Medium', 'Moderate', 'Tested on QC, 15 minutes', 'New platform', 'none'] | 0     || 'Application' | new RiskAssessment('Single', 'Moderate', '5-25', 'Moderate', 'Single', null, 'Single', 'New', 'None')
        'DATABASE'     | [2, 25, 2, 1, 2]        | ['High', 'Very', 'unable to test', 'Being decommissioned', 'Medium']    | 1        || 'Database'    | new RiskAssessment('2-3', 'Very', '5-25', 'Very', 'Two', 'Unable to test', 'More than one but not all', 'Decommissioned', 'Medium')
        'system software' | [3, 26, 3, 2, 7]     | ['Hard', null, '30 mins - 2 hours', 'Unknown', 'Severe']                | 0        || 'System Software' | new RiskAssessment('2-3', null, '26-250', null, 'More than 2', '30 mins - 2 hours', 'More than one but not all', null, null)
        'Cloud'        | [4, 250, 9, null, null] | ['VERY', 'medium', 'Greater than 2 hours', 'existing', 'HIGH']          | 0        || 'Other'       | new RiskAssessment('More than 3', 'Very', '26-250', 'Moderate', 'More than 2', 'Greater than 2 hours', null, 'Existing', 'High')
        'Other'        | [null, 251, null, 3, null] | [null, null, null, null, null]                                       | 1        || 'Other'       | new RiskAssessment(null, null, 'All users', null, null, null, null, null, null)
    }

    def "the wizard fields roll back to the numbers and the type they came from and migrate again"() {
        given:
        liquibase.update('')
        def assessed = template(type: BUSINESS_CRITICAL, category: 'Hardware', requestedFor: 'Ann Lee',
                requestedBy: 'Jane Smith', department: 'Custody', assignedTo: 'Grace Turner',
                directBusinessService: 'Certificates', usersAffected: 'Operators', secureCodingTicket: 'APPSEC-1',
                downtime: true, riskAssessment: new RiskAssessment('More than 3', 'Very', '26-250', 'Moderate', 'Two',
                '30 mins - 2 hours', 'All clients', 'New', 'Medium'))
        def (long productId, long changeId) = stored(assessed, schedule(downtimeStart: '2026-10-10T07:00:00Z',
                downtimeEnd: '2026-10-10T08:00:00Z'))
        profiles.save(ChangeProfile.create(productOf('LEDGER'), template(type: EMERGENCY), tasks(1)))

        when:
        rollBackSince('017-')

        then:
        !columns('DSO_CHANGE_PROFILE').any { it in WIZARD_COLUMNS }
        !columns('DSO_PRODUCTION_CHANGE').any { it in CHANGE_COLUMNS }
        ['DSO_CHANGE_PROFILE', 'DSO_PRODUCTION_CHANGE'].every { table ->
            jdbc.queryForMap(RISK_VALUES + table + ' WHERE PRODUCT_ID = ? AND CATEGORY = ?', productId, 'Hardware') ==
                    [CHANGE_TYPE: 'NORMAL', CATEGORY: 'Hardware', RISK_BBH_WORKGROUPS: 4, RISK_BBH_USERS: 26,
                     RISK_BBH_APPLICATIONS: 2, RISK_CLIENTS: null, RISK_CLIENTS_OUTSIDE_BBH: 3,
                     RISK_CHANGE_COMPLEXITY: 'High', RISK_VALIDATION_COMPLEXITY: 'Medium',
                     RISK_BACKOUT_TESTING: '30 mins - 2 hours', RISK_PLATFORM_STATUS: 'New',
                     RISK_BUSINESS_IMPACT: 'Medium']
        }
        jdbc.queryForList("SELECT CHANGE_TYPE FROM DSO_CHANGE_PROFILE WHERE CATEGORY <> 'Hardware'", String) ==
                ['EMERGENCY']

        when:
        liquibase.update('')
        def profile = profiles.find(productId).get().template()
        def change = inTransaction { changes.load(changeId).get() }
        def migrated = assessed.toBuilder().type(STANDARD).requestedFor(null).requestedBy(null).department(null)
                .assignedTo(null).directBusinessService(null).usersAffected(null).secureCodingTicket(null)
                .riskAssessment(assessed.riskAssessment().toBuilder().clientsOutsideBbh('More than one but not all')
                        .build()).build()

        then:
        profile == migrated
        change.template() == migrated.releasedAs(FIX_VERSION)
        change.openedBy() == null
        change.schedule() == schedule(downtimeStart: '2026-10-10T06:00:00Z', downtimeEnd: '2026-10-10T10:00:00Z')
        WIZARD_COLUMNS.every { nullable('DSO_CHANGE_PROFILE', it) }
        CHANGE_COLUMNS.every { nullable('DSO_PRODUCTION_CHANGE', it) }
        !columns('DSO_CHANGE_PROFILE').contains('RISK_CLIENTS')
    }

    private List<Long> stored(ChangeTemplate template, ChangeSchedule schedule) {
        jdbc.update('DELETE FROM DSO_PRODUCTION_CHANGE')
        jdbc.update('DELETE FROM DSO_PRODUCT')
        long productId = productOf('CERTSCANNER')
        profiles.save(ChangeProfile.create(productId, template, tasks(1)))
        ProductionChange change = inTransaction {
            changes.save(raised(id: null, version: null, productId: productId, openedBy: 'Mateusz Matan',
                    schedule: schedule, template: template.releasedAs(FIX_VERSION)))
        }
        [productId, change.id()]
    }

    private long productOf(String code) {
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES (?, ?, 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''', code, code.capitalize())
        jdbc.queryForObject('SELECT ID FROM DSO_PRODUCT WHERE CODE = ?', Long, code)
    }
}
