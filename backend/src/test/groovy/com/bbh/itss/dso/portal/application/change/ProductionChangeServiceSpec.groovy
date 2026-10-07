package com.bbh.itss.dso.portal.application.change

import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentView
import com.bbh.itss.dso.portal.application.catalog.port.in.DepartmentsUseCase
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ChangeCommand
import com.bbh.itss.dso.portal.application.change.port.in.ChangeIntegrations
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.JiraPort
import com.bbh.itss.dso.portal.application.change.port.out.ProductionChangeRepositoryPort
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort
import com.bbh.itss.dso.portal.application.change.port.out.ServiceNowPort.RaisedChange
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeSchedule
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.JiraVersion
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.product

class ProductionChangeServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')
    static final List ISSUES = [epic('CERT-1', 'Expiry alerts'), epic('CERT-5', 'Audit trail'),
                                story('CERT-2', 'E-mail the owner', 'CERT-1'), story('CERT-6', 'Record it', 'CERT-5')]

    ProductsUseCase products = Stub()
    DepartmentsUseCase departments = Stub()
    ChangeProfileRepositoryPort profiles = Stub()
    ProductionChangeRepositoryPort changes = Mock()
    JiraPort jira = Mock()
    ServiceNowPort serviceNow = Mock()
    def service = new ProductionChangeService(products, departments, profiles, changes, jira, serviceNow,
            Clock.fixed(NOW, ZoneOffset.UTC))
    def certScanner = product(code: 'CERTSCANNER', services: [[id: 10, name: 'gui'], [id: 11, name: 'api'],
                                                               [id: 12, name: 'batch']])

    def setup() {
        products.get(1L) >> certScanner
        products.get(2L) >> product(id: 2L, code: 'PAYHUB', name: 'PayHub', services: [[id: 20, name: 'gateway']])
        products.get(3L) >> product(id: 3L, code: 'EMPTY', name: 'Empty', services: [])
        departments.list() >> [new DepartmentView(3, 'Corporate Technology', 0, 1, 3, 3, 3)]
        profiles.find(1L) >> Optional.of(ChangeProfile.create(1L, template(jiraProjectKey: 'CSCAN')))
        profiles.find(2L) >> Optional.empty()
        jira.issues('CERT', _) >> { project, Collection keys -> ISSUES.findAll { it.key() in keys } }
    }

    def "the preview drafts the change of the chosen services in product order with the chosen Jira issues"() {
        when:
        def draft = service.preview(command(serviceIds: [12L, 10L, 12L]))

        then:
        draft == ProductionChange.draft(certScanner, 'Corporate Technology',
                [certScanner.services()[0], certScanner.services()[2]], FIX_VERSION, schedule(), template(),
                ISSUES.take(2), ISSUES.drop(2), null, null)
        draft.tasks()*.serviceName() == ['gui', 'batch']
        draft.template().release() == FIX_VERSION
        0 * changes._
        0 * serviceNow._
    }

    def "a change without chosen services deploys every service of the product"() {
        expect:
        service.preview(command(serviceIds: [])).tasks()*.serviceName() == ['gui', 'api', 'batch']
    }

    def "a product without stored ServiceNow defaults raises a change from the template of the request"() {
        given:
        def payHub = template(jiraProjectKey: 'PAY', configurationItem: 'PayHub')
        jira.issues('PAY', ['PAY-1']) >> [epic('PAY-1', 'Instant payments')]

        when:
        def preview = service.preview(command(productId: 2L, serviceIds: [], template: payHub, epicKeys: ['PAY-1'],
                storyKeys: [], fixVersion: ' PAY 1.0 '))

        then:
        preview.productCode() == 'PAYHUB'
        preview.fixVersion() == 'PAY 1.0'
        preview.template() == payHub.releasedAs('PAY 1.0')
        preview.shortDescription() == 'PayHub PAY 1.0: Instant payments'
        preview.tasks()*.serviceName() == ['gateway']
    }

    def "a raised change is filed in ServiceNow and stored with its numbers"() {
        when:
        def raised = service.raise(command(serviceIds: [11L], storyKeys: [], shortDescription: 'Mine'))

        then:
        1 * serviceNow.raise({ it.number() == null && it.shortDescription() == 'Mine' }) >>
                new RaisedChange('CHG0012345', ['CTASK0020001'], 'https://bbh.service-now.com/CHG0012345')
        1 * changes.save({ it.number() == 'CHG0012345' && it.tasks()*.number() == ['CTASK0020001'] }) >>
                { ProductionChange change -> change }
        raised.number() == 'CHG0012345'
        raised.tasks()*.serviceName() == ['api']
        raised.url() == 'https://bbh.service-now.com/CHG0012345'
        raised.fixVersion() == FIX_VERSION
        raised.schedule() == schedule()
    }

    def "#refusal is refused with every problem before anything is raised"() {
        when:
        service.raise(command(edits))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems == problems.collect { new FieldProblem(it.key, it.value) }
        0 * serviceNow._
        0 * changes._

        where:
        refusal                         | edits                                              || problems
        'a service of another product'  | [serviceIds: [10L, 99L]]                           || [serviceIds: 'service 99 is not a service of CertScanner']
        'a product without services'    | [productId: 3L, serviceIds: []]                    || [serviceIds: 'Empty has no services to deploy']
        'a change without a FixVersion' | [fixVersion: ' ']                                  || [fixVersion: 'choose the FixVersion of the release']
        'a FixVersion over 100 bytes'   | [fixVersion: 'é' * 51]                             || [fixVersion: 'is too long: it may take at most 100 bytes']
        'a change without epics'        | [epicKeys: [], storyKeys: []]                      || [epicKeys: 'choose at least one epic']
        'an epic missing in Jira'       | [epicKeys: ['CERT-5', 'CERT-404'], storyKeys: []]  || [epicKeys: 'CERT-404 is not in Jira project CERT']
        'a story chosen as an epic'     | [epicKeys: ['CERT-1', 'CERT-2'], storyKeys: []]    || [epicKeys: 'CERT-2 is a story, not an epic']
        'a story of another epic'       | [epicKeys: ['CERT-1']]                             || [storyKeys: 'CERT-6 is not a story of the chosen epics']
        'a story missing in Jira'       | [storyKeys: ['CERT-2', 'CERT-77']]                 || [storyKeys: 'CERT-77 is not in Jira project CERT']
        'an installation in the past'   | [schedule: schedule(installationStart: '2026-10-07T09:59:00Z')] || ['schedule.installationStart': 'must be in the future']
        'an installation starting now'  | [schedule: schedule(installationStart: '2026-10-07T10:00:00Z')] || ['schedule.installationStart': 'must be in the future']
        'an installation ending first'  | [schedule: schedule(installationEnd: '2026-10-10T06:00:00Z', validationStart: '2026-10-10T06:00:00Z')] || ['schedule.installationEnd': 'must be after the installation start']
        'a first usage before the end'  | [schedule: schedule(firstUsage: '2026-10-10T10:30:00Z')] || ['schedule.firstUsage': 'must not be before the validation end']
        'a change without a schedule'   | [schedule: null]                                   || [schedule: 'choose when the change is installed, validated and first used']
        'a change without a template'   | [template: null]                                   || [template: 'fill in the ServiceNow fields of the change']
        'a broken template'             | [template: template(timing: new Timing('18:00', 0, 1), privilegedAccess: new PrivilegedAccess(true, []))] || ['template.timing.installationHours': 'must be between 1 and 72 hours', 'template.privilegedAccess.users': 'add the users who need privileged access']
        'a short description over 160 bytes' | [shortDescription: 'é' * 81]                  || [shortDescription: 'is too long: it may take at most 160 bytes']
        'a description over 4000 bytes' | [description: 'ł' * 2001]                         || [description: 'is too long: it may take at most 4000 bytes']
    }

    def "a template without a valid Jira key is refused without asking Jira"() {
        when:
        service.preview(command(template: template(jiraProjectKey: key)))

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems*.field == ['template.jiraProjectKey']
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
        refused.problems == [new FieldProblem('schedule.installationStart', 'must be in the future')]
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
        refused.problems == [new FieldProblem(field, message)]
        0 * jira._

        where:
        method    | problem              | action                                                  || field        | message
        'epics'   | 'a blank FixVersion' | { ProductionChangeService it -> it.epics(1L, ' ', null) } || 'fixVersion' | 'choose a FixVersion'
        'stories' | 'no FixVersion'      | { ProductionChangeService it -> it.stories(1L, null, ['CERT-1'], null) } || 'fixVersion' | 'choose a FixVersion'
        'versions'| 'a broken project'   | { ProductionChangeService it -> it.versions(1L, 'ce-rt') } || 'project' | ChangeTemplate.JIRA_KEY_MESSAGE
    }

    def "raised changes are listed and read back, and the integrations say they are demo ones"() {
        given:
        def stored = service.preview(command())

        when:
        def listed = service.list()
        def loaded = service.get(7L)
        def integrations = service.integrations()

        then:
        1 * changes.findAll() >> [stored]
        1 * changes.load(7L) >> Optional.of(stored)
        1 * jira.connected() >> false
        1 * serviceNow.connected() >> true
        listed == [stored]
        loaded == stored
        integrations == new ChangeIntegrations(false, true)
    }

    def "an unknown change is not found"() {
        given:
        changes.load(8L) >> Optional.empty()

        when:
        service.get(8L)

        then:
        thrown(NotFoundException)
    }

    static ChangeCommand command(Map changes = [:]) {
        Map values = [productId: 1L, serviceIds: [10L], fixVersion: FIX_VERSION, epicKeys: ['CERT-1', 'CERT-5'],
                      storyKeys: ['CERT-2', 'CERT-6'], schedule: schedule(), template: template()] + changes
        new ChangeCommand(values.productId as long, values.serviceIds as List, values.fixVersion as String,
                values.epicKeys as List, values.storyKeys as List, values.schedule as ChangeSchedule,
                values.template as ChangeTemplate, values.shortDescription as String, values.description as String)
    }
}
