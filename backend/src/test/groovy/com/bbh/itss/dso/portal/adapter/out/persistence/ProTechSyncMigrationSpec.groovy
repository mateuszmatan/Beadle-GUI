package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.TaskText
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.IMPLEMENTATION
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.APPLIED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.domain.change.TaskText.suggestedTasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.changeProduct
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:protech-sync;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter, ProductPersistenceAdapter])
class ProTechSyncMigrationSpec extends MigrationSpecification {

    static final List<String> SYNC_COLUMNS = ['DEPARTMENT_ID', 'STATE', 'SYNCED_AT', 'UPDATE_STATUS',
                                              'UPDATE_REQUESTED_AT', 'UPDATE_DEPARTMENT', 'UPDATE_FIELDS',
                                              'UPDATE_MESSAGE', 'UPDATE_CHECKED_AT', 'EDITED_VERSION']

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    @Autowired
    ProductPersistenceAdapter products

    def "Change templates get tasks from the services, changes get their department and draft stage, and both roll back"() {
        given:
        liquibase.update('')
        Product product = inTransaction {
            products.save(Product.create(details(code: 'CERTSCANNER', name: 'CertScanner', departmentId: 3L),
                    account(), [new ServiceDraft(null, 'gui', null, settings()),
                                new ServiceDraft(null, 'api', null, settings())], products))
        }
        jdbc.update("UPDATE DSO_SERVICE SET DISPLAY_ORDER = CASE NAME WHEN 'api' THEN 0 ELSE 1 END WHERE PRODUCT_ID = ?",
                product.id())
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES ('LEDGER', 'Ledger', 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long ledger = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'LEDGER'", Long)
        profiles.save(ChangeProfile.create(product.id(), template(), tasks(3)))
        profiles.save(ChangeProfile.create(ledger, template(jiraProjectKey: 'LED'), tasks(1)))
        def raised = inTransaction { changes.save(changeOf(product)) }
        def moved = inTransaction {
            changes.save(changeOf(product, 'CHG0031002', 'CTASK0041011', 'Fund Services'))
        }
        def renamed = inTransaction {
            changes.save(changeOf(product, 'CHG0031003', 'CTASK0041021', 'Former Technology'))
        }

        when:
        liquibase.rollback(executedSince('016-'), '')

        then:
        !tables().any { it in ['DSO_CHANGE_PROFILE_TASK', 'DSO_PRODUCTION_CHANGE_STAGE'] }
        !columns('DSO_PRODUCTION_CHANGE').any { it in SYNC_COLUMNS }
        !columns('DSO_PRODUCTION_CHANGE_TASK').contains('STATE')
        jdbc.queryForList('SELECT TASK_NUMBER, SERVICE_NAME FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?',
                raised.id()) == [[TASK_NUMBER: 'CTASK0041001', SERVICE_NAME: 'Not recorded']]
        !nullable('DSO_PRODUCTION_CHANGE_TASK', 'SERVICE_NAME')
        !nullable('DSO_PRODUCTION_CHANGE_TASK', 'TASK_NUMBER')

        when:
        liquibase.update('')
        def change = inTransaction { changes.load(raised.id()).get() }

        then:
        profiles.find(product.id()).get().tasks() == ['api', 'gui'].collect {
            new TaskText("Deploy $it of CertScanner to production",
                    "Deploy $it of CertScanner, then run its smoke tests and confirm the result in this task.")
        }
        profiles.find(ledger).get().tasks() == suggestedTasks('Ledger')
        change.departmentId() == 3L
        inTransaction { changes.load(moved.id()).get() }.departmentId() == 5L
        inTransaction { changes.load(renamed.id()).get() }.departmentId() == 3L
        change.editedVersion() == change.version()
        change.state() == DRAFT
        change.workflow() == [new WorkflowStep(DRAFT, RAISED)]
        change.syncedAt() == null
        change.update() == null
        change.tasks() == [new ChangeTask('CTASK0041001', 'Task 1 of the CertScanner release',
                'Step 1 of the CertScanner release.', OPEN)]
        !columns('DSO_PRODUCTION_CHANGE_TASK').contains('SERVICE_NAME')
        nullable('DSO_PRODUCTION_CHANGE_TASK', 'TASK_NUMBER')
        !nullable('DSO_PRODUCTION_CHANGE', 'STATE')
    }

    def "the Oracle task defaults run the SQL tested on H2, cut by bytes"() {
        given:
        def text = getClass().getResource('/db/changelog/oracle/016-beadle-protech-sync.sql').getText('UTF-8')
        def body = { String id -> (text =~ /(?s)--changeset dso-portal:$id dbms:\w+\n(.*?)--rollback/)[0][1] as String }

        expect:
        body('016-change-profile-task-defaults').count('SUBSTRB(') == 3
        body('016-change-profile-task-defaults').replace('SUBSTRB(', 'SUBSTR(') == body('016-change-profile-task-defaults-h2')
    }

    private static ProductionChange changeOf(Product product, String number = 'CHG0031001',
                                             String task = 'CTASK0041001', String department = 'Corporate Technology') {
        ProductionChange draft = ProductionChange.draft(changeProduct(id: product.id(), code: product.code(),
                name: product.name(), departmentName: department), 'Mateusz Matan', tasks(2), FIX_VERSION, schedule(),
                template(), [epic('CERT-1', 'Expiry alerts')], [], null, null).raisedAt(RAISED)
                .numbered(number, [task, task + '2'], null)
        draft.toBuilder().state(IMPLEMENTATION)
                .workflow([new WorkflowStep(DRAFT, RAISED), new WorkflowStep(BUSINESS_APPROVAL, RAISED.plusSeconds(120)),
                           new WorkflowStep(IMPLEMENTATION, RAISED.plusSeconds(600))])
                .tasks([draft.tasks()[0].in(WORK_IN_PROGRESS), draft.tasks()[1].numbered(null)])
                .update(new ChangeUpdate(APPLIED, RAISED.plusSeconds(300), 'Corporate Technology', [], null,
                        RAISED.plusSeconds(303)))
                .syncedAt(RAISED.plusSeconds(600)).build()
    }
}
