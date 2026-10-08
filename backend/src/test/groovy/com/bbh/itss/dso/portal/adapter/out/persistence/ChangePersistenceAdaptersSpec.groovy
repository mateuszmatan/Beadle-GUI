package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileSummary
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.TaskText
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEST_SUMMARY
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.NOT_APPLIED_MESSAGE
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.NOT_APPLIED
import static com.bbh.itss.dso.portal.domain.change.TaskState.CANCELED
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.change.TaskState.WORK_IN_PROGRESS
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:change-adapters;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter, ProductPersistenceAdapter])
class ChangePersistenceAdaptersSpec extends Specification {

    static final ChangeTemplate FULL = template(release: 'R 4.2', incident: 'INC0012345', problem: 'PRB0001234',
            affectedClients: 'Fund administration clients', downtime: true, timing: new Timing('20:30', 3, 2),
            privilegedAccess: new PrivilegedAccess(true, [new PrivilegedUser('Jane Smith', 'adm_jsmith'),
                                                          new PrivilegedUser('Ann Lee', 'adm_alee')]))

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    Product product

    def setup() {
        product = save('CERT', 'CertScanner', 3L)
        entities.clear()
    }

    def "a product template is created with its tasks, changed to a new version and refused when stale"() {
        expect:
        profiles.find(product.id()) == Optional.empty()

        when:
        def created = profiles.save(ChangeProfile.create(product.id(), FULL, tasks()))
        entities.clear()
        def changed = profiles.save(profiles.find(product.id()).get().change(0L,
                template(approvers: new Approvers('Emma Brooks', null, null), privilegedAccess: privileged(3)),
                tasks(3).reverse()))
        entities.clear()

        then:
        created.version() == 0
        created.template() == FULL
        created.tasks() == tasks()
        created.updatedAt() != null
        changed.version() == 1
        profiles.find(product.id()).get() == changed
        changed.template().privilegedAccess() == privileged(3)
        changed.tasks() == tasks(3).reverse()
        jdbc.queryForMap('''SELECT L1_MANAGER, L2_MANAGER, TIMING_INSTALLATION_START, TEST_SUMMARY, DOWNTIME,
                PRIVILEGED_ACCESS_REQUIRED, RISK_BBH_USERS FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?''', product.id()) ==
                [L1_MANAGER: 'Emma Brooks', L2_MANAGER: null, TIMING_INSTALLATION_START: '18:00',
                 TEST_SUMMARY: TEST_SUMMARY, DOWNTIME: 0, PRIVILEGED_ACCESS_REQUIRED: 1, RISK_BBH_USERS: 10]
        jdbc.queryForList('''SELECT u.POSITION, u.USER_NAME, u.ACCOUNT_NAME FROM DSO_CHANGE_PROFILE_PRIVILEGED_USER u
                JOIN DSO_CHANGE_PROFILE p ON p.ID = u.PROFILE_ID WHERE p.PRODUCT_ID = ? ORDER BY u.POSITION''',
                product.id()) == (1..3).collect { [POSITION: it - 1, USER_NAME: "User $it".toString(),
                                                    ACCOUNT_NAME: "adm_user$it".toString()] }
        jdbc.queryForList('''SELECT t.POSITION, t.SHORT_DESCRIPTION FROM DSO_CHANGE_PROFILE_TASK t
                JOIN DSO_CHANGE_PROFILE p ON p.ID = t.PROFILE_ID WHERE p.PRODUCT_ID = ? ORDER BY t.POSITION''',
                product.id()) == (0..2).collect {
                    [POSITION: it, SHORT_DESCRIPTION: "Task ${3 - it} of the CertScanner release".toString()]
                }

        when:
        profiles.save(created)

        then:
        thrown(IllegalStateException)
    }

    def "a template without approvers, privileged users or risk assessment reads back as such"() {
        given:
        def bare = template(approvers: Approvers.NONE, riskAssessment: RiskAssessment.NONE)

        when:
        profiles.save(ChangeProfile.create(product.id(), bare, tasks(1)))
        entities.clear()

        then:
        profiles.find(product.id()).get().template() == bare
        profiles.find(product.id()).get().tasks() == tasks(1)
    }

    def "the stored profiles are summarised by product name"() {
        given:
        def access = save('ACCESS', 'Access Hub', 3L)
        save('LEDGER', 'Ledger', 3L)
        profiles.save(ChangeProfile.create(product.id(), FULL, tasks()))
        def stored = profiles.save(ChangeProfile.create(access.id(), template(), tasks()))
        profiles.save(profiles.find(access.id()).get().change(0L, template(category: 'Apps'), tasks()))
        entities.clear()

        when:
        def summaries = profiles.summaries()

        then:
        summaries*.productName() == ['Access Hub', 'CertScanner']
        summaries[0] == new ChangeProfileSummary(access.id(), 'Access Hub', 1, summaries[0].updatedAt())
        !summaries[0].updatedAt().isBefore(stored.updatedAt())
        summaries[1].version() == 0
    }

    def "a raised change is stored with its department, its draft stage, its Jira keys and its tasks in order"() {
        given:
        def raised = raise('CHG0001001', 'CTASK0002001', 'CTASK0002002')

        when:
        def saved = changes.save(raised)
        def later = changes.save(raise('CHG0001002', 'CTASK0002003', 'CTASK0002004'))
        entities.clear()
        def loaded = changes.load(saved.id()).get()

        then:
        saved.id() != null
        saved.version() == 0
        loaded == saved
        loaded == raised.toBuilder().id(saved.id()).version(0L).build()
        [loaded.productId(), loaded.productCode(), loaded.productName(), loaded.departmentId(),
         loaded.departmentName()] == [product.id(), 'CERT', 'CertScanner', 3L, 'Corporate Technology']
        loaded.createdAt() == RAISED
        loaded.state() == DRAFT
        loaded.workflow() == [new WorkflowStep(DRAFT, RAISED)]
        loaded.syncedAt() == RAISED
        loaded.update() == null
        loaded.template() == FULL.releasedAs(FIX_VERSION)
        loaded.template().privilegedAccess().users()*.user() == ['Jane Smith', 'Ann Lee']
        [loaded.epicKeys(), loaded.storyKeys()] == [['CERT-1'], ['CERT-2']]
        loaded.tasks() == [new ChangeTask('CTASK0002001', 'Task 1 of the CertScanner release',
                'Step 1 of the CertScanner release.', OPEN), new ChangeTask('CTASK0002002',
                'Task 2 of the CertScanner release', 'Step 2 of the CertScanner release.', OPEN)]
        loaded.url() == 'https://snow/CHG0001001'
        changes.findAll()*.number() == ['CHG0001002', 'CHG0001001']
        changes.findAll()*.template().every { it == FULL.releasedAs(FIX_VERSION) }
        changes.load(later.id()).get().tasks()*.number() == ['CTASK0002003', 'CTASK0002004']
        changes.load(9999L) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_PRIVILEGED_USER WHERE CHANGE_ID = ?', Integer,
                saved.id()) == 2
    }

    def "a synced change is stored at a new version with its stages, its task states and its update status"() {
        given:
        def saved = changes.save(raise('CHG0001001', 'CTASK0002001', 'CTASK0002002'))
        entities.clear()
        def before = taskIds(saved.id())
        def update = new ChangeUpdate(NOT_APPLIED, RAISED.plusSeconds(600), 'Corporate Technology',
                ['schedule.installationEnd', 'tasks'], NOT_APPLIED_MESSAGE, RAISED.plusSeconds(660))
        def synced = saved.toBuilder().shortDescription('Renamed in ProTech')
                .schedule(schedule(firstUsage: '2026-10-13T08:00:00Z')).state(BUSINESS_APPROVAL)
                .workflow(saved.workflow() + new WorkflowStep(BUSINESS_APPROVAL, RAISED.plusSeconds(120)))
                .tasks([saved.tasks()[1].in(WORK_IN_PROGRESS), new ChangeTask('CTASK0002009', 'Check the audit trail',
                        'Open the audit trail.', OPEN), saved.tasks()[0].in(CANCELED)])
                .update(update).syncedAt(RAISED.plusSeconds(660)).build()

        when:
        def stored = changes.save(synced)
        entities.clear()
        def loaded = changes.load(saved.id()).get()

        then:
        stored.version() == 1
        loaded == synced.toBuilder().version(1L).build()
        loaded.update().fields() == ['schedule.installationEnd', 'tasks']
        with(taskIds(saved.id())) {
            it[0] == before[1]
            !(it[1] in before)
            it[2] == before[0]
        }
        loaded.tasks()*.state() == [WORK_IN_PROGRESS, OPEN, CANCELED]

        when:
        changes.save(synced)

        then:
        def stale = thrown(IllegalStateException)
        stale.message == STALE_VERSION
    }

    def "a sync that found nothing new only records its time and keeps the version"() {
        given:
        def saved = changes.save(raise('CHG0001001', 'CTASK0002001'))
        entities.clear()

        when:
        changes.synced(saved.id(), RAISED.plusSeconds(3600))
        entities.clear()
        def loaded = changes.load(saved.id()).get()

        then:
        loaded.syncedAt() == RAISED.plusSeconds(3600)
        loaded.version() == 0
        loaded == saved.toBuilder().syncedAt(RAISED.plusSeconds(3600)).build()
    }

    def "the changes of a department are listed newest first"() {
        given:
        def custody = save('CUST', 'Custody Ledger', 4L)
        def first = changes.save(raise('CHG0001001', 'CTASK0002001'))
        changes.save(raise('CHG0001002', 'CTASK0002002', custody))
        def last = changes.save(raise('CHG0001003', 'CTASK0002003'))
        entities.clear()

        expect:
        changes.findByDepartment(3L)*.id() == [last.id(), first.id()]
        changes.findByDepartment(4L)*.number() == ['CHG0001002']
        changes.findByDepartment(5L) == []
        changes.findAll()*.number() == ['CHG0001003', 'CHG0001002', 'CHG0001001']
    }

    def "deleting a product deletes its template and keeps its raised changes"() {
        given:
        profiles.save(ChangeProfile.create(product.id(), FULL, tasks()))
        def saved = changes.save(raise('CHG0001003', 'CTASK0002005'))
        entities.clear()

        when:
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product.id())

        then:
        profiles.find(product.id()) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_CHANGE_PROFILE_PRIVILEGED_USER', Integer) == 0
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_CHANGE_PROFILE_TASK', Integer) == 0
        with(changes.load(saved.id()).get()) {
            productId() == null
            productName() == 'CertScanner'
            departmentId() == 3L
            it.tasks()*.shortDescription() == ['Task 1 of the CertScanner release']
            it.workflow() == [new WorkflowStep(DRAFT, RAISED)]
            it.template().privilegedAccess().users().size() == 2
        }
    }

    private ProductionChange raise(String number, String... taskNumbers) {
        raise(number, taskNumbers.toList(), product)
    }

    private ProductionChange raise(String number, String taskNumber, Product owner) {
        raise(number, [taskNumber], owner)
    }

    private ProductionChange raise(String number, List<String> taskNumbers, Product owner) {
        List<TaskText> texts = tasks(taskNumbers.size())
        String department = owner.departmentId() == 3L ? 'Corporate Technology' : 'Custody'
        ProductionChange.draft(owner, owner.departmentId(), department, texts, FIX_VERSION, schedule(), FULL,
                [epic('CERT-1', 'Expiry alerts')], [story('CERT-2', 'E-mail', 'CERT-1')], null, null).raisedAt(RAISED)
                .numbered(number, taskNumbers, "https://snow/$number".toString())
    }

    private List<Long> taskIds(long changeId) {
        jdbc.queryForList('SELECT ID FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ? ORDER BY TASK_ORDER', Long,
                changeId)
    }

    private Product save(String code, String name, long departmentId) {
        products.save(Product.create(details(code: code, name: name, departmentId: departmentId), account(),
                [new ServiceDraft(null, 'gui', 'Angular', settings()), new ServiceDraft(null, 'api', null, settings())],
                products))
    }
}
