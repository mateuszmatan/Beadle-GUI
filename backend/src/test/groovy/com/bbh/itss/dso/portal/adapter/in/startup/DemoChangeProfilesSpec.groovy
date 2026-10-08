package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.TaskText
import com.bbh.itss.dso.portal.domain.shared.ValidationProblems
import spock.lang.Specification

import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.LEVELS
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.PRIVILEGED_PRODUCT
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.PRIVILEGED_USERS
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.defaultsFor
import static com.bbh.itss.dso.portal.adapter.in.startup.DemoChangeProfiles.tasksFor
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor
import static com.bbh.itss.dso.portal.domain.change.TaskText.suggestedTasks
import static com.bbh.itss.dso.portal.domain.change.TaskText.validateTasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.risk
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static java.time.Instant.EPOCH

class DemoChangeProfilesSpec extends Specification {

    static final List<String> DEMO_CODES = ['CERTSCANNER', 'DOCSENSE', 'ADVISORAI', 'DEALFLOW', 'LPPORTAL',
                                            'ACCESSHUB', 'SAFEKEEP', 'CORPACT', 'PAYHUB', 'NAVCALC']

    ProductsUseCase products = Stub()
    ChangeProfilesUseCase profiles = Mock()
    def seeder = new DemoChangeProfiles(products, profiles)

    def "every product without a template gets its ProTech defaults and tasks, the others are left alone"() {
        given:
        def suggested = suggestedFor('PAYHUB', 'PayHub', 'Payments Engineering', 'Payments.')
        products.list(null) >> [summary(1, 'CERT'), summary(2, 'PAYHUB'), summary(3, 'FXR')]
        Map<Long, ChangeTemplate> saved = [:]
        Map<Long, List<TaskText>> savedTasks = [:]

        when:
        seeder.fillIn()

        then:
        1 * profiles.get(1L) >> new ChangeProfileView(1L, 'CertScanner', 4L, EPOCH, template(), tasks())
        1 * profiles.get(2L) >> ChangeProfileView.builder().productId(2L).productName('PayHub').template(suggested)
                .tasks(suggestedTasks('PayHub')).build()
        1 * profiles.get(3L) >> ChangeProfileView.builder().productId(3L).productName('FX Rates').template(suggested)
                .tasks(suggestedTasks('FX Rates')).build()
        2 * profiles.save({ it in [2L, 3L] }, null, _, _) >> {
            long id, Long version, ChangeTemplate template, List<TaskText> chosen ->
            saved[id] = template
            savedTasks[id] = chosen
            null
        }
        0 * profiles.save(1L, *_)
        saved.values().every {
            it.jiraProjectKey() == 'PAYHUB' && it.planning() == suggested.planning() &&
                    it.description() == 'Payments.' && it.release() == null
        }
        saved[2L].privilegedAccess() == new PrivilegedAccess(true, PRIVILEGED_USERS)
        saved[3L].privilegedAccess().users() == []
        saved[2L].assignmentGroup() == 'Team of PAYHUB Application Support'
        savedTasks[2L].first() == suggestedTasks('PayHub').first()
        savedTasks[2L].last() == suggestedTasks('PayHub').last()
        savedTasks[3L].first() == suggestedTasks('FX Rates').first()
        savedTasks.values()*.size().every { it in [2, 3] }
    }

    def "a product with a #impact business impact gets the demo tasks #expected"() {
        given:
        def defaults = template(riskAssessment: risk(businessImpact: impact))
        def problems = new ValidationProblems()

        when:
        def chosen = tasksFor(summary(2, 'PayHub'), defaults, suggestedTasks('PayHub'))
        validateTasks(chosen, problems)

        then:
        chosen*.shortDescription() == expected
        problems.list() == []

        where:
        impact   || expected
        'Low'    || ['Deploy PayHub to production', 'Validate PayHub in production']
        'Medium' || ['Deploy PayHub to production', 'Run the database scripts of PayHub', 'Validate PayHub in production']
        'High'   || ['Deploy PayHub to production', 'Run the database scripts of PayHub', 'Validate PayHub in production']
    }

    def "the demo defaults of every demo product are complete, realistic and valid"() {
        expect:
        DEMO_CODES.every { code ->
            def suggested = suggestedFor(code, code, null, null)
            def defaults = defaultsFor(summary(1, code), suggested)
            def problems = new ValidationProblems()
            defaults.validate(problems)
            def approvers = defaults.approvers()
            def risk = defaults.riskAssessment()
            assert problems.list() == []
            assert [approvers.l1Manager(), approvers.l2Manager(), approvers.businessApprover()].every { it }
            assert approvers.l1Manager() != approvers.l2Manager()
            assert defaults.timing().installationStart() in ['18:00', '19:00', '20:00']
            assert defaults.affectedClients() != null
            assert [risk.bbhWorkgroups(), risk.bbhUsers(), risk.bbhApplications()].every { it > 0 }
            assert [risk.clients(), risk.clientsOutsideBbh()].every { it >= 0 }
            assert [risk.businessImpact(), risk.changeComplexity(), risk.validationComplexity()]
                    .every { it in LEVELS }
            assert risk.backoutTesting() && risk.platformStatus() in ['Existing platform', 'Platform upgrade']
            assert defaults.downtime() == (risk.businessImpact() == 'High')
            assert defaults.privilegedAccess().required() == (code == PRIVILEGED_PRODUCT)
            true
        }
        DEMO_CODES.collect { defaultsFor(summary(1, it), suggestedFor(it, it, null, null)).riskAssessment()
                .businessImpact() }.toSet().size() > 1
        PRIVILEGED_USERS.size() == 2
    }

    static ProductSummaryView summary(long id, String code) {
        new ProductSummaryView(id, code, code, null, "Team of $code", 3L, 'Corporate Technology', 1, 1, 1, null)
    }
}
