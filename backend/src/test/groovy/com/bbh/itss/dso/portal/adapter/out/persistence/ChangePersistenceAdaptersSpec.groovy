package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeWindow
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Risk.HIGH
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.story
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static com.bbh.itss.dso.portal.support.Fixtures.account
import static com.bbh.itss.dso.portal.support.Fixtures.details
import static com.bbh.itss.dso.portal.support.Fixtures.settings
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:change-adapters;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter, ProductPersistenceAdapter])
class ChangePersistenceAdaptersSpec extends Specification {

    static final ChangeWindow WINDOW = new ChangeWindow(Instant.parse('2026-10-10T06:00:00Z'),
            Instant.parse('2026-10-10T10:00:00Z'))

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    @Autowired
    ProductPersistenceAdapter products

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    Product product

    def setup() {
        product = products.save(Product.create(details(code: 'CERT', name: 'CertScanner'), account(),
                [new ServiceDraft(null, 'gui', 'Angular', settings()), new ServiceDraft(null, 'api', null, settings())],
                products))
        entities.clear()
    }

    def "a product template is created, changed to a new version and refused when stale"() {
        expect:
        profiles.find(product.id()) == Optional.empty()

        when:
        def created = profiles.save(ChangeProfile.create(product.id(), template()))
        entities.clear()
        def changed = profiles.save(profiles.find(product.id()).get().change(0L,
                template(risk: HIGH, approvers: ['Emma Brooks'])))
        entities.clear()

        then:
        created.version() == 0
        created.template() == template()
        created.updatedAt() != null
        changed.version() == 1
        profiles.find(product.id()).get() == changed
        changed.template().approvers() == ['Emma Brooks']
        jdbc.queryForMap('SELECT RISK, APPROVERS FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?', product.id()) ==
                [RISK: 'HIGH', APPROVERS: 'Emma Brooks']

        when:
        profiles.save(created)

        then:
        thrown(IllegalStateException)
    }

    def "a raised change is stored with its template, its Jira keys and its tasks in order"() {
        given:
        def raised = ProductionChange.draft(product, 'Corporate Technology', product.services(), template(), WINDOW,
                [epic('CERT-1', 'Expiry alerts')], [story('CERT-2', 'E-mail', 'CERT-1')], null, null)
                .numbered('CHG0001001', ['CTASK0002001', 'CTASK0002002'], 'https://snow/CHG0001001')

        when:
        def saved = changes.save(raised)
        def later = changes.save(raised.numbered('CHG0001002', ['CTASK0002003', 'CTASK0002004'], null))
        entities.clear()
        def loaded = changes.load(saved.id()).get()

        then:
        saved.id() != null
        saved.createdAt() != null
        loaded.number() == 'CHG0001001'
        [loaded.productId(), loaded.productCode(), loaded.productName(), loaded.departmentName()] ==
                [product.id(), 'CERT', 'CertScanner', 'Corporate Technology']
        loaded.window() == WINDOW
        [loaded.shortDescription(), loaded.description()] == [raised.shortDescription(), raised.description()]
        loaded.template() == template()
        [loaded.epicKeys(), loaded.storyKeys()] == [['CERT-1'], ['CERT-2']]
        loaded.tasks() == raised.tasks()
        loaded.url() == 'https://snow/CHG0001001'
        changes.findAll()*.number() == ['CHG0001002', 'CHG0001001']
        changes.load(later.id()).get().tasks()*.number() == ['CTASK0002003', 'CTASK0002004']
        changes.load(9999L) == Optional.empty()
    }

    def "deleting a product deletes its template and keeps its raised changes"() {
        given:
        profiles.save(ChangeProfile.create(product.id(), template()))
        def saved = changes.save(ProductionChange.draft(product, null, product.services().take(1), template(),
                WINDOW, [epic('CERT-1', 'Expiry alerts')], [], null, null)
                .numbered('CHG0001003', ['CTASK0002005'], null))
        entities.clear()

        when:
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product.id())

        then:
        profiles.find(product.id()) == Optional.empty()
        with(changes.load(saved.id()).get()) {
            productId() == null
            productName() == 'CertScanner'
            tasks()*.serviceName() == ['gui']
        }
    }
}
