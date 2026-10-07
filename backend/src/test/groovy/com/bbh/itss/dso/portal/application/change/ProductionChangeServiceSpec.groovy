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
import com.bbh.itss.dso.portal.domain.change.ChangeWindow
import com.bbh.itss.dso.portal.domain.change.DateRange
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.shared.ConflictException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException.FieldProblem
import com.bbh.itss.dso.portal.domain.shared.NotFoundException
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.product

class ProductionChangeServiceSpec extends Specification {

    static final Instant NOW = Instant.parse('2026-10-07T10:00:00Z')
    static final Instant START = Instant.parse('2026-10-10T06:00:00Z')
    static final Instant END = Instant.parse('2026-10-10T10:00:00Z')
    static final DateRange RANGE = new DateRange(LocalDate.parse('2026-07-01'), LocalDate.parse('2026-10-07'))
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
        products.get(2L) >> product(id: 2L, code: 'PAYHUB', name: 'PayHub')
        departments.list() >> [new DepartmentView(3, 'Corporate Technology', 0, 1, 3, 3, 3)]
        profiles.find(1L) >> Optional.of(ChangeProfile.create(1L, template()))
        profiles.find(2L) >> Optional.empty()
        jira.issues('CERT', _) >> { project, Collection keys -> ISSUES.findAll { it.key() in keys } }
    }

    def "the preview drafts the change of the chosen services in product order with the chosen Jira issues"() {
        when:
        def draft = service.preview(command(serviceIds: [12L, 10L, 12L]))

        then:
        draft == ProductionChange.draft(certScanner, 'Corporate Technology',
                [certScanner.services()[0], certScanner.services()[2]], template(), new ChangeWindow(START, END),
                ISSUES.take(2), ISSUES.drop(2), null, null)
        draft.tasks()*.serviceName() == ['gui', 'batch']
        0 * changes._
        0 * serviceNow._
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
        refusal                    | edits                                              || problems
        'a change without services' | [serviceIds: []]                                  || [serviceIds: 'choose at least one service']
        'a service of another product' | [serviceIds: [10L, 99L]]                       || [serviceIds: 'service 99 is not a service of CertScanner']
        'a change without epics'    | [epicKeys: [], storyKeys: []]                     || [epicKeys: 'choose at least one epic']
        'an epic missing in Jira'   | [epicKeys: ['CERT-5', 'CERT-404'], storyKeys: []] || [epicKeys: 'CERT-404 is not in Jira project CERT']
        'a story chosen as an epic' | [epicKeys: ['CERT-1', 'CERT-2'], storyKeys: []]   || [epicKeys: 'CERT-2 is a story, not an epic']
        'a story of another epic'   | [epicKeys: ['CERT-1']]                            || [storyKeys: 'CERT-6 is not a story of the chosen epics']
        'a story missing in Jira'   | [storyKeys: ['CERT-2', 'CERT-77']]                || [storyKeys: 'CERT-77 is not in Jira project CERT']
        'a window in the past'      | [start: NOW.minusSeconds(60)]                     || [start: 'must be in the future']
        'a window ending first'     | [end: START]                                      || [end: 'must be after the start']
        'a window over seven days'  | [end: START.plusSeconds(8 * 86400)]               || [end: 'a change window may last at most 7 days']
        'a change without a window' | [start: null, end: null]                          || [start: 'choose when the change starts', end: 'choose when the change ends']
    }

    def "a product without a ServiceNow change template cannot raise changes or read Jira"() {
        when:
        action(service)

        then:
        def refused = thrown(ConflictException)
        refused.message == ('PayHub has no ServiceNow change template yet. Fill it in under DevSecOps Product'
                + ' Management first.')
        0 * jira._

        where:
        action << [{ ProductionChangeService it -> it.preview(command(productId: 2L)) },
                   { ProductionChangeService it -> it.epics(2L, RANGE) },
                   { ProductionChangeService it -> it.stories(2L, ['PAY-1'], RANGE) }]
    }

    def "epics and stories are read from the Jira project of the product"() {
        when:
        def epics = service.epics(1L, RANGE)
        def stories = service.stories(1L, ['CERT-1'], RANGE)
        def none = service.stories(1L, [], RANGE)

        then:
        1 * jira.epics('CERT', RANGE) >> ISSUES.take(2)
        1 * jira.stories('CERT', ['CERT-1'], RANGE) >> ISSUES.drop(2).take(1)
        epics*.key() == ['CERT-1', 'CERT-5']
        stories*.key() == ['CERT-2']
        none == []
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
        Map values = [productId: 1L, serviceIds: [10L], epicKeys: ['CERT-1', 'CERT-5'],
                      storyKeys: ['CERT-2', 'CERT-6'], start: START, end: END] + changes
        new ChangeCommand(values.productId as long, values.serviceIds as List, values.epicKeys as List,
                values.storyKeys as List, values.start as Instant, values.end as Instant,
                values.shortDescription as String, values.description as String)
    }
}
