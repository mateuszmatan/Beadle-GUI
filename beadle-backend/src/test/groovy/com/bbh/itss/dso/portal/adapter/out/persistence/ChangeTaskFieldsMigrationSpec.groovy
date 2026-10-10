package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.domain.change.ChangeTask.NOT_YET_REQUESTED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.releaseTask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.task
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:change-task-fields;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter])
class ChangeTaskFieldsMigrationSpec extends MigrationSpecification {

    static final List<String> PROFILE_TASK_FIELDS = ['ASSIGNMENT_GROUP', 'ASSIGNED_TO', 'CONFIGURATION_ITEM',
                                                     'PLATFORM', 'APPLICATION', 'PACKAGES', 'BACKOUT_PACKAGES',
                                                     'IMPORTANCE', 'ADDITIONAL_COMMENTS']
    static final List<String> CHANGE_TASK_FIELDS = PROFILE_TASK_FIELDS + ['TASK_START', 'APPROVAL']

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    def "022 gives the tasks the group and the affected CI of their template or change and rolls back"() {
        given:
        liquibase.update('')
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES ('PAYHUB', 'PayHub', 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'PAYHUB'", Long)
        def payHub = template(assignmentGroup: 'Payments Engineering', configurationItem: 'PayHub')
        profiles.save(ChangeProfile.create(productId, payHub, tasks(2)))
        ProductionChange change = inTransaction {
            changes.save(raised(id: null, version: null, productId: productId,
                    template: payHub.releasedAs(FIX_VERSION), tasks: [task(1).approved('Approved'),
                                                                      releaseTask().numbered('CTASK0041003')]))
        }

        when:
        rollBackSince('022-')

        then:
        PROFILE_TASK_FIELDS.every { !(it in columns('DSO_CHANGE_PROFILE_TASK')) }
        CHANGE_TASK_FIELDS.every { !(it in columns('DSO_PRODUCTION_CHANGE_TASK')) }

        when:
        liquibase.update('')
        def migrated = inTransaction { changes.load(change.id()).get() }

        then:
        profiles.find(productId).get().tasks() == tasks(2).collect {
            it.toBuilder().assignmentGroup('Payments Engineering').configurationItem('PayHub').build()
        }
        migrated.tasks()*.details()*.assignmentGroup() == ['Payments Engineering'] * 2
        migrated.tasks()*.details()*.configurationItem() == ['PayHub'] * 2
        migrated.tasks()*.details()*.shortDescription() == ['Task 1 of the CertScanner release',
                                                            'Task 3 of the CertScanner release']
        migrated.tasks()*.approval() == [NOT_YET_REQUESTED] * 2
        migrated.tasks()*.start() == [null, null]

        when:
        jdbc.update("UPDATE DSO_PRODUCTION_CHANGE_TASK SET STATE = 'DONE'")

        then:
        thrown(DataIntegrityViolationException)
    }
}
