package com.bbh.itss.beadle.application.change

import com.bbh.itss.beadle.application.AuditNotes
import com.bbh.itss.beadle.application.AuditNotes.Stored
import com.bbh.itss.beadle.application.change.port.in.ChangeCommand
import com.bbh.itss.beadle.application.change.port.in.ChangeEditCommand
import com.bbh.itss.beadle.application.change.port.in.ChangeIntegrations
import com.bbh.itss.beadle.application.change.port.in.ChangeTasksCommand
import com.bbh.itss.beadle.application.change.port.in.ReminderCommand
import com.bbh.itss.beadle.application.change.port.in.SecureCodingCommand
import com.bbh.itss.beadle.application.change.port.out.ChangeProductsPort
import com.bbh.itss.beadle.application.change.port.out.CyberTrackPort
import com.bbh.itss.beadle.application.change.port.out.JiraPort
import com.bbh.itss.beadle.application.change.port.out.ProTechPort
import com.bbh.itss.beadle.application.change.port.out.ProTechPort.CreatedChange
import com.bbh.itss.beadle.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.domain.audit.ActionObject
import com.bbh.itss.beadle.domain.change.ApprovalRef
import com.bbh.itss.beadle.domain.change.ChangeApproval
import com.bbh.itss.beadle.domain.change.ChangeSchedule
import com.bbh.itss.beadle.domain.change.ChangeTask
import com.bbh.itss.beadle.domain.change.ChangeTemplate
import com.bbh.itss.beadle.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.beadle.domain.change.ChangeTemplate.Timing
import com.bbh.itss.beadle.domain.change.ChangeUpdate
import com.bbh.itss.beadle.domain.change.JiraVersion
import com.bbh.itss.beadle.domain.change.ProductionChange
import com.bbh.itss.beadle.domain.change.Reminder
import com.bbh.itss.beadle.domain.change.SecureCoding
import com.bbh.itss.beadle.domain.change.SecureCodingTicket
import com.bbh.itss.beadle.domain.change.WorkflowStep
import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate

import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_SYNCED
import static com.bbh.itss.beadle.domain.audit.ObjectType.CHANGE
import static com.bbh.itss.beadle.domain.change.ApprovalRole.BUSINESS
import static com.bbh.itss.beadle.domain.change.ApprovalRole.SUPPORT
import static com.bbh.itss.beadle.domain.change.ApprovalState.APPROVED
import static com.bbh.itss.beadle.domain.change.ApprovalState.NOT_APPROVED
import static com.bbh.itss.beadle.domain.change.ApprovalState.REQUESTED
import static com.bbh.itss.beadle.domain.change.ChangeSchedule.UNPLANNED
import static com.bbh.itss.beadle.domain.change.ChangeState.BUSINESS_APPROVAL
import static com.bbh.itss.beadle.domain.change.ChangeState.CLOSED
import static com.bbh.itss.beadle.domain.change.ChangeState.DRAFT
import static com.bbh.itss.beadle.domain.change.ChangeState.SUPPORT_APPROVAL
import static com.bbh.itss.beadle.domain.change.ChangeTemplate.JIRA_KEY_MESSAGE
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.NOT_APPLIED_MESSAGE
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.Status.APPLIED
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.Status.NOT_APPLIED
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.Status.PENDING
import static com.bbh.itss.beadle.domain.change.ChangeUpdate.requested
import static com.bbh.itss.beadle.domain.change.TaskState.OPEN
import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.domain.shared.Failures.staleVersion
import static com.bbh.itss.beadle.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.beadle.support.ChangeFixtures.CREATED
import static com.bbh.itss.beadle.support.ChangeFixtures.changeProduct
import static com.bbh.itss.beadle.support.ChangeFixtures.changeUrl
import static com.bbh.itss.beadle.support.ChangeFixtures.ctask
import static com.bbh.itss.beadle.support.ChangeFixtures.details
import static com.bbh.itss.beadle.support.ChangeFixtures.epic
import static com.bbh.itss.beadle.support.ChangeFixtures.issued
import static com.bbh.itss.beadle.support.ChangeFixtures.releaseTask
import static com.bbh.itss.beadle.support.ChangeFixtures.schedule
import static com.bbh.itss.beadle.support.ChangeFixtures.secureCoding
import static com.bbh.itss.beadle.support.ChangeFixtures.story
import static com.bbh.itss.beadle.support.ChangeFixtures.task
import static com.bbh.itss.beadle.support.ChangeFixtures.template
import static java.time.Clock.fixed
import static java.time.ZoneOffset.UTC

class ProductionChangeServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')
    static final List ISSUES = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail'),
                                story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record it', 'CERT-5')]
    static final ChangeTask NEW_TASK = ctask(null, 'Check the audit trail', 'Open the audit trail.', null)
    static final String CLOSED_REFUSAL = 'ProTech does not change a closed change'
    static final String PENDING_REFUSAL = 'The last update of CHG0031001 is still waiting for ProTech; change it' +
            ' again once ProTech has applied it'

    ChangeProductsPort products = Stub()
    ProductionChangeRepositoryPort changes = Mock()
    JiraPort jira = Mock()
    ProTechPort proTech = Mock()
    CyberTrackPort cyberTrack = Mock()
    SignedInUserPort users = Stub() {
        name() >> 'Mateusz Matan'
    }
    def service = new ProductionChangeService(products, changes, jira, proTech, cyberTrack, users, fixed(NOW, UTC))
    def certScanner = changeProduct(jiraProjectKey: 'CSCAN')

    def setup() {
        products.get(1L) >> certScanner
        products.get(2L) >> changeProduct(id: 2L, code: 'PAYHUB', name: 'PayHub', businessServiceId: 4L,
                businessServiceName: 'Custody')
        products.get(3L) >> changeProduct(id: 3L, businessServiceId: null, businessServiceName: null)
        jira.versions('CERT') >> [new JiraVersion(FIX_VERSION, false, LocalDate.parse('2026-10-20')),
                                  new JiraVersion('CERT 4.1', true, LocalDate.parse('2026-09-01'))]
        jira.versions(_) >> []
        jira.epics('CERT', _) >> { project, version -> version == FIX_VERSION ? ISSUES.take(2) : [] }
        jira.stories('CERT', _, _) >> { project, version, Collection epics ->
            version == FIX_VERSION ? ISSUES.drop(2).findAll { it.epicKey() in epics } : []
        }
    }

    def "the preview drafts the change of the product's business service with the chosen Jira issues and no tasks yet"() {
        when:
        def draft = service.preview(command())

        then:
        draft == ProductionChange.draft(certScanner, 'Mateusz Matan', FIX_VERSION, schedule(), template(),
                ISSUES.take(2), ISSUES.drop(2), null, null)
        draft.businessServiceId() == 3L
        draft.openedBy() == 'Mateusz Matan'
        [draft.template().requestedFor(), draft.template().requestedBy(), draft.template().assignedTo(),
         draft.template().businessService()] == ['Mateusz Matan', 'Mateusz Matan', 'Mateusz Matan', 'Corporate Technology']
        draft.tasks() == []
        draft.template().release() == FIX_VERSION
        draft.state() == DRAFT
        draft.workflow() == []
        0 * changes._
        0 * proTech._
    }

    def "a product without stored ProTech defaults creates a change from the request in its business service"() {
        given:
        def payHub = template(jiraProjectKey: 'PAY', configurationItem: 'PayHub')
        jira.epics('PAY', 'PAY 1.0') >> [epic('PAY-1', 'Instant payments')]

        when:
        def preview = service.preview(command(productId: 2L, template: payHub, epicKeys: ['PAY-1'], storyKeys: [],
                fixVersion: ' PAY 1.0 '))

        then:
        preview.productCode() == 'PAYHUB'
        preview.businessServiceId() == 4L
        preview.businessServiceName() == 'Custody'
        preview.fixVersion() == 'PAY 1.0'
        preview.template() == payHub.releasedAs('PAY 1.0').openedBy('Mateusz Matan', 'Custody')
        preview.shortDescription() == 'PayHub PAY 1.0: Instant payments'
        preview.description().startsWith('Production release PAY 1.0 of PayHub (PAYHUB) in Custody.')
        !preview.description().contains('Change tasks')
    }

    def "a change of a product outside every business service is refused on the product when it is #action"() {
        when:
        call(service)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('productId',
                'the product must be placed in a business service in Beadle Admin first')]
        0 * proTech._
        0 * changes._

        where:
        action      | call
        'previewed' | { ProductionChangeService it -> it.preview(command(productId: 3L)) }
        'created'    | { ProductionChangeService it -> it.create(command(productId: 3L)) }
    }

    def "a created change is filed in ProTech without tasks and stored with its number, its draft stage and its sync time"() {
        when:
        def created = service.create(command(storyKeys: [], shortDescription: 'Mine'))

        then:
        1 * proTech.create({ it.number() == null && it.shortDescription() == 'Mine' && it.createdAt() == NOW }) >>
                new CreatedChange('CHG0012345', changeUrl('CHG0012345'))
        1 * changes.save({ it.number() == 'CHG0012345' }) >> { ProductionChange change -> change }
        0 * proTech._
        created.number() == 'CHG0012345'
        created.tasks() == []
        created.businessServiceId() == 3L
        created.businessServiceName() == 'Corporate Technology'
        created.state() == DRAFT
        created.workflow() == [new WorkflowStep(DRAFT, NOW)]
        created.syncedAt() == NOW
        created.createdAt() == NOW
        created.update() == null
        created.url() == changeUrl('CHG0012345')
        created.fixVersion() == FIX_VERSION
        created.schedule() == schedule()
    }

    def "a FixVersion is drafted with Jira's own spelling of it, or as typed when the project lists no such version"() {
        when:
        def draft = service.preview(command(fixVersion: typed, storyKeys: []))

        then:
        1 * jira.epics('CERT', stored) >> ISSUES.take(2)
        draft.fixVersion() == stored
        draft.template().release() == stored

        where:
        typed        || stored
        'cert 4.2'   || FIX_VERSION
        ' Cert 4.2 ' || FIX_VERSION
        'CERT 4.3'   || 'CERT 4.3'
    }

    def "#refusal is refused with every problem before anything is created"() {
        when:
        service.create(command(edits))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == problems.collect { new FieldProblem(it.key, it.value) }
        0 * proTech._
        0 * changes._

        where:
        refusal                         | edits                                              || problems
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

    def "the preview allows an installation start in the past, the create does not"() {
        given:
        def past = command(schedule: schedule(installationStart: '2026-10-01T06:00:00Z'))

        when:
        def preview = service.preview(past)

        then:
        preview.schedule().installationStart() == Instant.parse('2026-10-01T06:00:00Z')

        when:
        service.create(past)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('schedule.installationStart', 'must be in the future')]
    }

    def "the preview writes the texts before the schedule is #planned, the create refuses it"() {
        given:
        def unplanned = command(schedule: schedule)

        when:
        def preview = service.preview(unplanned)

        then:
        preview.schedule() == UNPLANNED
        preview.description().contains('Installation not planned yet, post-install validation not planned yet,' +
                ' first usage not planned yet. No downtime.')

        when:
        service.create(unplanned)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems()*.field().every { it.startsWith('schedule') }
        0 * proTech._

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

    def "the integrations say whether Jira, ProTech and CyberTrack are connected and whether their services answered"() {
        given:
        jira.reachable() >> jiraAnswered
        cyberTrack.reachable() >> cyberTrackAnswered
        proTech.reachable() >> proTechAnswered

        when:
        def integrations = service.integrations()

        then:
        1 * jira.jiraConnected() >> false
        1 * proTech.proTechConnected() >> true
        1 * cyberTrack.cyberTrackConnected() >> false
        integrations == new ChangeIntegrations(false, true, false, internal, proTechAnswered)

        where:
        jiraAnswered | cyberTrackAnswered | proTechAnswered || internal
        true         | true               | true            || true
        true         | false              | false           || false
        false        | true               | true            || false
    }

    def "the list syncs the open changes with one ProTech read, saves the changed ones and times the rest at once"() {
        given:
        def unchanged = issued()
        def quiet = issued(id: 9L, number: 'CHG0031003')
        def moved = issued(id: 8L, number: 'CHG0031002')
        def closed = issued(id: 6L, number: 'CHG0031000', state: CLOSED)
        def progressed = moved.toBuilder().state(BUSINESS_APPROVAL)
                .workflow(moved.workflow() + new WorkflowStep(BUSINESS_APPROVAL, CREATED.plusSeconds(120))).build()

        when:
        def listed = service.list(3L)

        then:
        1 * changes.findByBusinessService(3L) >> [quiet, moved, unchanged, closed]
        1 * proTech.read([quiet, moved, unchanged]) >> [CHG0031001: unchanged, CHG0031002: progressed,
                                                           CHG0031003: quiet]
        1 * changes.save(progressed.toBuilder().syncedAt(NOW).build()) >>
                { ProductionChange change -> change.toBuilder().version(1L).build() }
        1 * changes.synced([9L, 7L], NOW)
        0 * changes._
        0 * proTech._
        listed*.number() == ['CHG0031003', 'CHG0031002', 'CHG0031001', 'CHG0031000']
        listed[0] == quiet.toBuilder().syncedAt(NOW).build()
        listed[1].state() == BUSINESS_APPROVAL
        listed[1].version() == 1L
        listed[2] == unchanged.toBuilder().syncedAt(NOW).build()
        listed[3].is(closed)
    }

    def "a list without open changes does not ask ProTech"() {
        given:
        def closed = issued(state: CLOSED)

        when:
        def listed = service.list(null)

        then:
        1 * changes.findAll() >> [closed]
        0 * changes._
        0 * proTech._
        listed == [closed]
    }

    def "#action keeps the stored values when ProTech #problem"() {
        given:
        def closed = issued(id: 6L, number: 'CHG0031000', state: CLOSED)
        changes.findAll() >> [issued(), closed]
        changes.load(7L) >> Optional.of(issued())

        when:
        def shown = shownBy(service)

        then:
        1 * proTech.read([issued()]) >> { reply() }
        0 * changes.save(_)
        0 * changes.synced(*_)
        shown == (listed ? [issued(syncProblem: message), closed] : issued(syncProblem: message))

        where:
        action               | listed | shownBy                 | problem             | reply                                                                       || message
        'the list'           | true   | { it.list(null) }       | 'cannot be reached' | { throw new UncheckedIOException('Connection refused', new IOException()) } || 'ProTech could not be reached: Connection refused.'
        'the list'           | true   | { it.list(null) }       | 'does not hold it'  | { [:] }                                                                     || 'ProTech has no change CHG0031001.'
        'an opened change'   | false  | { it.get(7L) }          | 'cannot be reached' | { throw new UncheckedIOException('Read timed out', new IOException()) }    || 'ProTech could not be reached: Read timed out.'
        'an opened change'   | false  | { it.get(7L) }          | 'does not hold it'  | { [:] }                                                                     || 'ProTech has no change CHG0031001.'
        'the list'           | true   | { it.list(null) }       | 'refuses the read'  | { throw new NoSuchElementException('Beadle-ProTech refused to read the changes (404)') } || 'ProTech refused the read: Beadle-ProTech refused to read the changes (404).'
        'an opened change'   | false  | { it.get(7L) }          | 'refuses the read'  | { throw InvalidRequestException.of('number', 'is not a ProTech change number') } || 'ProTech refused the read: is not a ProTech change number.'
        'an opened change'   | false  | { it.get(7L) }          | 'refuses the read'  | { throw new IllegalStateException('Beadle-ProTech refused to read the changes (409)') } || 'ProTech refused the read: Beadle-ProTech refused to read the changes (409).'
    }

    def "a change #action is read from ProTech and saved only when ProTech #changed it"() {
        given:
        changes.load(7L) >> Optional.of(stored)
        changes.findByNumber('CHG0031001') >> Optional.of(stored)

        when:
        ProductionChange read = reading(service)

        then:
        1 * proTech.read([stored]) >> [CHG0031001: remote]
        saves * changes.save(remote.toBuilder().syncedAt(NOW).build()) >>
                { ProductionChange change -> change.toBuilder().version(1L).build() }
        (1 - saves) * changes.synced([7L], NOW)
        read.state() == remote.state()
        read.shortDescription() == remote.shortDescription()
        read.syncedAt() == NOW
        read.syncProblem() == null
        read.version() == saves

        where:
        action                     | reading                                 | stored                | remote                                                        || saves
        'opened, even when closed' | { it.get(7L) }                          | issued(state: CLOSED) | issued(state: CLOSED)                                         || 0
        'opened, even when closed' | { it.get(7L) }                          | issued(state: CLOSED) | issued(state: CLOSED, shortDescription: 'Renamed in ProTech') || 1
        'ProTech moved'            | { it.resync('CHG0031001').get() }       | issued()              | issued()                                                      || 0
        'ProTech moved'            | { it.resync('CHG0031001').get() }       | issued()              | issued(state: BUSINESS_APPROVAL, workflow: [new WorkflowStep(DRAFT, CREATED), new WorkflowStep(BUSINESS_APPROVAL, NOW)]) || 1

        changed = saves ? 'changed' : 'did not change'
    }

    def "a change Beadle did not create is not read again, and an unreachable ProTech fails the read"() {
        when:
        def unknown = service.resync('CHG0099999')

        then:
        1 * changes.findByNumber('CHG0099999') >> Optional.empty()
        0 * proTech._
        unknown.empty

        when:
        service.resync('CHG0031001')

        then:
        1 * changes.findByNumber('CHG0031001') >> Optional.of(issued())
        1 * proTech.read(_) >> { throw new UncheckedIOException('Beadle-ProTech did not answer', new IOException()) }
        thrown(UncheckedIOException)
    }

    def "a pending update is #status when the change is opened #age seconds after it was published"() {
        given:
        def requested = issued(shortDescription: 'Renamed', update: new ChangeUpdate(PENDING, NOW.minusSeconds(age),
                'Corporate Technology', ['shortDescription'], null, NOW.minusSeconds(age)))

        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(requested)
        1 * proTech.read([requested]) >> [CHG0031001: issued(state: BUSINESS_APPROVAL)]
        1 * changes.save(_) >> { ProductionChange change -> change }
        loaded.update() == new ChangeUpdate(status, NOW.minusSeconds(age), 'Corporate Technology',
                ['shortDescription'], message, NOW)
        loaded.shortDescription() == shown
        loaded.state() == BUSINESS_APPROVAL

        where:
        age | status      || shown                           | message
        59  | PENDING     || 'Renamed'                       | null
        60  | NOT_APPLIED || issued().shortDescription()     | NOT_APPLIED_MESSAGE
    }

    def "a pending update ProTech still has not applied is only timed when nothing else is new"() {
        given:
        def waiting = issued(shortDescription: 'Renamed', update: new ChangeUpdate(PENDING, NOW.minusSeconds(9),
                'Corporate Technology', ['shortDescription'], null, NOW.minusSeconds(3)))

        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(waiting)
        1 * proTech.read([waiting]) >> [CHG0031001: issued()]
        1 * changes.synced([7L], NOW)
        0 * changes._
        loaded == waiting.toBuilder().syncedAt(NOW).update(new ChangeUpdate(PENDING, NOW.minusSeconds(9),
                'Corporate Technology', ['shortDescription'], null, NOW)).build()
    }

    def "an unknown change is not found when it is #action"() {
        given:
        changes.load(8L) >> Optional.empty()

        when:
        call(service)

        then:
        thrown(NoSuchElementException)
        0 * proTech._

        where:
        action    | call
        'opened'  | { ProductionChangeService it -> it.get(8L) }
        'updated' | { ProductionChangeService it -> it.update(8L, edit()) }
    }

    def "an update its business service claims, publishes to ProTech, verifies as applied and stores"() {
        given:
        def stored = issued()
        def applied = issued(shortDescription: 'Renamed', state: BUSINESS_APPROVAL,
                tasks: stored.tasks() + NEW_TASK.withNumber('CTASK0041003'))

        when:
        def updated = service.update(7L, edit(shortDescription: ' Renamed ', tasks: stored.tasks() + NEW_TASK))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read([stored]) >> [CHG0031001: stored]
        1 * changes.synced([7L], NOW)

        then:
        1 * changes.save({ ProductionChange it ->
            it.version() == 0L && it.shortDescription() == 'Renamed' && it.update() == requested(NOW, 'Corporate Technology') &&
                    it.tasks()*.number() == ['CTASK0041001', 'CTASK0041002', null] && it.tasks()*.state() == [OPEN] * 3
        }) >> { ProductionChange change -> change.toBuilder().version(1L).build() }

        then:
        1 * proTech.update({ ProductionChange it -> it.version() == 1L && it.shortDescription() == 'Renamed' })

        then:
        1 * proTech.read({ it*.update()*.status() == [PENDING] }) >> [CHG0031001: applied]
        1 * changes.save({ it.version() == 1L }) >> { ProductionChange change -> change.toBuilder().version(2L).build() }
        0 * changes._
        0 * proTech._
        updated.shortDescription() == 'Renamed'
        updated.tasks()*.number() == ['CTASK0041001', 'CTASK0041002', 'CTASK0041003']
        updated.state() == BUSINESS_APPROVAL
        updated.update() == new ChangeUpdate(APPLIED, NOW, 'Corporate Technology', [], null, NOW)
        updated.syncedAt() == NOW
        updated.syncProblem() == null
        updated.version() == 2L
    }

    def "an update read before ProTech only moved the change through its workflow is published"() {
        given:
        def stored = issued(state: BUSINESS_APPROVAL, version: 4L, editedVersion: 2L)
        changes.load(7L) >> Optional.of(stored)
        proTech.read(_) >> [CHG0031001: stored]

        when:
        def updated = service.update(7L, edit(version: 3L, shortDescription: 'Renamed'))

        then:
        1 * changes.save({ it.version() == 4L }) >> { ProductionChange change -> change.toBuilder().version(5L).build() }
        1 * proTech.update({ it.shortDescription() == 'Renamed' })
        1 * changes.save({ it.version() == 5L }) >> { ProductionChange change -> change.toBuilder().version(6L).build() }
        updated.version() == 6L
        updated.update().status() == PENDING
    }

    def "an update ProTech has not applied yet is stored as pending with the requested values"() {
        given:
        def stored = issued()

        when:
        def updated = service.update(7L, edit(shortDescription: 'Renamed'))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read(_) >> [CHG0031001: stored]
        1 * changes.synced([7L], NOW)
        1 * changes.save({ it.update().fields() == [] }) >> { ProductionChange change -> change }

        then:
        1 * proTech.update(_)

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(state: BUSINESS_APPROVAL)]
        1 * changes.save({ it.update().fields() == ['shortDescription'] }) >> { ProductionChange change -> change }
        updated.shortDescription() == 'Renamed'
        updated.state() == BUSINESS_APPROVAL
        updated.update() == new ChangeUpdate(PENDING, NOW, 'Corporate Technology', ['shortDescription'], null, NOW)
    }

    def "an update published to ProTech stays pending with every requested change when the read back #problem"() {
        given:
        def stored = issued()

        when:
        def updated = service.update(7L, edit(shortDescription: 'Renamed', tasks: stored.tasks() + NEW_TASK))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read(_) >> [CHG0031001: stored]
        1 * changes.synced([7L], NOW)
        1 * changes.save({ ProductionChange it -> it.syncProblem() == null }) >> { ProductionChange change -> change }

        then:
        1 * proTech.update(_)

        then:
        1 * proTech.read(_) >> { reply() }
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
        service.update(7L, edit(businessServiceId: businessServiceId, version: version))

        then:
        def refused = thrown(failure)
        refused.message == message
        0 * proTech._
        0 * changes.save(_)

        where:
        refusal                          | stored                                              | businessServiceId | version || failure                 | message
        'without a business service'           | issued()                                            | null         | 0L      || InvalidRequestException | 'choose your business service'
        'of another business service'          | issued()                                            | 4L           | 0L      || SecurityException       | 'Only Corporate Technology can change CHG0031001'
        'of another business service at an old version' | issued(version: 2L, editedVersion: 2L)     | 4L           | 1L      || SecurityException       | 'Only Corporate Technology can change CHG0031001'
        'of a change without business service' | issued(businessServiceId: null, businessServiceName: null)    | 3L           | 0L      || SecurityException       | 'No business service owns CHG0031001, so nobody can change it in Beadle'
        'at a stale version'             | issued(version: 2L, editedVersion: 2L)              | 3L           | 1L      || IllegalStateException   | STALE_VERSION
        'read before its last edit'      | issued(version: 4L, editedVersion: 3L)              | 3L           | 2L      || IllegalStateException   | STALE_VERSION
        'at a version it never had'      | issued(version: 2L)                                 | 3L           | 3L      || IllegalStateException   | STALE_VERSION
    }

    def "an update is refused without being published when ProTech #problem"() {
        when:
        service.update(7L, edit())

        then:
        1 * changes.load(7L) >> Optional.of(issued())
        1 * proTech.read(_) >> { reply() }
        _ * changes.save({ it.update() == null }) >> { ProductionChange change -> change }
        0 * changes.save({ it.update() != null })
        0 * proTech.update(_)
        def refused = thrown(failure)
        refused.message == message

        where:
        problem                  | reply                                                                     || failure               | message
        'has closed the change'  | { [CHG0031001: issued(state: CLOSED)] }                                   || IllegalStateException | 'CHG0031001 is closed in ProTech and can no longer be changed'
        'changed its texts'      | { [CHG0031001: issued(description: 'Edited in ProTech')] }               || IllegalStateException | STALE_VERSION
        'added a change task'    | { [CHG0031001: issued(tasks: issued().tasks() + NEW_TASK.withNumber('CTASK0041009'))] } || IllegalStateException | STALE_VERSION
        'no longer holds it'     | { [:] }                                                                   || IllegalStateException | 'ProTech has no change CHG0031001.'
        'cannot be reached'      | { throw new UncheckedIOException('Read timed out', new IOException()) }  || UncheckedIOException  | 'Read timed out'
    }

    def "an update ProTech applied but Beadle has not read back yet no longer blocks the next one"() {
        given:
        def waiting = issued(shortDescription: 'Renamed', update: new ChangeUpdate(PENDING, NOW.minusSeconds(5),
                'Corporate Technology', ['shortDescription'], null, NOW.minusSeconds(5)))

        when:
        def updated = service.update(7L, edit(shortDescription: 'Renamed', description: 'Edited again'))

        then:
        1 * changes.load(7L) >> Optional.of(waiting)
        1 * proTech.read(_) >> [CHG0031001: issued(shortDescription: 'Renamed')]
        1 * changes.save({ it.update().status() == APPLIED }) >>
                { ProductionChange change -> change.toBuilder().version(1L).build() }

        then:
        1 * changes.save({ it.version() == 1L && it.update() == requested(NOW, 'Corporate Technology') }) >>
                { ProductionChange change -> change.toBuilder().version(2L).build() }

        then:
        1 * proTech.update({ it.shortDescription() == 'Renamed' && it.description() == 'Edited again' })

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(shortDescription: 'Renamed', description: 'Edited again')]
        1 * changes.save({ it.version() == 2L }) >> { ProductionChange change -> change.toBuilder().version(3L).build() }
        updated.update() == new ChangeUpdate(APPLIED, NOW, 'Corporate Technology', [], null, NOW)
        updated.version() == 3L
    }

    def "an update after one ProTech #outcome is judged after the sync and refused as #refusal"() {
        given:
        def waiting = issued(shortDescription: 'Renamed', update: new ChangeUpdate(PENDING, NOW.minusSeconds(age),
                'Corporate Technology', ['shortDescription'], null, NOW.minusSeconds(age)))

        when:
        service.update(7L, edit(shortDescription: 'Renamed', description: 'Edited again'))

        then:
        1 * changes.load(7L) >> Optional.of(waiting)
        1 * proTech.read(_) >> [CHG0031001: issued()]
        _ * changes.save({ it.update().requestedAt() != NOW }) >> { ProductionChange change -> change }
        0 * changes.save({ it.update().requestedAt() == NOW })
        0 * proTech.update(_)
        def refused = thrown(IllegalStateException)
        refused.message == message

        where:
        outcome                         | age || refusal         | message
        'has not applied yet'           | 59  || 'still pending' | PENDING_REFUSAL
        'did not apply within a minute' | 60  || 'stale'         | STALE_VERSION
    }

    def "an update with problems in its texts, schedule or template is refused before ProTech is read"() {
        given:
        def stored = issued()
        changes.load(7L) >> Optional.of(stored)
        proTech.read(_) >> { throw new UncheckedIOException('Connection refused', new IOException()) }

        when:
        service.update(7L, edit(shortDescription: ' ', schedule: schedule(installationStart: '2026-10-07T09:00:00Z'),
                template: template(category: 'Software'),
                tasks: [stored.tasks()[0], stored.tasks()[0], ctask('CTASK9', ' ', 'Other task.', OPEN)]))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('shortDescription', 'is required'),
                               new FieldProblem('template.category', 'must be one of Application, Hardware, ' +
                                       'Infrastructure, System Software, Network, Telecom, Data Amendment, ' +
                                       'Desktop Software, Storage, Facilities, Other, Database'),
                               new FieldProblem('schedule.installationStart', 'must be in the future'),
                               new FieldProblem('tasks[2].details.shortDescription', 'is required')]
        0 * proTech.update(_)
        0 * changes.save(_)
    }

    def "an update with tasks ProTech does not take is refused with their paths before it is claimed"() {
        given:
        def stored = issued()

        when:
        service.update(7L, edit(tasks: [stored.tasks()[0], stored.tasks()[0],
                                         ctask('CTASK9', 'Other', 'Other task.', OPEN)]))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read(_) >> [CHG0031001: stored]
        0 * proTech.update(_)
        0 * changes.save(_)
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('tasks[1].number', 'is listed more than once'),
                               new FieldProblem('tasks[2].number', 'is not a change task of CHG0031001')]
    }

    def "of two updates read at the same version the second is refused before ProTech is asked"() {
        when:
        service.update(7L, edit(shortDescription: 'Mine'))

        then:
        1 * changes.load(7L) >> Optional.of(issued())
        1 * proTech.read(_) >> [CHG0031001: issued()]
        1 * changes.synced([7L], NOW)
        1 * changes.save({ it.version() == 0L && it.update().pending() }) >> { throw staleVersion() }
        0 * changes._
        0 * proTech.update(_)
        def refused = thrown(IllegalStateException)
        refused.message == STALE_VERSION
    }

    def "an opened change another request synced in the meantime is shown as that request stored it"() {
        given:
        def theirs = issued(shortDescription: 'Renamed in ProTech', version: 1L)

        when:
        def loaded = service.get(7L)

        then:
        1 * changes.load(7L) >> Optional.of(issued())
        1 * proTech.read(_) >> [CHG0031001: issued(shortDescription: 'Renamed in ProTech')]
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
        1 * changes.load(7L) >> Optional.of(issued())
        1 * proTech.read(_) >> [CHG0031001: issued()]
        1 * changes.synced([7L], NOW)
        1 * changes.save({ it.version() == 0L }) >> { ProductionChange change -> change.toBuilder().version(1L).build() }

        then:
        1 * proTech.update(_)

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(shortDescription: 'Renamed')]
        1 * changes.save({ it.version() == 1L }) >> { throw staleVersion() }

        then:
        1 * changes.load(7L) >> Optional.of(issued(shortDescription: 'Renamed', version: 2L))

        then:
        1 * changes.save({ it.version() == 2L && it.shortDescription() == 'Renamed' }) >>
                { ProductionChange change -> change.toBuilder().version(3L).build() }
        0 * changes._
        updated.version() == 3L
        updated.update().status() == APPLIED
    }

    def "the tasks of a created change are created in ProTech one by one against its number, stored with theirs and read back with their approvers"() {
        given:
        def stored = issued(tasks: [])
        ProductionChange recorded

        when:
        def change = service.createTasks(7L, created())

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read([stored]) >> [CHG0031001: stored]
        1 * changes.synced([7L], NOW)

        then:
        1 * proTech.createTask('CHG0031001', releaseTask([configurationItem: 'CertScanner'])) >> 'CTASK0050001'

        then:
        1 * proTech.createTask('CHG0031001', ChangeTask.of(details(2, [configurationItem: 'CertScanner']))) >>
                'CTASK0050002'

        then:
        1 * changes.save({ ProductionChange it -> it.tasks()*.number() == ['CTASK0050001', 'CTASK0050002'] }) >>
                { ProductionChange saved -> recorded = saved.toBuilder().version(1L).build() }

        then:
        1 * proTech.read({ it*.version() == [1L] }) >> { [CHG0031001: recorded.toBuilder().tasks(
                recorded.tasks().collect {
                    it.withApproval(NOT_APPROVED).withApprovers(['Rebecca Lawson', 'Thomas Ashby'])
                })
                .build()] }
        1 * changes.save({ ProductionChange it -> it.tasks()*.approvers().every { it == ['Rebecca Lawson', 'Thomas Ashby'] } }) >>
                { ProductionChange saved -> saved.toBuilder().version(2L).build() }
        0 * changes._
        0 * proTech._
        change.tasks()*.number() == ['CTASK0050001', 'CTASK0050002']
        change.tasks()[0].start() == Instant.parse('2026-10-10T06:01:00Z')
        change.tasks()*.approval() == [NOT_APPROVED] * 2
        change.tasks()*.approvers() == [['Rebecca Lawson', 'Thomas Ashby']] * 2
        change.version() == 2L
    }

    def "change tasks ProTech created are stored even when it cannot be read back just then"() {
        given:
        changes.load(7L) >> Optional.of(issued(tasks: []))

        when:
        def change = service.createTasks(7L, created())

        then:
        1 * proTech.read(_) >> [CHG0031001: issued(tasks: [])]
        2 * proTech.createTask('CHG0031001', _) >>> ['CTASK0050001', 'CTASK0050002']
        1 * changes.save(_) >> { ProductionChange saved -> saved.toBuilder().version(1L).build() }

        then:
        1 * proTech.read(_) >> { throw new UncheckedIOException('Read timed out', new IOException()) }
        0 * changes.save(_)
        change.tasks()*.number() == ['CTASK0050001', 'CTASK0050002']
        change.version() == 1L
        change.syncProblem() == 'ProTech could not be reached: Read timed out.'
    }

    def "#action is not created when #problem"() {
        given:
        changes.load(7L) >> Optional.of(stored)
        changes.save(_) >> { ProductionChange it -> it }
        proTech.read(_) >> [CHG0031001: remote]

        when:
        creating(service)

        then:
        def refused = thrown(type)
        refused.message == message
        0 * proTech.createTask(*_)
        0 * cyberTrack.createTicket(_)
        0 * proTech.update(_)

        where:
        action                   | problem                          | stored                                             | remote                           | creating                                                                            || type                    | message
        'a change task'          | 'another business service asks'  | issued(tasks: [])                                  | issued(tasks: [])                | { it.createTasks(7L, created(businessServiceId: 4L)) }                              || SecurityException       | 'Only Corporate Technology can change CHG0031001'
        'a change task'          | 'the change moved on meanwhile'  | issued(tasks: [])                                  | issued(tasks: [])                | { it.createTasks(7L, created(version: 1L)) }                                        || IllegalStateException   | STALE_VERSION
        'a change task'          | 'ProTech has closed the change'  | issued(tasks: [])                                  | issued(tasks: [], state: CLOSED) | { it.createTasks(7L, created()) }                                                   || IllegalStateException   | 'CHG0031001 is closed in ProTech and can no longer be changed'
        'a change task'          | 'a release task starts too late' | issued(tasks: [])                                  | issued(tasks: [])                | { it.createTasks(7L, created(tasks: [releaseTask([:], '2026-10-10T10:01:00Z')])) } || InvalidRequestException | 'must not be after the installation end'
        'a secure coding ticket' | 'another business service asks'  | issued()                                           | issued()                         | { it.createSecureCodingTicket(7L, ticket(businessServiceId: 4L)) }                  || SecurityException       | 'Only Corporate Technology can change CHG0031001'
        'a secure coding ticket' | 'the change moved on meanwhile'  | issued()                                           | issued()                         | { it.createSecureCodingTicket(7L, ticket(version: 1L)) }                            || IllegalStateException   | STALE_VERSION
        'a secure coding ticket' | 'ProTech has closed the change'  | issued(state: CLOSED)                              | issued(state: CLOSED)            | { it.createSecureCodingTicket(7L, ticket()) }                                       || IllegalStateException   | 'CHG0031001 is closed in ProTech and can no longer be changed'
        'a secure coding ticket' | 'the change has its ticket'      | issued().withSecureCoding(secureCoding(), 'SCP-7') | stored                           | { it.createSecureCodingTicket(7L, ticket()) }                                       || IllegalStateException   | 'CHG0031001 already has the secure coding ticket SCP-7'
        'a secure coding ticket' | 'an input is missing'            | issued()                                           | issued()                         | { it.createSecureCodingTicket(7L, ticket(apoNumber: ' ')) }                         || InvalidRequestException | 'is required'
    }

    def "the tasks ProTech created before it refused one are stored, noted for the audit trail and the refusal is passed on"() {
        given:
        changes.load(7L) >> Optional.of(issued(tasks: []))
        proTech.read(_) >> [CHG0031001: issued(tasks: [])]

        when:
        def notes = AuditNotes.listen()
        notes.withCloseable { service.createTasks(7L, created()) }

        then:
        1 * proTech.createTask('CHG0031001', _) >> 'CTASK0050001'

        then:
        1 * proTech.createTask('CHG0031001', _) >> { throw new IllegalStateException(CLOSED_REFUSAL) }

        then:
        1 * changes.save({ ProductionChange it -> it.tasks()*.number() == ['CTASK0050001'] }) >> { it[0] }
        def refused = thrown(IllegalStateException)
        refused.message == CLOSED_REFUSAL
        notes.created() == ['CTASK0050001']
    }

    def "a refusal of ProTech puts the stored change back and is passed on"() {
        when:
        service.update(7L, edit(shortDescription: 'Renamed'))

        then:
        1 * changes.load(7L) >> Optional.of(issued())
        1 * proTech.read(_) >> [CHG0031001: issued()]
        1 * changes.synced([7L], NOW)
        1 * changes.save({ it.shortDescription() == 'Renamed' && it.update().pending() }) >>
                { ProductionChange change -> change.toBuilder().version(1L).build() }

        then:
        1 * proTech.update(_) >> { throw new IllegalStateException('ProTech does not change a closed change') }

        then:
        1 * changes.save(issued(version: 1L, syncedAt: NOW)) >> { ProductionChange change -> change.toBuilder().version(2L).build() }
        0 * changes._
        def refused = thrown(IllegalStateException)
        refused.message == 'ProTech does not change a closed change'
    }

    def "a secure coding ticket is created in CyberTrack from its inputs and published to ProTech as the ticket of the change"() {
        given:
        def stored = issued()
        def ticketed = stored.withSecureCoding(secureCoding(), 'SCP-1001')

        when:
        def change = service.createSecureCodingTicket(7L, ticket(implementationDate: ' 10102026 '))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read([stored]) >> [CHG0031001: stored]
        1 * changes.synced([7L], NOW)

        then:
        1 * cyberTrack.createTicket(new SecureCodingTicket('CHG0031001', 'CertScanner', LocalDate.parse('2026-10-10'),
                secureCoding())) >>
                'SCP-1001'

        then:
        1 * changes.save({ ProductionChange it ->
            it.template() == ticketed.template() && it.update() == requested(NOW, 'Corporate Technology')
        }) >> { ProductionChange saved -> saved.toBuilder().version(1L).build() }

        then:
        1 * proTech.update({ ProductionChange it -> it.template().secureCodingTicket() == 'SCP-1001' })

        then:
        1 * proTech.read(_) >> [CHG0031001: ticketed]
        1 * changes.save({ it.version() == 1L }) >> { ProductionChange saved -> saved.toBuilder().version(2L).build() }
        0 * changes._
        0 * proTech._
        0 * cyberTrack._
        change.template().secureCodingTicket() == 'SCP-1001'
        change.template().secureCoding() == secureCoding()
        change.update() == new ChangeUpdate(APPLIED, NOW, 'Corporate Technology', [], null, NOW)
        change.version() == 2L
    }

    def "every missing or broken input of a secure coding ticket is refused against its field before CyberTrack is asked"() {
        given:
        changes.load(7L) >> Optional.of(issued())

        when:
        service.createSecureCodingTicket(7L, ticket(apoNumber: null, implementationDate: '13012026',
                bitbucketUrl: 'bitbucket.bbh.com/projects/CERT', artifactLink: null, qcApplicationLink: 'ftp://cert'))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('apoNumber', 'is required'),
                               new FieldProblem('artifactLink', 'is required'),
                               new FieldProblem('bitbucketUrl', SecureCoding.LINK_MESSAGE),
                               new FieldProblem('qcApplicationLink', SecureCoding.LINK_MESSAGE),
                               new FieldProblem('implementationDate', SecureCodingTicket.DATE_MESSAGE)]
        0 * proTech._
        0 * cyberTrack._
    }

    def "a ticket CyberTrack created but ProTech refused is noted and named in the refusal so it can be entered by hand"() {
        given:
        changes.load(7L) >> Optional.of(issued())
        changes.save(_) >> { ProductionChange it -> it }
        proTech.read(_) >> [CHG0031001: issued()]
        cyberTrack.createTicket(_) >> 'SCP-1002'

        when:
        def notes = AuditNotes.listen()
        notes.withCloseable { service.createSecureCodingTicket(7L, ticket()) }

        then:
        1 * proTech.update(_) >> { throw new IllegalStateException(CLOSED_REFUSAL) }
        def refused = thrown(IllegalStateException)
        refused.message == 'SCP-1002 was created in CyberTrack, but CHG0031001 could not take it: ' + CLOSED_REFUSAL +
                ' Enter SCP-1002 as the secure coding ticket with Edit the change.'
        refused.cause.message == CLOSED_REFUSAL
        notes.created() == ['SCP-1002']
    }

    def "a CyberTrack failure is passed on and leaves the change as it was"() {
        given:
        changes.load(7L) >> Optional.of(issued())
        proTech.read(_) >> [CHG0031001: issued()]

        when:
        service.createSecureCodingTicket(7L, ticket())

        then:
        1 * cyberTrack.createTicket(_) >> { throw new UncheckedIOException('CyberTrack is down', new IOException()) }
        0 * changes.save(_)
        0 * proTech.update(_)
        def refused = thrown(UncheckedIOException)
        refused.message == 'CyberTrack is down'
    }

    static ChangeCommand command(Map changes = [:]) {
        Map values = [productId: 1L, fixVersion: FIX_VERSION, epicKeys: ['CERT-1', 'CERT-5'],
                      storyKeys: ['CERT-2', 'CERT-6'], schedule: schedule(), template: template()] + changes
        new ChangeCommand(values.productId as long, values.fixVersion as String, values.epicKeys as List,
                values.storyKeys as List, values.schedule as ChangeSchedule, values.template as ChangeTemplate,
                values.shortDescription as String, values.description as String)
    }

    def "a reminder to everyone goes through ProTech to each approver who has not approved and is stored with whom it reached"() {
        when:
        def reminded = service.remind(7L, new ReminderCommand(3L, null, null))

        then:
        1 * changes.load(7L) >> Optional.of(issued())
        1 * proTech.read(_) >> [CHG0031001: awaiting()]
        1 * changes.save({ ProductionChange it -> it.state() == SUPPORT_APPROVAL && it.syncedAt() == NOW }) >>
                { ProductionChange it -> it.toBuilder().version(1L).build() }

        then:
        1 * proTech.remind('CHG0031001', ApprovalRef.of(SUPPORT)) >> ['Jane Smith']
        1 * proTech.remind('CHG0031001', ApprovalRef.ofTask('CTASK0041002')) >> ['Ann Lee', 'Grace Turner']

        then:
        1 * changes.save({ ProductionChange it -> it.version() == 1L }) >>
                { ProductionChange it -> it.toBuilder().version(2L).build() }
        0 * changes._
        0 * proTech._
        reminded.version() == 2L
        reminded.approvals()*.reminder() == [null, null, null, new Reminder(NOW, ['Jane Smith'])]
        reminded.tasks()*.reminder() == [null, new Reminder(NOW, ['Ann Lee', 'Grace Turner'])]
        reminded.approvals()*.state() == [APPROVED, APPROVED, APPROVED, REQUESTED]
    }

    def "a reminder to one approval reaches only its approvers and keeps the reminders sent before"() {
        given:
        def earlier = new Reminder(NOW.minusSeconds(600), ['Jane Smith'])
        def stored = awaiting().reminded([(ApprovalRef.of(SUPPORT)): earlier])

        when:
        def reminded = service.remind(7L, new ReminderCommand(3L, null, 'CTASK0041002'))

        then:
        1 * changes.load(7L) >> Optional.of(stored)
        1 * proTech.read(_) >> [CHG0031001: awaiting()]
        1 * proTech.remind('CHG0031001', ApprovalRef.ofTask('CTASK0041002')) >> ['Ann Lee', 'Grace Turner']
        1 * changes.save(_) >> { ProductionChange it -> it }
        1 * changes.synced([7L], NOW)
        0 * changes._
        0 * proTech._
        reminded.approvals()*.reminder() == [null, null, null, earlier]
        reminded.tasks()*.reminder() == [null, new Reminder(NOW, ['Ann Lee', 'Grace Turner'])]
        reminded.editedVersion() == stored.editedVersion()
    }

    def "no reminder is sent when #problem"() {
        given:
        changes.load(7L) >> Optional.of(stored)
        changes.load(8L) >> Optional.empty()
        changes.save(_) >> { ProductionChange it -> it }
        proTech.read(_) >> { reply() }

        when:
        service.remind(id, command)

        then:
        def refused = thrown(type)
        refused.message == message
        0 * proTech.remind(*_)

        where:
        problem                        | id | stored                                         | command                                     | reply                               || type                    | message
        'the change is unknown'        | 8L | issued()                                       | new ReminderCommand(3L, null, null)         | { [:] }                             || NoSuchElementException  | 'Change 8 does not exist'
        'no business service is chosen'      | 7L | issued()                                       | new ReminderCommand(null, null, null)       | { [CHG0031001: awaiting()] }        || InvalidRequestException | 'choose your business service'
        'another business service asks'      | 7L | issued()                                       | new ReminderCommand(4L, null, null)         | { [CHG0031001: awaiting()] }        || SecurityException       | 'Only Corporate Technology can remind the approvers of CHG0031001'
        'no business service owns it'        | 7L | issued(businessServiceId: null, businessServiceName: null) | new ReminderCommand(3L, null, null)       | { [CHG0031001: awaiting()] }        || SecurityException       | 'No business service owns CHG0031001, so nobody can remind the approvers of it in Beadle'
        'ProTech cannot be read'       | 7L | issued()                                       | new ReminderCommand(3L, null, null)         | { throw new UncheckedIOException('Read timed out', new IOException()) } || UncheckedIOException | 'Read timed out'
        'ProTech closed it'            | 7L | issued()                                       | new ReminderCommand(3L, null, null)         | { [CHG0031001: issued(state: CLOSED)] } || IllegalStateException | 'CHG0031001 is closed in ProTech, so nobody is reminded to approve it'
        'the approver already approved' | 7L | issued()                                      | new ReminderCommand(3L, BUSINESS, null)     | { [CHG0031001: awaiting()] }        || IllegalStateException   | 'The business approver already approved CHG0031001'
    }

    def "a reminder to both the change and one of its tasks is refused before ProTech is read"() {
        given:
        changes.load(7L) >> Optional.of(awaiting())

        when:
        service.remind(7L, new ReminderCommand(3L, BUSINESS, 'CTASK0041002'))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('task', 'Remind the approvers of either the change or one change task')]
        0 * proTech._
        0 * changes.save(_)
    }

    def "the newer state #action stored on its way is noted for the audit trail as synced"() {
        given:
        changes.load(7L) >> Optional.of(issued(syncedAt: NOW))
        proTech.read(_) >> [CHG0031001: remote]

        when:
        def notes = AuditNotes.listen()
        notes.withCloseable { call(service) }

        then:
        def refused = thrown(RuntimeException)
        refused.message == message
        1 * changes.save({ ProductionChange it -> it.version() == 0L && it.state() == remote.state() }) >>
                { ProductionChange it -> it.toBuilder().version(1L).build() }
        0 * proTech.remind(*_)
        0 * proTech.update(_)
        notes.storedOnTheWay() == [new Stored(CHANGE_SYNCED, new ActionObject(CHANGE, 7L, 'CHG0031001'))]

        where:
        action                          | remote                       | call                                                             || message
        'a refused reminder'            | awaiting()                   | { it.remind(7L, new ReminderCommand(3L, BUSINESS, null)) }       || 'The business approver already approved CHG0031001'
        'a reminder of an unknown task' | awaiting()                   | { it.remind(7L, new ReminderCommand(3L, null, 'CTASK0049999')) } || 'CTASK0049999 is not a change task of CHG0031001'
        'a refused edit'                | issued(state: CLOSED)        | { it.update(7L, edit(shortDescription: 'Renamed')) }             || 'CHG0031001 is closed in ProTech and can no longer be changed'
    }

    def "nothing is noted as synced when #problem"() {
        given:
        changes.load(7L) >>> [Optional.of(issued(syncedAt: NOW)), Optional.of(awaiting().toBuilder().version(1L).build())]
        proTech.read(_) >> [CHG0031001: awaiting()]
        changes.save(_) >> { throw failure }

        when:
        def notes = AuditNotes.listen()
        notes.withCloseable { service.remind(7L, new ReminderCommand(3L, BUSINESS, null)) }

        then:
        def refused = thrown(RuntimeException)
        refused.message == message
        notes.storedOnTheWay() == []

        where:
        problem                                    | failure                                              || message
        'the newer state could not be stored'      | new IllegalArgumentException('The database is full') || 'The database is full'
        'someone else stored the change meanwhile' | new IllegalStateException(STALE_VERSION)             || 'The business approver already approved CHG0031001'
    }

    def "the reminders ProTech sent before it refused one are stored, noted for the audit trail and the refusal is passed on"() {
        given:
        changes.load(7L) >> Optional.of(awaiting())
        proTech.read(_) >> [CHG0031001: awaiting()]

        when:
        def notes = AuditNotes.listen()
        notes.withCloseable { service.remind(7L, new ReminderCommand(3L, null, null)) }

        then:
        1 * proTech.remind('CHG0031001', ApprovalRef.of(SUPPORT)) >> ['Jane Smith']

        then:
        1 * proTech.remind('CHG0031001', ApprovalRef.ofTask('CTASK0041002')) >>
                { throw new IllegalStateException('ProTech has nobody to remind on CHG0031001') }

        then:
        1 * changes.save({ ProductionChange it -> it.approvals()[3].reminder() == new Reminder(NOW, ['Jane Smith'])
                && it.tasks()*.reminder() == [null, null] }) >> { ProductionChange it -> it }
        def refused = thrown(IllegalStateException)
        refused.message == 'ProTech has nobody to remind on CHG0031001'
        notes.results() == [reminded: [[role: SUPPORT, task: null, people: ['Jane Smith']]]]
        notes.created() == []
    }

    def "reminders are stored on the latest version when a sync stored the change in the meantime"() {
        given:
        def latest = awaiting().toBuilder().shortDescription('Renamed meanwhile').version(3L).build()
        changes.load(7L) >>> [Optional.of(awaiting()), Optional.of(latest)]
        proTech.read(_) >> [CHG0031001: awaiting()]
        proTech.remind('CHG0031001', ApprovalRef.of(SUPPORT)) >> ['Jane Smith']

        when:
        def reminded = service.remind(7L, new ReminderCommand(3L, SUPPORT, null))

        then:
        1 * changes.save({ ProductionChange it -> it.version() == 0L }) >> {
            throw new IllegalStateException(STALE_VERSION)
        }
        1 * changes.save({ ProductionChange it -> it.version() == 3L }) >> { ProductionChange it -> it }
        reminded.shortDescription() == 'Renamed meanwhile'
        reminded.approvals()[3].reminder() == new Reminder(NOW, ['Jane Smith'])
    }

    def "the number ProTech gave a created change is noted for the audit trail even when Beadle cannot store it"() {
        given:
        proTech.create(_) >> new CreatedChange('CHG0012345', null)
        changes.save(_) >> { throw new IllegalStateException('The database refused the change') }

        when:
        def notes = AuditNotes.listen()
        notes.withCloseable { service.create(command(storyKeys: [])) }

        then:
        thrown(IllegalStateException)
        notes.created() == ['CHG0012345']
    }

    static ProductionChange awaiting() {
        issued(state: SUPPORT_APPROVAL, workflow: [new WorkflowStep(DRAFT, CREATED),
                                                   new WorkflowStep(SUPPORT_APPROVAL, CREATED.plusSeconds(480))],
                syncedAt: NOW,
                approvals: ChangeApproval.of(template().approvers()).withIndex().collect { approval, index ->
                    new ChangeApproval(approval.role(), approval.approver(), index < 3 ? APPROVED : REQUESTED, null)
                },
                tasks: [task(1).withApproval(APPROVED).withApprovers(['Ann Lee']),
                        task(2).withApproval(NOT_APPROVED).withApprovers(['Ann Lee', 'Grace Turner'])])
    }

    static ChangeTasksCommand created(Map changes = [:]) {
        Map values = [version: 0L, businessServiceId: 3L, tasks: [releaseTask([:], null), ChangeTask.of(details(2))]] +
                changes
        new ChangeTasksCommand(values.version as Long, values.businessServiceId as Long, values.tasks as List)
    }

    static SecureCodingCommand ticket(Map changes = [:]) {
        SecureCoding inputs = secureCoding()
        Map values = [version: 0L, businessServiceId: 3L, implementationDate: '10102026', apoNumber: inputs.apoNumber(),
                      bitbucketUrl: inputs.bitbucketUrl(), artifactLink: inputs.artifactLink(),
                      qcApplicationLink: inputs.qcApplicationLink()] + changes
        new SecureCodingCommand(values.version as Long, values.businessServiceId as Long,
                new SecureCoding(values.apoNumber as String, values.bitbucketUrl as String,
                        values.artifactLink as String, values.qcApplicationLink as String),
                values.implementationDate as String)
    }

    static ChangeEditCommand edit(Map changes = [:]) {
        ProductionChange stored = issued()
        Map values = [version: 0L, businessServiceId: 3L, shortDescription: stored.shortDescription(),
                      description: stored.description(), schedule: stored.schedule(), template: stored.template(),
                      tasks: stored.tasks()] + changes
        new ChangeEditCommand(values.version as Long, values.businessServiceId as Long, values.shortDescription as String,
                values.description as String, values.schedule as ChangeSchedule, values.template as ChangeTemplate,
                values.tasks as List)
    }
}
