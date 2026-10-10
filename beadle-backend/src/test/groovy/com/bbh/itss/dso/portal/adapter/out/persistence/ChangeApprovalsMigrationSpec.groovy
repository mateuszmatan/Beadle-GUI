package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ApprovalRef
import com.bbh.itss.dso.portal.domain.change.ChangeState
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.Reminder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L1
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.APPROVED
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.REQUESTED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CTASK_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeState.PRIMARY_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.SUPPORT_APPROVAL
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.task
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:change-approvals;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import(ProductionChangePersistenceAdapter)
class ChangeApprovalsMigrationSpec extends MigrationSpecification {

    static final List<String> TASK_COLUMNS = ['APPROVERS', 'REMINDER_SENT_AT', 'REMINDER_SENT_TO']

    @Autowired
    ProductionChangePersistenceAdapter changes

    @Override
    protected String changelogFile() {
        'db/changelog/db.changelog-master.yaml'
    }

    def "027 gives every change its four approvals from its state and its change tasks their approval states, and rolls back"() {
        given:
        liquibase.update('')
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, CREATED_AT, UPDATED_AT)
                VALUES ('CERTSCANNER', 'CertScanner', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'CERTSCANNER'", Long)
        Map<ChangeState, ProductionChange> stored = [DRAFT, PRIMARY_APPROVAL, CTASK_APPROVAL, CLOSED].withIndex()
                .collectEntries { state, index ->
                    [state, inTransaction {
                        changes.save(raised(id: null, version: null, number: "CHG003100$index", productId: productId,
                                state: state, approvals: [], template: template(release: FIX_VERSION),
                                tasks: [1, 2].collect { task(it).numbered("CTASK00$index$it") }))
                    }]
                }
        def withTasks = stored[CTASK_APPROVAL].id()

        when:
        rollBackSince('027-')

        then:
        !('DSO_PRODUCTION_CHANGE_APPROVAL' in tables())
        !('SUPPORT_APPROVER' in columns('DSO_CHANGE_PROFILE'))
        !('SUPPORT_APPROVER' in columns('DSO_PRODUCTION_CHANGE'))
        TASK_COLUMNS.every { !(it in columns('DSO_PRODUCTION_CHANGE_TASK')) }
        approvals(withTasks) == ['Not Yet Requested'] * 2

        when:
        jdbc.update("UPDATE DSO_PRODUCTION_CHANGE_TASK SET APPROVAL = 'Approved' WHERE CHANGE_ID = ? AND TASK_ORDER = 0",
                withTasks)
        jdbc.update("UPDATE DSO_PRODUCTION_CHANGE_TASK SET APPROVAL = 'Requested' WHERE CHANGE_ID = ? AND TASK_ORDER = 1",
                withTasks)
        liquibase.update('')
        def migrated = stored.collectEntries { state, change -> [state, inTransaction { changes.load(change.id()).get() }] }

        then:
        migrated.every { state, change -> change.approvals()*.approver() ==
                ['Rebecca Lawson', 'Olivia Bennett', 'James Carter', null] }
        migrated.collectEntries { state, change -> [state, change.approvals()*.state()] } == [
                (DRAFT)           : [NOT_APPROVED] * 4,
                (PRIMARY_APPROVAL): [APPROVED, REQUESTED, NOT_APPROVED, NOT_APPROVED],
                (CTASK_APPROVAL)  : [APPROVED] * 4,
                (CLOSED)          : [APPROVED] * 4]
        migrated[CTASK_APPROVAL].tasks()*.approval() == [APPROVED, REQUESTED]
        migrated[DRAFT].tasks()*.approval() == [NOT_APPROVED] * 2
        migrated.values()*.tasks().flatten()*.approvers().every { it == [] }
        migrated.values()*.approvals().flatten()*.reminder().every { it == null }
        migrated[DRAFT].template().approvers().supportApprover() == null

        when:
        def reminded = inTransaction {
            changes.save(migrated[PRIMARY_APPROVAL].toBuilder().state(SUPPORT_APPROVAL)
                    .template(template(release: FIX_VERSION)).build()
                    .reminded([(ApprovalRef.of(L1))                                        :
                                       new Reminder(RAISED, ['Olivia Bennett']),
                               (ApprovalRef.ofTask(migrated[PRIMARY_APPROVAL].tasks()[0].number())):
                                       new Reminder(RAISED, ['Rebecca Lawson', 'Thomas Ashby'])]))
        }
        def read = inTransaction { changes.load(reminded.id()).get() }

        then:
        read.state() == SUPPORT_APPROVAL
        read.template().approvers().supportApprover() == 'Jane Smith'
        read.approvals()[1].reminder() == new Reminder(RAISED, ['Olivia Bennett'])
        read.tasks()[0].reminder() == new Reminder(RAISED, ['Rebecca Lawson', 'Thomas Ashby'])
        read.tasks()[1].reminder() == null

        when:
        jdbc.update("UPDATE DSO_PRODUCTION_CHANGE_TASK SET APPROVAL = 'Requested' WHERE CHANGE_ID = ?", withTasks)

        then:
        thrown(DataIntegrityViolationException)
    }

    private List<String> approvals(long changeId) {
        jdbc.queryForList('SELECT APPROVAL FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ? ORDER BY TASK_ORDER',
                String, changeId)
    }
}
