package com.bbh.itss.dso.portal.adapter.in.startup

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductSummaryView
import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfilesUseCase
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ChangeFixtures.template

class DemoChangeProfilesSpec extends Specification {

    ProductsUseCase products = Stub()
    ChangeProfilesUseCase profiles = Mock()
    def seeder = new DemoChangeProfiles(products, profiles)

    def "every product without a template gets a risk assessment and its approvers, the others are left alone"() {
        given:
        def suggested = ChangeTemplate.suggestedFor('PAYHUB', 'PayHub', null, null)
        products.list(null) >> [summary(1, 'CERT'), summary(2, 'PAYHUB'), summary(3, 'FXR')]
        List<ChangeTemplate> saved = []

        when:
        seeder.fillIn()

        then:
        1 * profiles.get(1L) >> new ChangeProfileView(1L, 'CertScanner', 4L, Instant.EPOCH, template())
        1 * profiles.get(2L) >> new ChangeProfileView(2L, 'PayHub', null, null, suggested)
        1 * profiles.get(3L) >> new ChangeProfileView(3L, 'FX Rates', null, null, suggested)
        2 * profiles.save({ it in [2L, 3L] }, null, _) >> { long id, Long version, ChangeTemplate template ->
            saved << template
            null
        }
        0 * profiles.save(1L, *_)
        saved.every {
            it.riskAssessment() != null && it.approvers().size() in 2..4 && it.approvers().toSet().size() ==
                    it.approvers().size() && it.impact().ordinal() <= it.risk().ordinal()
        }
        saved.every { it.jiraProjectKey() == 'PAYHUB' && it.implementationPlan() == suggested.implementationPlan() }
    }

    static ProductSummaryView summary(long id, String code) {
        new ProductSummaryView(id, code, code, null, null, 3L, 'Corporate Technology', 1, 1, 1, null)
    }
}
