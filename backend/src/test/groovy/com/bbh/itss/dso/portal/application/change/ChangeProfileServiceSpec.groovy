package com.bbh.itss.dso.portal.application.change

import com.bbh.itss.dso.portal.application.catalog.port.in.ProductsUseCase
import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileView
import com.bbh.itss.dso.portal.application.change.port.out.ChangeProfileRepositoryPort
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeProfileSummary
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.TaskText
import com.bbh.itss.dso.portal.domain.shared.InvalidRequestException
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.suggestedFor
import static com.bbh.itss.dso.portal.domain.change.TaskText.suggestedTasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
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
        profiles.find(1L) >> Optional.of(new ChangeProfile(1L, template(), tasks(), 3, SAVED))

        expect:
        service.get(1L) == new ChangeProfileView(1L, 'CertScanner', 3L, SAVED, template(), tasks())
    }

    def "a product without a template is shown a suggestion that is not saved yet"() {
        given:
        profiles.find(1L) >> Optional.empty()

        expect:
        service.get(1L) == ChangeProfileView.builder().productId(1L).productName('CertScanner')
                .template(suggestedFor('CERTSCANNER', 'CertScanner', 'Technology Architecture',
                        'Watches TLS certificates.'))
                .tasks(suggestedTasks('CertScanner')).build()
    }

    def "the first template of a product is created"() {
        given:
        profiles.find(1L) >> Optional.empty()

        when:
        def saved = service.save(1L, null, template(), tasks(3))

        then:
        1 * profiles.save(ChangeProfile.create(1L, template(), tasks(3))) >>
                new ChangeProfile(1L, template(), tasks(3), 0, SAVED)
        saved == new ChangeProfileView(1L, 'CertScanner', 0L, SAVED, template(), tasks(3))
    }

    def "the stored profiles are listed as the repository sorts them"() {
        given:
        def stored = [new ChangeProfileSummary(2L, 'Access Hub', 0, SAVED),
                      new ChangeProfileSummary(1L, 'CertScanner', 3, SAVED)]
        profiles.summaries() >> stored

        expect:
        service.list() == stored
    }

    def "a stored template is changed when the version matches"() {
        given:
        def changed = template(privilegedAccess: privileged(2))
        profiles.find(1L) >> Optional.of(new ChangeProfile(1L, template(), tasks(), 2, SAVED))

        when:
        def saved = service.save(1L, 2L, changed, tasks(1))

        then:
        1 * profiles.save(new ChangeProfile(1L, changed, tasks(1), 2, SAVED)) >>
                new ChangeProfile(1L, changed, tasks(1), 3, SAVED)
        saved.version() == 3L
        saved.tasks() == tasks(1)
        saved.template().privilegedAccess().users()*.account() == ['adm_user1', 'adm_user2']
    }

    def "a template changed by someone else meanwhile is not saved"() {
        given:
        profiles.find(1L) >> Optional.of(new ChangeProfile(1L, template(), tasks(), 2, SAVED))

        when:
        service.save(1L, version, template(), tasks())

        then:
        thrown(IllegalStateException)
        0 * profiles.save(_)

        where:
        version << [1L, 3L, null]
    }

    def "a template that breaks its rules is refused against its fields before anything is stored"() {
        when:
        service.save(1L, null, template(planning: null, privilegedAccess: new PrivilegedAccess(false,
                privileged(1).users())), listed)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems()*.field() == fields
        0 * profiles._

        where:
        listed                                  || fields
        tasks()                                 || ['template.planning', 'template.privilegedAccess.users']
        []                                      || ['template.planning', 'template.privilegedAccess.users', 'tasks']
        [new TaskText(' ', 'Text.')]            || ['template.planning', 'template.privilegedAccess.users', 'tasks[0].shortDescription']
    }
}
