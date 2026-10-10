package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductView
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase
import com.bbh.itss.dso.portal.application.change.port.in.LookupsUseCase
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.Lookup
import com.bbh.itss.dso.portal.domain.change.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.TaskDetails
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.PRIVILEGED_PRODUCT
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.PRIVILEGED_USERS
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.tasksFor
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.suggestedTasks
import static com.bbh.itss.dso.portal.domain.change.TaskDetails.validateTasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.risk
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static java.time.Instant.EPOCH

class DemoChangeProfilesSpec extends Specification {

    static final List<String> DEMO_CODES = ['CERTSCANNER', 'DOCSENSE', 'ADVISORAI', 'DEALFLOW', 'LPPORTAL',
                                            'ACCESSHUB', 'SAFEKEEP', 'CORPACT', 'PAYHUB', 'NAVCALC']

    static final List<String> CLIENTS = (1..8).collect { "Client $it fund".toString() }

    ProductsUseCase products = Stub()
    ChangeProfilesUseCase profiles = Mock()
    LookupsUseCase lookups = Stub() {
        find('clients', null) >> CLIENTS.collect { new Lookup(it, 'Custody') }
        find('configuration-items', _) >> { String kind, String name -> [new Lookup(name + ' Two', 'Other'),
                                                                         new Lookup(name, 'Fund Accounting')] }
    }
    def seeder = new DemoChangeProfiles(products, profiles, lookups)

    def "every product without a template gets its ProTech defaults and tasks, the others are left alone"() {
        given:
        def suggested = suggestedFor('PAYHUB', 'PayHub', 'Payments Engineering')
        products.list(null) >> [summary(1, 'CERT'), summary(2, 'PAYHUB'), summary(3, 'FXR')]
        Map<Long, ChangeTemplate> saved = [:]
        Map<Long, List<TaskDetails>> savedTasks = [:]

        when:
        seeder.fillIn()

        then:
        1 * profiles.get(1L) >> new ChangeProfileView(1L, 'CertScanner', 4L, EPOCH, template(), tasks())
        1 * profiles.get(2L) >> ChangeProfileView.builder().productId(2L).productName('PayHub').template(suggested)
                .tasks(suggestedTasks('PayHub', suggested.assignmentGroup())).build()
        1 * profiles.get(3L) >> ChangeProfileView.builder().productId(3L).productName('FX Rates').template(suggested)
                .tasks(suggestedTasks('FX Rates', suggested.assignmentGroup())).build()
        2 * profiles.save({ it in [2L, 3L] }, null, _, _) >> {
            long id, Long version, ChangeTemplate template, List<TaskDetails> chosen ->
            saved[id] = template
            savedTasks[id] = chosen
            null
        }
        0 * profiles.save(1L, *_)
        saved.values().every {
            it.jiraProjectKey() == 'PAYHUB' && it.planning() == suggested.planning() &&
                    it.release() == null &&
                    it.directBusinessService() == 'Fund Accounting' &&
                    [it.requestedFor(), it.requestedBy(), it.department(), it.assignedTo()] == [null] * 4
        }
        saved[2L].privilegedAccess() == new PrivilegedAccess(true, PRIVILEGED_USERS)
        saved[3L].privilegedAccess().users() == []
        saved[2L].assignmentGroup() == 'Team of PAYHUB Application Support'
        savedTasks[2L].first() == suggestedTasks('PAYHUB', saved[2L].assignmentGroup()).first()
        savedTasks[2L].last() == suggestedTasks('PAYHUB', saved[2L].assignmentGroup()).last()
        savedTasks[2L].last().assignmentGroup() == 'Team of PAYHUB Application Support'
        savedTasks[3L].first() == suggestedTasks('FXR', saved[3L].assignmentGroup()).first()
        savedTasks.values().every { it.first().releaseManagement() }
        savedTasks.every { id, chosen -> chosen.size() == (saved[id].risk() == 'Low' ? 2 : 3) }
    }

    def "a product whose template is assessed #risk gets the demo tasks #expected"() {
        given:
        def problems = new ValidationProblems()

        when:
        def chosen = tasksFor(summary(2, 'PayHub'), template(riskAssessment: assessment))
        validateTasks(chosen, problems)

        then:
        chosen*.shortDescription() == expected
        chosen*.assignmentGroup() == groups
        problems.list() == []

        where:
        assessment                                                      || risk       | expected                                                                                                      | groups
        RiskAssessment.builder().bbhUsers('Less than 5').build()        || 'Low'      | ['Deploy PayHub to production', 'Validate PayHub in production']                                                | ['Release Management', 'Technology Architecture']
        risk(businessImpact: 'Medium')                                  || 'Moderate' | ['Deploy PayHub to production', 'Run the database scripts of PayHub', 'Validate PayHub in production']          | ['Release Management', 'Database Administration', 'Technology Architecture']
        risk(businessImpact: 'High')                                    || 'High'     | ['Deploy PayHub to production', 'Run the database scripts of PayHub', 'Validate PayHub in production']          | ['Release Management', 'Database Administration', 'Technology Architecture']
    }

    def "a product whose name fills its column in characters still gets demo tasks and a configuration item ProTech takes"() {
        given:
        def name = 'Zarządzanie płatnościami ' * 8
        def problems = new ValidationProblems()

        when:
        def chosen = tasksFor(new ProductView(2, 'PAYHUB', name, null, null, 3L, 'Custody', 0, null),
                template(riskAssessment: risk(businessImpact: 'High')))
        validateTasks(chosen, problems)
        suggestedFor('PAYHUB', name, null).validate(problems.at('template'))

        then:
        problems.list() == []
        chosen[1].shortDescription().startsWith('Run the database scripts of Zarządzanie płatnościami')
    }

    def "a product whose template ProTech refuses is skipped and the others are still filled in"() {
        given:
        def suggested = suggestedFor('PAYHUB', 'PayHub', null)
        products.list(null) >> [summary(1, 'CERT'), summary(2, 'PAYHUB')]
        profiles.get(_) >> ChangeProfileView.builder().template(suggested).tasks([]).build()

        when:
        seeder.fillIn()

        then:
        1 * profiles.save(1L, *_) >> { throw InvalidRequestException.of('template.department', 'is too long') }
        1 * profiles.save(2L, *_)
        noExceptionThrown()
    }

    def "the demo defaults of every demo product are complete, realistic and valid"() {
        when:
        def defaults = DEMO_CODES.collectEntries {
            [it, seeder.defaultsFor(summary(1, it), suggestedFor(it, it, null))]
        }

        then:
        defaults.every { code, ChangeTemplate template ->
            def problems = new ValidationProblems()
            template.validate(problems)
            def approvers = template.approvers()
            def risk = template.riskAssessment()
            def named = template.affectedClients()?.split(', ')?.toList() ?: []
            assert problems.list() == []
            assert [approvers.l1Manager(), approvers.l2Manager(), approvers.businessApprover()].every { it }
            assert approvers.l1Manager() != approvers.l2Manager()
            assert template.timing().installationStart() in ['18:00', '19:00', '20:00']
            assert RiskAssessment.Question.values().every { it.answerIn(risk) != null }
            assert template.risk() in ['Low', 'Moderate', 'High']
            assert template.downtime() == (template.risk() == 'High')
            assert named.size() == [0, 1, 3, 5][RiskAssessment.Question.CLIENTS_OUTSIDE_BBH.options()
                    .indexOf(risk.clientsOutsideBbh())]
            assert CLIENTS.containsAll(named) && named.toSet().size() == named.size()
            assert template.directBusinessService() == 'Fund Accounting'
            assert template.usersAffected().contains(code)
            assert template.secureCodingTicket() ==~ /APPSEC-\d{4}/
            assert template.privilegedAccess().required() == (code == PRIVILEGED_PRODUCT)
            true
        }
        defaults.values()*.risk().toSet() == ['Low', 'Moderate', 'High'] as Set
        PRIVILEGED_USERS.size() == 2
    }

    static ProductView summary(long id, String code) {
        new ProductView(id, code, code, "Team of $code", null, 3L, 'Corporate Technology', 0, null)
    }
}
