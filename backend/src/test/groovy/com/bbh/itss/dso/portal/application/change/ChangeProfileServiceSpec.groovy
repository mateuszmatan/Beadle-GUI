package com.bbh.itss.dso.portal.application.change

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Risk.HIGH
import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.product

class ChangeProfileServiceSpec extends Specification {

    static final Instant SAVED = Instant.parse('2026-10-05T12:00:00Z')

    ChangeProfileRepositoryPort profiles = Mock()
    ProductsUseCase products = Stub()
    def service = new ChangeProfileService(profiles, products)

    def setup() {
        products.get(1L) >> product(code: 'CERTSCANNER', ownerTeam: 'Technology Architecture',
                description: 'Watches TLS certificates.')
    }

    def "a stored template is shown with its version"() {
        given:
        profiles.find(1L) >> Optional.of(new ChangeProfile(1L, template(), 3, SAVED))

        expect:
        service.get(1L) == new ChangeProfileView(1L, 'CertScanner', 3L, SAVED, template())
    }

    def "a product without a template is shown a suggestion that is not saved yet"() {
        given:
        profiles.find(1L) >> Optional.empty()

        expect:
        service.get(1L) == ChangeProfileView.builder().productId(1L).productName('CertScanner')
                .template(suggestedFor('CERTSCANNER', 'CertScanner', 'Technology Architecture',
                        'Watches TLS certificates.'))
                .build()
    }

    def "the first template of a product is created"() {
        given:
        profiles.find(1L) >> Optional.empty()

        when:
        def saved = service.save(1L, null, template())

        then:
        1 * profiles.save(ChangeProfile.create(1L, template())) >> new ChangeProfile(1L, template(), 0, SAVED)
        saved == new ChangeProfileView(1L, 'CertScanner', 0L, SAVED, template())
    }

    def "a stored template is changed when the version matches"() {
        given:
        def changed = template(risk: HIGH)
        profiles.find(1L) >> Optional.of(new ChangeProfile(1L, template(), 2, SAVED))

        when:
        def saved = service.save(1L, 2L, changed)

        then:
        1 * profiles.save(new ChangeProfile(1L, changed, 2, SAVED)) >> new ChangeProfile(1L, changed, 3, SAVED)
        saved.version() == 3L
        saved.template().risk() == HIGH
    }

    def "a template changed by someone else meanwhile is not saved"() {
        given:
        profiles.find(1L) >> Optional.of(new ChangeProfile(1L, template(), 2, SAVED))

        when:
        service.save(1L, version, template())

        then:
        thrown(IllegalStateException)
        0 * profiles.save(_)

        where:
        version << [1L, 3L, null]
    }
}
