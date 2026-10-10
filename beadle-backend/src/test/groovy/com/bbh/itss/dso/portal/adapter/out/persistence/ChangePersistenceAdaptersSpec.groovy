package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.change.ChangeProduct
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ApprovalRef
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate
import com.bbh.itss.dso.portal.domain.change.ChangeUsage
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.Reminder
import com.bbh.itss.dso.portal.domain.change.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.TaskDetails
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L1
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.L2
import static com.bbh.itss.dso.portal.domain.change.ApprovalRole.SUPPORT
import static com.bbh.itss.dso.portal.domain.change.ApprovalState.REQUESTED
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
import static com.bbh.itss.dso.portal.support.ChangeFixtures.ctask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.details as taskDetails
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.releaseTask
import static com.bbh.itss.dso.portal.support.ChangeFixtures.risk
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.secureCoding
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.task
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.CatalogFixtures.details
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:change-adapters;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([ChangeProfilePersistenceAdapter, ChangeProductPersistenceAdapter, ProductionChangePersistenceAdapter,
        ProductPersistenceAdapter, ChangeUsageAdapter])
class ChangePersistenceAdaptersSpec extends Specification {

    static final ChangeTemplate FULL = template(requestedFor: 'Ann Lee', requestedBy: 'Jane Smith',
            department: 'Corporate Technology', assignedTo: 'Grace Turner', release: 'R 4.2', incident: 'INC0012345',
            directBusinessService: 'Certificate management', problem: 'PRB0001234',
            affectedClients: 'Fund administration clients', usersAffected: 'Fund accountants', downtime: true,
            timing: new Timing('20:30', 3, 2), privilegedAccess: new PrivilegedAccess(true, [
            new PrivilegedUser('Jane Smith', 'adm_jsmith'), new PrivilegedUser('Ann Lee', 'adm_alee')]),
            riskAssessment: risk(bbhUsers: 'All users', backoutTesting: 'Unable to test'),
            secureCodingTicket: 'SCP-1234', secureCoding: secureCoding())
    static final ChangeTemplate RAISED_TEMPLATE = FULL.releasedAs(FIX_VERSION).toBuilder()
            .secureCodingTicket('SCP-2001').build()
    static final ChangeSchedule DOWNTIME = schedule(downtimeStart: '2026-10-10T06:00:00Z',
            downtimeEnd: '2026-10-10T08:30:00Z')

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ChangeProductPersistenceAdapter changeProducts

    @Autowired
    ProductionChangePersistenceAdapter changes

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    ChangeUsageAdapter usage

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
                template(approvers: new Approvers('Emma Brooks', null, null, 'Ann Lee'), privilegedAccess: privileged(3)),
                tasks(3).reverse()))
        entities.clear()

        then:
        created.version() == 0
        created.template() == FULL
        created.template().risk() == 'High'
        created.tasks() == tasks()
        created.updatedAt() != null
        changed.version() == 1
        profiles.find(product.id()).get() == changed
        changed.template().privilegedAccess() == privileged(3)
        changed.tasks() == tasks(3).reverse()
        jdbc.queryForMap('''SELECT L1_MANAGER, L2_MANAGER, TIMING_INSTALLATION_START, TEST_SUMMARY, DOWNTIME,
                PRIVILEGED_ACCESS_REQUIRED, RISK_BBH_USERS, REQUEST_DEPARTMENT FROM DSO_CHANGE_PROFILE
                WHERE PRODUCT_ID = ?''', product.id()) ==
                [L1_MANAGER: 'Emma Brooks', L2_MANAGER: null, TIMING_INSTALLATION_START: '18:00',
                 TEST_SUMMARY: TEST_SUMMARY, DOWNTIME: 0, PRIVILEGED_ACCESS_REQUIRED: 1, RISK_BBH_USERS: '5-25',
                 REQUEST_DEPARTMENT: null]
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
        def bare = template(approvers: Approvers.NONE, riskAssessment: RiskAssessment.DEFAULTS)

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
        profiles.save(profiles.find(access.id()).get().change(0L, template(category: 'Hardware'), tasks()))
        entities.clear()

        when:
        def summaries = profiles.summaries()

        then:
        summaries*.productName() == ['Access Hub', 'CertScanner']
        summaries[0] == new ChangeProfileSummary(access.id(), 'Access Hub', 1, summaries[0].updatedAt())
        !summaries[0].updatedAt().isBefore(stored.updatedAt())
        summaries[1].version() == 0
    }

    def "Beadle reads a product with its department and the Jira project of its profile, not its services"() {
        given:
        def ledger = save('LEDGER', 'Ledger', 4L)
        def access = save('ACCESS', 'Access Hub', 3L)
        jdbc.update('UPDATE DSO_PRODUCT SET DEPARTMENT_ID = NULL WHERE ID = ?', access.id())
        profiles.save(ChangeProfile.create(product.id(), template(jiraProjectKey: 'CSCAN'), tasks()))
        entities.clear()

        expect:
        changeProducts.get(product.id()) == new ChangeProduct(product.id(), 'CERT', 'CertScanner', null, 3L,
                'Corporate Technology', 'CSCAN')
        changeProducts.get(product.id()).jiraProject() == 'CSCAN'
        changeProducts.get(ledger.id()).jiraProject() == 'LEDGER'
        changeProducts.findAll() == [new ChangeProduct(access.id(), 'ACCESS', 'Access Hub', null, null, null, null),
                                     changeProducts.get(product.id()), changeProducts.get(ledger.id())]
        changeProducts.get(ledger.id()).departmentName() == 'Custody'

        when:
        changeProducts.get(99999L)

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == 'Product 99999 does not exist'
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
         loaded.departmentName(), loaded.openedBy()] == [product.id(), 'CERT', 'CertScanner', 3L,
                                                         'Corporate Technology', 'Mateusz Matan']
        loaded.schedule() == DOWNTIME
        loaded.createdAt() == RAISED
        loaded.state() == DRAFT
        loaded.workflow() == [new WorkflowStep(DRAFT, RAISED)]
        loaded.syncedAt() == RAISED
        loaded.update() == null
        loaded.template() == RAISED_TEMPLATE
        loaded.template().privilegedAccess().users()*.user() == ['Jane Smith', 'Ann Lee']
        [loaded.epicKeys(), loaded.storyKeys()] == [['CERT-1'], ['CERT-2']]
        loaded.tasks() == [ctask('CTASK0002001', 'Task 1 of the CertScanner release',
                'Step 1 of the CertScanner release.', OPEN), ctask('CTASK0002002',
                'Task 2 of the CertScanner release', 'Step 2 of the CertScanner release.', OPEN)]
        loaded.url() == 'https://snow/CHG0001001'
        changes.findAll()*.number() == ['CHG0001002', 'CHG0001001']
        changes.findAll()*.template().every { it == RAISED_TEMPLATE }
        changes.load(later.id()).get().tasks()*.number() == ['CTASK0002003', 'CTASK0002004']
        changes.load(9999L) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_PRIVILEGED_USER WHERE CHANGE_ID = ?', Integer,
                saved.id()) == 2
        jdbc.queryForMap('''SELECT OPENED_BY, REQUESTED_FOR, REQUESTED_BY, REQUEST_DEPARTMENT, ASSIGNED_TO,
                DIRECT_BUSINESS_SERVICE, USERS_AFFECTED, SECURE_CODING_TICKET, APO_NUMBER, BITBUCKET_URL, ARTIFACT_LINK,
                QC_APPLICATION_LINK, CHANGE_TYPE, CATEGORY, RISK_BBH_USERS, RISK_BACKOUT_TESTING
                FROM DSO_PRODUCTION_CHANGE WHERE ID = ?''', saved.id()) ==
                [OPENED_BY: 'Mateusz Matan', REQUESTED_FOR: 'Ann Lee', REQUESTED_BY: 'Jane Smith',
                 REQUEST_DEPARTMENT: 'Corporate Technology', ASSIGNED_TO: 'Grace Turner',
                 DIRECT_BUSINESS_SERVICE: 'Certificate management', USERS_AFFECTED: 'Fund accountants',
                 SECURE_CODING_TICKET: 'SCP-2001', APO_NUMBER: 'APO-12345',
                 BITBUCKET_URL: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
                 ARTIFACT_LINK: 'https://jenkins.bbh.com/job/CERT/job/cert-release/',
                 QC_APPLICATION_LINK: 'https://cert.qc.bbh.com', CHANGE_TYPE: 'STANDARD', CATEGORY: 'Application',
                 RISK_BBH_USERS: 'All users', RISK_BACKOUT_TESTING: 'Unable to test']
    }

    def "a change raised with the empty people of its template names the signed-in user and its department"() {
        given:
        def bare = template(riskAssessment: RiskAssessment.DEFAULTS)

        when:
        def saved = changes.save(ProductionChange.draft(changeProducts.get(product.id()), 'Mateusz Matan',
                FIX_VERSION, schedule(), bare, [epic('CERT-1', 'Expiry alerts')], [], null, null)
                .raisedAt(RAISED).numbered('CHG0001009', null).withTasks([task(1).numbered('CTASK0002019')]))
        entities.clear()

        then:
        with(changes.load(saved.id()).get()) {
            [it.template().requestedFor(), it.template().requestedBy(), it.template().assignedTo()] ==
                    ['Mateusz Matan'] * 3
            it.template().department() == 'Corporate Technology'
            it.template().risk() == 'Low'
            it.schedule() == schedule()
        }
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
                .tasks([saved.tasks()[1].in(WORK_IN_PROGRESS), ctask('CTASK0002009', 'Check the audit trail',
                        'Open the audit trail.', OPEN), saved.tasks()[0].in(CANCELED)])
                .update(update).syncedAt(RAISED.plusSeconds(660)).build()

        when:
        def stored = changes.save(synced)
        entities.clear()
        def loaded = changes.load(saved.id()).get()

        then:
        stored.version() == 1
        loaded == synced.toBuilder().version(1L).editedVersion(1L).build()
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

    def "a sync that only moved the workflow and the task states keeps the version its fields were edited at"() {
        given:
        def saved = changes.save(raise('CHG0001001', 'CTASK0002001', 'CTASK0002002'))
        entities.clear()
        def moved = saved.toBuilder().state(BUSINESS_APPROVAL)
                .workflow(saved.workflow() + new WorkflowStep(BUSINESS_APPROVAL, RAISED.plusSeconds(120)))
                .tasks([saved.tasks()[0].in(WORK_IN_PROGRESS), saved.tasks()[1]]).syncedAt(RAISED.plusSeconds(120))
                .build()

        when:
        def stored = changes.save(moved)
        entities.clear()
        def edited = changes.save(stored.toBuilder().description('Changed in ProTech.').build())
        entities.clear()
        def assessed = changes.save(edited.toBuilder().template(edited.template().toBuilder()
                .usersAffected('All fund accountants').build()).build())
        entities.clear()
        def windowed = changes.save(assessed.toBuilder().schedule(schedule(downtimeStart: '2026-10-10T07:00:00Z',
                downtimeEnd: '2026-10-10T08:00:00Z')).build())
        entities.clear()

        then:
        [stored.version(), stored.editedVersion()] == [1L, 0L]
        [edited.version(), edited.editedVersion()] == [2L, 2L]
        [assessed.version(), assessed.editedVersion()] == [3L, 3L]
        [windowed.version(), windowed.editedVersion()] == [4L, 4L]
        changes.load(saved.id()).get() == windowed
    }

    def "a claimed edit put back after ProTech refused it returns to the version its fields were edited at"() {
        given:
        def stored = changes.save(raise('CHG0001001', 'CTASK0002001'))
        entities.clear()

        when:
        def claimed = changes.save(stored.toBuilder().shortDescription('Renamed').build())
        entities.clear()
        def putBack = changes.save(stored.toBuilder().version(claimed.version()).build())
        entities.clear()

        then:
        [claimed.version(), claimed.editedVersion()] == [1L, 1L]
        [putBack.version(), putBack.editedVersion()] == [2L, 0L]
        changes.load(stored.id()).get() == putBack
    }

    def "a change task and a template task keep every ProTech field, the task also its start and its approval"() {
        given:
        def release = releaseTask([assignedTo     : 'Grace Turner', configurationItem: 'CertScanner UI',
                                   platform       : 'Distributed', packages: 'cert-4.2.tar\ncert-ui-4.2.tar',
                                   backoutPackages: 'cert-4.1.tar', additionalComments: 'Call the owner first.'])
                .numbered('CTASK0002031').withApproval(REQUESTED, ['Rebecca Lawson', 'Thomas Ashby'])
                .remindedBy(new Reminder(RAISED, ['Rebecca Lawson', 'Thomas Ashby']))
        def other = ChangeTask.of(taskDetails(2, [assignmentGroup  : 'Cloud Engineering', importance: '2 - High',
                                                  configurationItem: 'CertScanner']))
        def saved = changes.save(raise('CHG0001031').withTasks([release, other])
                .reminded([(ApprovalRef.of(SUPPORT)): new Reminder(RAISED, ['Jane Smith'])]))
        profiles.save(ChangeProfile.create(product.id(), FULL, [release.details(), other.details()]))
        entities.clear()

        expect:
        changes.load(saved.id()).get().tasks() == [release, other]
        changes.load(saved.id()).get().approvals() == saved.approvals()
        saved.approvals()*.role() == [BUSINESS, L1, L2, SUPPORT]
        saved.approvals()*.reminder() == [null, null, null, new Reminder(RAISED, ['Jane Smith'])]
        profiles.find(product.id()).get().tasks() == [release.details(), other.details()]
        jdbc.queryForMap('''SELECT ASSIGNMENT_GROUP, PLATFORM, APPLICATION, IMPORTANCE, APPROVAL, APPROVERS,
                REMINDER_SENT_TO FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ? AND TASK_NUMBER = ?''',
                saved.id(), 'CTASK0002031') == [ASSIGNMENT_GROUP: 'Release Management', PLATFORM: 'Distributed',
                                                APPLICATION     : 'CertScanner', IMPORTANCE: null,
                                                APPROVAL        : 'REQUESTED',
                                                APPROVERS       : 'Rebecca Lawson\nThomas Ashby',
                                                REMINDER_SENT_TO: 'Rebecca Lawson\nThomas Ashby']
        jdbc.queryForObject('''SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?
                AND TASK_START IS NOT NULL''', Integer, saved.id()) == 1
    }

    def "a change task added in Beadle is stored without a number, then with the number ProTech gave it"() {
        given:
        def saved = changes.save(raise('CHG0001001', 'CTASK0002001'))
        def added = ctask(null, 'Notify the users', 'Send the release notes.', OPEN)
        entities.clear()

        when:
        def pending = changes.save(saved.toBuilder().tasks(saved.tasks() + added).build())
        entities.clear()
        def applied = changes.save(pending.toBuilder().tasks(saved.tasks() + added.numbered('CTASK0002009')).build())
        entities.clear()

        then:
        pending.tasks()*.number() == ['CTASK0002001', null]
        applied.tasks()*.number() == ['CTASK0002001', 'CTASK0002009']
        changes.load(saved.id()).get().tasks() == saved.tasks() + added.numbered('CTASK0002009')
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ?', Integer,
                saved.id()) == 2
    }

    def "a sync that found nothing new only records its time on all the changes it read and keeps their version"() {
        given:
        def saved = changes.save(raise('CHG0001001', 'CTASK0002001'))
        def other = changes.save(raise('CHG0001002', 'CTASK0002002'))
        def untouched = changes.save(raise('CHG0001003', 'CTASK0002003'))
        entities.clear()

        when:
        changes.synced([saved.id(), other.id()], RAISED.plusSeconds(3600))
        entities.clear()
        def loaded = changes.load(saved.id()).get()

        then:
        loaded.syncedAt() == RAISED.plusSeconds(3600)
        loaded.version() == 0
        loaded == saved.toBuilder().syncedAt(RAISED.plusSeconds(3600)).build()
        changes.load(other.id()).get() == other.toBuilder().syncedAt(RAISED.plusSeconds(3600)).build()
        changes.load(untouched.id()).get() == untouched
    }

    def "the changes of a department are listed newest first and counted per department with its products"() {
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
        usage.perDepartment() == [3L: new ChangeUsage(1, 2), 4L: new ChangeUsage(1, 1)]
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
            it.tasks()*.details()*.shortDescription() == ['Task 1 of the CertScanner release']
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
        ProductionChange.draft(changeProducts.get(owner.id()), 'Mateusz Matan', FIX_VERSION, DOWNTIME, FULL,
                [epic('CERT-1', 'Expiry alerts')], [story('CERT-2', 'E-mail', 'CERT-1')], null, null)
                .withSecureCoding(FULL.secureCoding(), 'SCP-2001')
                .raisedAt(RAISED).numbered(number, "https://snow/$number".toString())
                .withTasks(taskNumbers.withIndex().collect { String taskNumber, int index ->
                    task(index + 1).numbered(taskNumber)
                })
    }

    private List<Long> taskIds(long changeId) {
        jdbc.queryForList('SELECT ID FROM DSO_PRODUCTION_CHANGE_TASK WHERE CHANGE_ID = ? ORDER BY TASK_ORDER', Long,
                changeId)
    }

    private Product save(String code, String name, long departmentId) {
        products.save(Product.create(details(code: code, name: name, departmentId: departmentId), products))
    }
}
