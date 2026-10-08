package com.bbh.itss.dso.portal.application.change

import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand
import com.bbh.itss.dso.portal.application.change.port.in.ChangeEditCommand
import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProductsPort
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort.RaisedChange
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUser
import com.bbh.itss.dso.portal.application.user.port.in.SignedInUserUseCase
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTask
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.ChangeUpdate
import com.bbh.itss.dso.portal.domain.change.JiraVersion
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.TaskText
import com.bbh.itss.dso.portal.domain.change.WorkflowStep
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.dso.portal.domain.change.ChangeSchedule.UNPLANNED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.dso.portal.domain.change.ChangeState.CLOSED
import static com.bbh.itss.dso.portal.domain.change.ChangeState.DRAFT
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.NOT_APPLIED_MESSAGE
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.APPLIED
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.NOT_APPLIED
import static com.bbh.itss.dso.portal.domain.change.ChangeUpdate.Status.PENDING
import static com.bbh.itss.dso.portal.domain.change.TaskState.OPEN
import static com.bbh.itss.dso.portal.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.dso.portal.domain.shared.Failures.staleVersion
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.RAISED
import static com.bbh.itss.dso.portal.support.ChangeFixtures.changeProduct
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class ProductionChangeServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')
    static final List ISSUES = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail'),
                                story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record it', 'CERT-5')]
    static final ChangeTask NEW_TASK = new ChangeTask(null, 'Check the audit trail', 'Open the audit trail.', null)
    static final ChangeUpdate PENDING_UPDATE = new ChangeUpdate(PENDING, NOW, 'Corporate Technology',
            ['shortDescription'], null, NOW)

    ChangeProductsPort products = Stub()
    ProductionChangeRepositoryPort changes = Mock()
    JiraPort jira = Mock()
    ServiceNowPort serviceNow = Mock()
    SignedInUserUseCase users = Stub() {
        signedInUser() >> new SignedInUser('Mateusz Matan')
    }
    def service = new ProductionChangeService(products, changes, jira, serviceNow, users, fixed(NOW, UTC))
    def certScanner = changeProduct(jiraProjectKey: 'CSCAN')

    def setup() {
        products.get(1L) >> certScanner
        products.get(2L) >> changeProduct(id: 2L, code: 'PAYHUB', name: 'PayHub', departmentId: 4L,
                departmentName: 'Custody')
        products.get(3L) >> changeProduct(id: 3L, departmentId: null, departmentName: null)
        jira.epics('CERT', _) >> { project, version -> version == FIX_VERSION ? ISSUES.take(2) : [] }
        jira.stories('CERT', _, _) >> { project, version, Collection epics ->
            version == FIX_VERSION ? ISSUES.drop(2).findAll { it.epicKey() in epics } : []
        }
    }

    def "the preview drafts the change of the product's department with its tasks and the chosen Jira issues"() {
        when:
        def draft = service.preview(command(tasks: tasks(3)))

        then:
        draft == ProductionChange.draft(certScanner, 'Mateusz Matan', tasks(3), FIX_VERSION, schedule(), template(),
                ISSUES.take(2), ISSUES.drop(2), null, null)
        draft.departmentId() == 3L
        draft.openedBy() == 'Mateusz Matan'
        [draft.template().requestedFor(), draft.template().requestedBy(), draft.template().assignedTo(),
         draft.template().department()] == ['Mateusz Matan', 'Mateusz Matan', 'Mateusz Matan', 'Corporate Technology']
        draft.tasks()*.shortDescription() == tasks(3)*.shortDescription()
        draft.tasks()*.number() == [null, null, null]
        draft.tasks()*.state() == [OPEN] * 3
        draft.template().release() == FIX_VERSION
        draft.state() == DRAFT
        draft.workflow() == []
        0 * changes._
        0 * serviceNow._
    }

    def "a product without stored ProTech defaults raises a change from the request in its department"() {
        given:
        def payHub = template(jiraProjectKey: 'PAY', configurationItem: 'PayHub')
        jira.epics('PAY', 'PAY 1.0') >> [epic('PAY-1', 'Instant payments')]

        when:
        def preview = service.preview(command(productId: 2L, template: payHub, epicKeys: ['PAY-1'], storyKeys: [],
                fixVersion: ' PAY 1.0 ', tasks: tasks(1)))

        then:
        preview.productCode() == 'PAYHUB'
        preview.departmentId() == 4L
        preview.departmentName() == 'Custody'
        preview.fixVersion() == 'PAY 1.0'
        preview.template() == payHub.releasedAs('PAY 1.0').openedBy('Mateusz Matan', 'Custody')
        preview.shortDescription() == 'PayHub PAY 1.0: Instant payments'
        preview.description().startsWith('Production release PAY 1.0 of PayHub (PAYHUB) in Custody.')
        preview.description().contains('Change tasks: Task 1 of the CertScanner release.')
    }

    def "a change of a product outside every department is refused on the product when it is #action"() {
        when:
        call(service)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('productId',
                'the product must be placed in a department in Beadle Admin first')]
        0 * serviceNow._
        0 * changes._

        where:
        action      | call
        'previewed' | { ProductionChangeService it -> it.preview(command(productId: 3L)) }
        'raised'    | { ProductionChangeService it -> it.raise(command(productId: 3L)) }
    }

    def "a raised change is filed in ProTech and stored with its numbers, its draft stage and its sync time"() {
        when:
        def raised = service.raise(command(storyKeys: [], shortDescription: 'Mine'))

        then:
        1 * serviceNow.raise({ it.number() == null && it.shortDescription() == 'Mine' && it.createdAt() == NOW }) >>
                new RaisedChange('CHG0012345', ['CTASK0020001', 'CTASK0020002'], 'https://bbh.service-now.com/CHG0012345')
        1 * changes.save({ it.number() == 'CHG0012345' }) >> { ProductionChange change -> change }
        0 * serviceNow._
        raised.number() == 'CHG0012345'
        raised.tasks()*.number() == ['CTASK0020001', 'CTASK0020002']
        raised.tasks()*.shortDescription() == tasks()*.shortDescription()
        raised.departmentId() == 3L
        raised.departmentName() == 'Corporate Technology'
        raised.state() == DRAFT
        raised.workflow() == [new WorkflowStep(DRAFT, NOW)]
        raised.syncedAt() == NOW
        raised.createdAt() == NOW
        raised.update() == null
        raised.url() == 'https://bbh.service-now.com/CHG0012345'
        raised.fixVersion() == FIX_VERSION
        raised.schedule() == schedule()
    }

    def "#refusal is refused with every problem before anything is raised"() {
        when:
        service.raise(command(edits))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == problems.collect { new FieldProblem(it.key, it.value) }
        0 * serviceNow._
        0 * changes._

        where:
        refusal                         | edits                                              || problems
        'a change without tasks'        | [tasks: []]                                        || [tasks: 'add at least one change task']
        'a change with 51 tasks'        | [tasks: tasks(51)]                                 || [tasks: 'may list at most 50 change tasks']
        'a blank task'                  | [tasks: [new TaskText(' ', null)]]                 || ['tasks[0].shortDescription': 'is required', 'tasks[0].description': 'is required']
        'a task over 160 bytes'         | [tasks: tasks(1) + new TaskText('é' * 81, 'Long.')] || ['tasks[1].shortDescription': 'is too long: it may take at most 160 bytes']
        'a change without a FixVersion' | [fixVersion: ' ']                                  || [fixVersion: 'choose the FixVersion of the release']
        'a FixVersion over 100 bytes'   | [fixVersion: 'é' * 51]                             || [fixVersion: 'is too long: it may take at most 100 bytes']
        'a change without epics'        | [epicKeys: [], storyKeys: []]                      || [epicKeys: 'choose at least one epic']
        'an epic missing in Jira'       | [epicKeys: ['CERT-5', 'CERT-404'], storyKeys: []]  || [epicKeys: 'CERT-404 is not an epic of FixVersion CERT 4.2 in Jira project CERT']
        'a story chosen as an epic'     | [epicKeys: ['CERT-1', 'CERT-2'], storyKeys: []]    || [epicKeys: 'CERT-2 is not an epic of FixVersion CERT 4.2 in Jira project CERT']
        'an epic of another FixVersion' | [fixVersion: 'CERT 4.1', epicKeys: ['CERT-1'], storyKeys: []] || [epicKeys: 'CERT-1 is not an epic of FixVersion CERT 4.1 in Jira project CERT']
        'a story of another epic'       | [epicKeys: ['CERT-1']]                             || [storyKeys: 'CERT-6 is not a story of the chosen epics in FixVersion CERT 4.2']
        'a story without its epic'      | [epicKeys: ['CERT-404'], storyKeys: ['CERT-2']]    || [epicKeys: 'CERT-404 is not an epic of FixVersion CERT 4.2 in Jira project CERT', storyKeys: 'CERT-2 is not a story of the chosen epics in FixVersion CERT 4.2']
        'a story missing in Jira'       | [storyKeys: ['CERT-2', 'CERT-77']]                 || [storyKeys: 'CERT-77 is not a story of the chosen epics in FixVersion CERT 4.2']
        'an installation in the past'   | [schedule: schedule(installationStart: '2026-10-07T09:59:00Z')] || ['schedule.installationStart': 'must be in the future']
        'an installation starting now'  | [schedule: schedule(installationStart: '2026-10-07T10:00:00Z')] || ['schedule.installationStart': 'must be in the future']
        'an installation ending first'  | [schedule: schedule(installationEnd: '2026-10-10T06:00:00Z', validationStart: '2026-10-10T06:00:00Z')] || ['schedule.installationEnd': 'must be after the installation start']
        'a first usage before the end'  | [schedule: schedule(firstUsage: '2026-10-10T10:30:00Z')] || ['schedule.firstUsage': 'must not be before the validation end']
        'a change without a schedule'   | [schedule: null]                                   || [schedule: 'choose when the change is installed, validated and first used']
        'a change without a template'   | [template: null]                                   || [template: 'fill in the ProTech fields of the change']
        'a broken template'             | [template: template(timing: new Timing('18:00', 0, 1), privilegedAccess: new PrivilegedAccess(true, []))] || ['template.timing.installationHours': 'must be between 1 and 72 hours', 'template.privilegedAccess.users': 'add the users who need privileged access']
        'a short description over 160 bytes' | [shortDescription: 'é' * 81]                  || [shortDescription: 'is too long: it may take at most 160 bytes']
        'a description over 4000 bytes' | [description: 'ł' * 2001]                         || [description: 'is too long: it may take at most 4000 bytes']
    }

    def "a template without a valid Jira key is refused without asking Jira"() {
        when:
        service.preview(command(template: template(jiraProjectKey: key)))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems()*.field() == ['template.jiraProjectKey']
        0 * jira._

        where:
        key << [null, 'CE-RT']
    }

    def "the preview allows an installation start in the past, the raise does not"() {
        given:
        def past = command(schedule: schedule(installationStart: '2026-10-01T06:00:00Z'))

        when:
        def preview = service.preview(past)

        then:
        preview.schedule().installationStart() == Instant.parse('2026-10-01T06:00:00Z')

        when:
        service.raise(past)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('schedule.installationStart', 'must be in the future')]
    }

    def "the preview writes the texts before the schedule is #planned, the raise refuses it"() {
        given:
        def unplanned = command(schedule: schedule)

        when:
        def preview = service.preview(unplanned)

        then:
        preview.schedule() == UNPLANNED
        preview.description().contains('Installation not planned yet, post-install validation not planned yet,' +
                ' first usage not planned yet. No downtime.')

        when:
        service.raise(unplanned)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems()*.field().every { it.startsWith('schedule') }
        0 * serviceNow._

        where:
        planned          | schedule
        'sent'           | null
        'filled in'      | UNPLANNED
    }

    def "FixVersions come from the Jira project of the stored profile, unreleased first and newest first"() {
        given:
        def old = new JiraVersion('CSCAN 1.0', true, LocalDate.parse('2026-01-10'))
        def last = new JiraVersion('CSCAN 1.1', true, LocalDate.parse('2026-09-10'))
        def next = new JiraVersion('CSCAN 1.2', false, LocalDate.parse('2026-10-20'))

        when:
        def versions = service.versions(1L, null)

        then:
        1 * jira.versions('CSCAN') >> [old, next, last]
        versions == [next, last, old]
    }

    def "Jira is read from #expected when the project parameter is #project and the product #stored a profile"() {
        when:
        service.versions(productId, project)
        service.epics(productId, FIX_VERSION, project)
        service.stories(productId, FIX_VERSION, ['X-1'], project)

        then:
        1 * jira.versions(expected) >> []
        1 * jira.epics(expected, FIX_VERSION) >> []
        1 * jira.stories(expected, FIX_VERSION, ['X-1']) >> []

        where:
        productId | project   || expected
        1L        | null      || 'CSCAN'
        1L        | ' '       || 'CSCAN'
        1L        | ' cert '  || 'CERT'
        2L        | null      || 'PAYHUB'
        2L        | 'PAY_2'   || 'PAY_2'

        stored = productId == 1L ? 'has' : 'has no'
    }

    def "epics and stories are read by FixVersion and only for chosen epics"() {
        when:
        def epics = service.epics(1L, ' CERT 4.2 ', 'CERT')
        def stories = service.stories(1L, 'CERT 4.2', ['CERT-1'], 'CERT')
        def none = service.stories(1L, 'CERT 4.2', [], 'CERT')

        then:
        1 * jira.epics('CERT', 'CERT 4.2') >> ISSUES.take(2)
        1 * jira.stories('CERT', 'CERT 4.2', ['CERT-1']) >> ISSUES.drop(2).take(1)
        epics*.key() == ['CERT-1', 'CERT-5']
        stories*.key() == ['CERT-2']
        none == []
    }

    def "Jira is not asked for #method with #problem"() {
        when:
        action(service)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem(field, message)]
        0 * jira._

        where:
        method    | problem              | action                                                  || field        | message
        'epics'   | 'a blank FixVersion' | { ProductionChangeService it -> it.epics(1L, ' ', null) } || 'fixVersion' | 'choose a FixVersion'
        'stories' | 'no FixVersion'      | { ProductionChangeService it -> it.stories(1L, null, ['CERT-1'], null) } || 'fixVersion' | 'choose a FixVersion'
        'versions'| 'a broken project'   | { ProductionChangeService it -> it.versions(1L, 'ce-rt') } || 'project' | JIRA_KEY_MESSAGE
    }

    def "the integrations say whether Jira and ProTech are connected"() {
        when:
        def integrations = service.integrations()

        then:
        1 * jira.connected() >> false
        1 * serviceNow.connected() >> true
        integrations == new ChangeIntegrations(false, true)
    }

    def "the list syncs the open changes with one ProTech read and saves only the changes ProTech changed"() {
        given:
        def unchanged = raised()
        def moved = raised(id: 8L, number: 'CHG0031002')
        def closed = raised(id: 6L, number: 'CHG0031000', state: CLOSED)
        def progressed = moved.toBuilder().state(BUSINESS_APPROVAL)
                .workflow(moved.workflow() + new WorkflowStep(BUSINESS_APPROVAL, RAISED.plusSeconds(120))).build()

        when:
        def listed = service.list(3L)

        then:
        1 * changes.findByDepartment(3L) >> [moved, unchanged, closed]
        1 * serviceNow.read([moved, unchanged]) >> [CHG0031001: unchanged, CHG0031002: progressed]
        1 * changes.save(progressed.toBuilder().syncedAt(NOW).build()) >>
                { ProductionChange change -> change.toBuilder().version(1L).build() }
        1 * changes.synced(7L, NOW)
        0 * changes._
        0 * serviceNow._
        listed*.number() == ['CHG0031002', 'CHG0031001', 'CHG0031000']
        listed[0].state() == BUSINESS_APPROVAL
        listed[0].version() == 1L
        listed[1] == unchanged.toBuilder().syncedAt(NOW).build()
        listed[2].is(closed)
    }

    def "a list without open changes does not ask ProTech"() {
        given:
        def closed = raised(state: CLOSED)

        when:
        def listed = service.list(null)

        then:
        1 * changes.findAll() >> [closed]
        0 * changes._
        0 * serviceNow._
        listed == [closed]
    }

    def "the listed open changes keep their stored values when ProTech #problem"() {
        given:
        def open = raised()
        def closed = raised(id: 6L, number: 'CHG0031000', state: CLOSED)

        when:
        def listed = service.list(null)

        then:
        1 * changes.findAll() >> [open, closed]
        1 * serviceNow.read([open]) >> { reply() }
        0 * changes._
        listed == [open.withSyncProblem(message), closed]

        where:
        problem             | reply                                                                       || message
        'cannot be reached' | { throw new UncheckedIOException('Connection refused', new IOException()) } || 'ProTech could not be reached: Connection refused.'
        'does not hold it'  | { [:] }                                                                     || 'ProTech has no change CHG0031001.'
    }

    def "an opened change is read from ProTech even when closed and is saved only when ProTech #changed it"() {
        given:
        def stored = raised(state: CLOSED)

        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * serviceNow.read([stored]) >> [CHG0031001: remote]
        saves * changes.save(remote.toBuilder().syncedAt(NOW).build()) >>
                { ProductionChange change -> change.toBuilder().version(1L).build() }
        (1 - saves) * changes.synced(7L, NOW)
        loaded.shortDescription() == remote.shortDescription()
        loaded.syncedAt() == NOW
        loaded.syncProblem() == null
        loaded.version() == saves

        where:
        remote                                                          || saves
        raised(state: CLOSED)                                           || 0
        raised(state: CLOSED, shortDescription: 'Renamed in ProTech')   || 1

        changed = saves ? 'changed' : 'did not change'
    }

    def "an opened change keeps its stored values when ProTech #problem"() {
        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(raised())
        1 * serviceNow.read(_) >> { reply() }
        0 * changes._
        loaded == raised(syncProblem: message)

        where:
        problem             | reply                                                                    || message
        'cannot be reached' | { throw new UncheckedIOException('Read timed out', new IOException()) } || 'ProTech could not be reached: Read timed out.'
        'does not hold it'  | { [:] }                                                                  || 'ProTech has no change CHG0031001.'
    }

    def "a pending update is #status when the change is opened #age seconds after it was published"() {
        given:
        def requested = raised(shortDescription: 'Renamed', update: new ChangeUpdate(PENDING, NOW.minusSeconds(age),
                'Corporate Technology', ['shortDescription'], null, NOW.minusSeconds(age)))

        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(requested)
        1 * serviceNow.read([requested]) >> [CHG0031001: raised(state: BUSINESS_APPROVAL)]
        1 * changes.save(_) >> { ProductionChange change -> change }
        loaded.update() == new ChangeUpdate(status, NOW.minusSeconds(age), 'Corporate Technology',
                ['shortDescription'], message, NOW)
        loaded.shortDescription() == shown
        loaded.state() == BUSINESS_APPROVAL

        where:
        age | status      || shown                           | message
        59  | PENDING     || 'Renamed'                       | null
        60  | NOT_APPLIED || raised().shortDescription()     | NOT_APPLIED_MESSAGE
    }

    def "an unknown change is not found when it is #action"() {
        given:
        changes.load(8L) >> Optional.empty()

        when:
        call(service)

        then:
        thrown(NoSuchElementException)
        0 * serviceNow._

        where:
        action    | call
        'opened'  | { ProductionChangeService it -> it.get(8L) }
        'updated' | { ProductionChangeService it -> it.update(8L, edit()) }
    }

    def "an update its department publishes to ProTech is verified as applied and stored"() {
        given:
        def stored = raised()
        def applied = raised(shortDescription: 'Renamed', state: BUSINESS_APPROVAL,
                tasks: stored.tasks() + NEW_TASK.numbered('CTASK0041003'))

        when:
        def updated = service.update(7L, edit(shortDescription: ' Renamed ', tasks: stored.tasks() + NEW_TASK))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * serviceNow.read([stored]) >> [CHG0031001: stored]
        1 * changes.synced(7L, NOW)

        then:
        1 * serviceNow.update({ ProductionChange it ->
            it.shortDescription() == 'Renamed' && it.tasks()*.number() == ['CTASK0041001', 'CTASK0041002', null] &&
                    it.tasks()*.state() == [OPEN] * 3 && it.update() == null
        })

        then:
        1 * serviceNow.read({ it*.update()*.status() == [PENDING] }) >> [CHG0031001: applied]
        1 * changes.save(_) >> { ProductionChange change -> change.toBuilder().version(1L).build() }
        0 * changes._
        0 * serviceNow._
        updated.shortDescription() == 'Renamed'
        updated.tasks()*.number() == ['CTASK0041001', 'CTASK0041002', 'CTASK0041003']
        updated.state() == BUSINESS_APPROVAL
        updated.update() == new ChangeUpdate(APPLIED, NOW, 'Corporate Technology', [], null, NOW)
        updated.syncedAt() == NOW
        updated.syncProblem() == null
        updated.version() == 1L
    }

    def "an update read before ProTech only moved the change through its workflow is published"() {
        given:
        def stored = raised(state: BUSINESS_APPROVAL, version: 4L, editedVersion: 2L)
        changes.load(7L) >> Optional.of(stored)
        serviceNow.read(_) >> [CHG0031001: stored]

        when:
        def updated = service.update(7L, edit(version: 3L, shortDescription: 'Renamed'))

        then:
        1 * serviceNow.update({ it.shortDescription() == 'Renamed' })
        1 * changes.save({ it.version() == 4L }) >> { ProductionChange change -> change.toBuilder().version(5L).build() }
        updated.version() == 5L
        updated.update().status() == PENDING
    }

    def "an update ProTech has not applied yet is stored as pending with the requested values"() {
        given:
        def stored = raised()

        when:
        def updated = service.update(7L, edit(shortDescription: 'Renamed'))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * serviceNow.read(_) >> [CHG0031001: stored]
        1 * changes.synced(7L, NOW)

        then:
        1 * serviceNow.update(_)

        then:
        1 * serviceNow.read(_) >> [CHG0031001: raised(state: BUSINESS_APPROVAL)]
        1 * changes.save(_) >> { ProductionChange change -> change }
        updated.shortDescription() == 'Renamed'
        updated.state() == BUSINESS_APPROVAL
        updated.update() == new ChangeUpdate(PENDING, NOW, 'Corporate Technology', ['shortDescription'], null, NOW)
    }

    def "an update published to ProTech stays pending with every requested change when the read back #problem"() {
        given:
        def stored = raised()

        when:
        def updated = service.update(7L, edit(shortDescription: 'Renamed', tasks: stored.tasks() + NEW_TASK))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * serviceNow.read(_) >> [CHG0031001: stored]
        1 * changes.synced(7L, NOW)

        then:
        1 * serviceNow.update(_)

        then:
        1 * serviceNow.read(_) >> { reply() }
        1 * changes.save({ ProductionChange it -> it.syncProblem() == message }) >>
                { ProductionChange change -> change.withSyncProblem(null) }
        updated.shortDescription() == 'Renamed'
        updated.tasks()*.number() == ['CTASK0041001', 'CTASK0041002', null]
        updated.update() == new ChangeUpdate(PENDING, NOW, 'Corporate Technology', ['shortDescription', 'tasks'],
                null, null)
        updated.syncProblem() == message

        where:
        problem             | reply                                                                    || message
        'cannot be reached' | { throw new UncheckedIOException('Read timed out', new IOException()) } || 'ProTech could not be reached: Read timed out.'
        'misses the change' | { [:] }                                                                  || 'ProTech has no change CHG0031001.'
    }

    def "an update #refusal is refused before ProTech is asked"() {
        given:
        changes.load(7L) >> Optional.of(stored)

        when:
        service.update(7L, edit(departmentId: departmentId, version: version))

        then:
        def refused = thrown(failure)
        refused.message == message
        0 * serviceNow._
        0 * changes.save(_)

        where:
        refusal                          | stored                                              | departmentId | version || failure                 | message
        'without a department'           | raised()                                            | null         | 0L      || InvalidRequestException | 'choose your department'
        'of another department'          | raised()                                            | 4L           | 0L      || SecurityException       | 'Only Corporate Technology can change CHG0031001'
        'of a change without department' | raised(departmentId: null, departmentName: null)    | 3L           | 0L      || SecurityException       | 'No department owns CHG0031001, so it cannot be changed in Beadle'
        'at a stale version'             | raised(version: 2L, editedVersion: 2L)              | 3L           | 1L      || IllegalStateException   | STALE_VERSION
        'read before its last edit'      | raised(version: 4L, editedVersion: 3L)              | 3L           | 2L      || IllegalStateException   | STALE_VERSION
        'at a version it never had'      | raised(version: 2L)                                 | 3L           | 3L      || IllegalStateException   | STALE_VERSION
        'while the last one is pending'  | raised(update: PENDING_UPDATE)                      | 3L           | 0L      || IllegalStateException   | 'The last update of CHG0031001 is still waiting for ProTech; change it again once ProTech has applied it'
    }

    def "an update is refused when ProTech #problem"() {
        when:
        service.update(7L, edit())

        then:
        1 * changes.load(7L) >> Optional.of(raised())
        1 * serviceNow.read(_) >> { reply() }
        _ * changes.save(_) >> { ProductionChange change -> change }
        0 * serviceNow.update(_)
        def refused = thrown(failure)
        refused.message == message

        where:
        problem                  | reply                                                                     || failure               | message
        'has closed the change'  | { [CHG0031001: raised(state: CLOSED)] }                                   || IllegalStateException | 'CHG0031001 is closed in ProTech and can no longer be changed'
        'changed its texts'      | { [CHG0031001: raised(description: 'Edited in ProTech')] }               || IllegalStateException | STALE_VERSION
        'added a change task'    | { [CHG0031001: raised(tasks: raised().tasks() + NEW_TASK.numbered('CTASK0041009'))] } || IllegalStateException | STALE_VERSION
        'cannot be reached'      | { throw new UncheckedIOException('Read timed out', new IOException()) }  || UncheckedIOException  | 'Read timed out'
    }

    def "an update with problems is refused with every problem and its path before it is published"() {
        given:
        def stored = raised()

        when:
        service.update(7L, edit(shortDescription: ' ', schedule: schedule(installationStart: '2026-10-07T09:00:00Z'),
                tasks: [stored.tasks()[0], stored.tasks()[0], new ChangeTask('CTASK9', 'Other', 'Other task.', OPEN)]))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * serviceNow.read(_) >> [CHG0031001: stored]
        0 * serviceNow.update(_)
        0 * changes.save(_)
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('shortDescription', 'is required'),
                               new FieldProblem('schedule.installationStart', 'must be in the future'),
                               new FieldProblem('tasks[1].number', 'is listed more than once'),
                               new FieldProblem('tasks[2].number', 'is not a change task of CHG0031001')]
    }

    def "an opened change another request synced in the meantime is shown as that request stored it"() {
        given:
        def theirs = raised(shortDescription: 'Renamed in ProTech', version: 1L)

        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(raised())
        1 * serviceNow.read(_) >> [CHG0031001: raised(shortDescription: 'Renamed in ProTech')]
        1 * changes.save(_) >> { throw staleVersion() }

        then:
        1 * changes.load(7L) >> Optional.of(theirs)
        0 * changes._
        loaded == theirs
    }

    def "a published update is stored on the latest version when a sync stored the change in the meantime"() {
        when:
        def updated = service.update(7L, edit(shortDescription: 'Renamed'))

        then:
        1 * changes.load(7L) >> Optional.of(raised())
        1 * serviceNow.read(_) >> [CHG0031001: raised()]
        1 * changes.synced(7L, NOW)

        then:
        1 * serviceNow.update(_)

        then:
        1 * serviceNow.read(_) >> [CHG0031001: raised(shortDescription: 'Renamed')]
        1 * changes.save({ it.version() == 0L }) >> { throw staleVersion() }

        then:
        1 * changes.load(7L) >> Optional.of(raised(version: 1L))

        then:
        1 * changes.save({ it.version() == 1L && it.shortDescription() == 'Renamed' }) >>
                { ProductionChange change -> change.toBuilder().version(2L).build() }
        0 * changes._
        updated.version() == 2L
        updated.update().status() == APPLIED
    }

    def "a refusal of ProTech is passed on and nothing is stored"() {
        when:
        service.update(7L, edit(shortDescription: 'Renamed'))

        then:
        1 * changes.load(7L) >> Optional.of(raised())
        1 * serviceNow.read(_) >> [CHG0031001: raised()]
        1 * serviceNow.update(_) >> { throw new IllegalStateException('ProTech does not change a closed change') }
        0 * changes.save(_)
        def refused = thrown(IllegalStateException)
        refused.message == 'ProTech does not change a closed change'
    }

    static ChangeCommand command(Map changes = [:]) {
        Map values = [productId: 1L, fixVersion: FIX_VERSION, epicKeys: ['CERT-1', 'CERT-5'],
                      storyKeys: ['CERT-2', 'CERT-6'], schedule: schedule(), template: template(), tasks: tasks()] +
                changes
        new ChangeCommand(values.productId as long, values.fixVersion as String, values.epicKeys as List,
                values.storyKeys as List, values.schedule as ChangeSchedule, values.template as ChangeTemplate,
                values.tasks as List, values.shortDescription as String, values.description as String)
    }

    static ChangeEditCommand edit(Map changes = [:]) {
        ProductionChange stored = raised()
        Map values = [version: 0L, departmentId: 3L, shortDescription: stored.shortDescription(),
                      description: stored.description(), schedule: stored.schedule(), template: stored.template(),
                      tasks: stored.tasks()] + changes
        new ChangeEditCommand(values.version as Long, values.departmentId as Long, values.shortDescription as String,
                values.description as String, values.schedule as ChangeSchedule, values.template as ChangeTemplate,
                values.tasks as List)
    }
}
