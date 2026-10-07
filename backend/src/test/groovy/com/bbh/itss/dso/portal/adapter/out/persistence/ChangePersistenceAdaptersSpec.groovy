package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.application.change.port.in.ChangeProfileSummary
import com.bbh.itss.dso.portal.domain.catalog.Product
import com.bbh.itss.dso.portal.domain.catalog.ServiceDraft
import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Approvers
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedAccess
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.PrivilegedUser
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.RiskAssessment
import com.bbh.itss.dso.portal.domain.change.ChangeTemplate.Timing
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import spock.lang.Specification

import static com.bbh.itss.dso.portal.domain.change.ChangeTemplate.TEST_SUMMARY
import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.epic
import static com.bbh.itss.dso.portal.support.ChangeFixtures.privileged
import static com.bbh.itss.dso.portal.support.ChangeFixtures.schedule
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

    static final ChangeTemplate FULL = template(release: 'R 4.2', incident: 'INC0012345', problem: 'PRB0001234',
            affectedClients: 'Fund administration clients', downtime: true, timing: new Timing('20:30', 3, 2),
            privilegedAccess: new PrivilegedAccess(true, [new PrivilegedUser('Jane Smith', 'adm_jsmith'),
                                                          new PrivilegedUser('Ann Lee', 'adm_alee')]))

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
        product = save('CERT', 'CertScanner')
        entities.clear()
    }

    def "a product template is created, changed to a new version and refused when stale"() {
        expect:
        profiles.find(product.id()) == Optional.empty()

        when:
        def created = profiles.save(ChangeProfile.create(product.id(), FULL))
        entities.clear()
        def changed = profiles.save(profiles.find(product.id()).get().change(0L,
                template(approvers: new Approvers('Emma Brooks', null, null), privilegedAccess: privileged(3))))
        entities.clear()

        then:
        created.version() == 0
        created.template() == FULL
        created.updatedAt() != null
        changed.version() == 1
        profiles.find(product.id()).get() == changed
        changed.template().privilegedAccess() == privileged(3)
        jdbc.queryForMap('''SELECT L1_MANAGER, L2_MANAGER, TIMING_INSTALLATION_START, TEST_SUMMARY, DOWNTIME,
                PRIVILEGED_ACCESS_REQUIRED, RISK_BBH_USERS FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?''', product.id()) ==
                [L1_MANAGER: 'Emma Brooks', L2_MANAGER: null, TIMING_INSTALLATION_START: '18:00',
                 TEST_SUMMARY: TEST_SUMMARY, DOWNTIME: 0, PRIVILEGED_ACCESS_REQUIRED: 1, RISK_BBH_USERS: 10]
        jdbc.queryForList('''SELECT u.POSITION, u.USER_NAME, u.ACCOUNT_NAME FROM DSO_CHANGE_PROFILE_PRIVILEGED_USER u
                JOIN DSO_CHANGE_PROFILE p ON p.ID = u.PROFILE_ID WHERE p.PRODUCT_ID = ? ORDER BY u.POSITION''',
                product.id()) == (1..3).collect { [POSITION: it - 1, USER_NAME: "User $it".toString(),
                                                    ACCOUNT_NAME: "adm_user$it".toString()] }

        when:
        profiles.save(created)

        then:
        thrown(IllegalStateException)
    }

    def "a template without approvers, privileged users or risk assessment reads back as such"() {
        given:
        def bare = template(approvers: Approvers.NONE, riskAssessment: RiskAssessment.NONE)

        when:
        profiles.save(ChangeProfile.create(product.id(), bare))
        entities.clear()

        then:
        profiles.find(product.id()).get().template() == bare
    }

    def "the stored profiles are summarised by product name"() {
        given:
        def access = save('ACCESS', 'Access Hub')
        save('LEDGER', 'Ledger')
        profiles.save(ChangeProfile.create(product.id(), FULL))
        def stored = profiles.save(ChangeProfile.create(access.id(), template()))
        profiles.save(profiles.find(access.id()).get().change(0L, template(category: 'Apps')))
        entities.clear()

        when:
        def summaries = profiles.summaries()

        then:
        summaries*.productName() == ['Access Hub', 'CertScanner']
        summaries[0] == new ChangeProfileSummary(access.id(), 'Access Hub', 1, summaries[0].updatedAt())
        !summaries[0].updatedAt().isBefore(stored.updatedAt())
        summaries[1].version() == 0
    }

    def "a raised change is stored with its schedule, its template, its Jira keys and its tasks in order"() {
        given:
        def raised = ProductionChange.draft(product, 'Corporate Technology', product.services(), FIX_VERSION,
                schedule(), FULL, [epic('CERT-1', 'Expiry alerts')], [story('CERT-2', 'E-mail', 'CERT-1')], null,
                null).numbered('CHG0001001', ['CTASK0002001', 'CTASK0002002'], 'https://snow/CHG0001001')

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
        loaded.fixVersion() == FIX_VERSION
        loaded.schedule() == schedule()
        [loaded.shortDescription(), loaded.description()] == [raised.shortDescription(), raised.description()]
        loaded.template() == FULL
        loaded.template().privilegedAccess().users()*.user() == ['Jane Smith', 'Ann Lee']
        [loaded.epicKeys(), loaded.storyKeys()] == [['CERT-1'], ['CERT-2']]
        loaded.tasks() == raised.tasks()
        loaded.url() == 'https://snow/CHG0001001'
        changes.findAll()*.number() == ['CHG0001002', 'CHG0001001']
        changes.findAll()*.template().every { it == FULL }
        changes.load(later.id()).get().tasks()*.number() == ['CTASK0002003', 'CTASK0002004']
        changes.load(9999L) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_PRODUCTION_CHANGE_PRIVILEGED_USER WHERE CHANGE_ID = ?', Integer,
                saved.id()) == 2
    }

    def "deleting a product deletes its template and keeps its raised changes"() {
        given:
        profiles.save(ChangeProfile.create(product.id(), FULL))
        def saved = changes.save(ProductionChange.draft(product, null, product.services().take(1), FIX_VERSION,
                schedule(), FULL, [epic('CERT-1', 'Expiry alerts')], [], null, null)
                .numbered('CHG0001003', ['CTASK0002005'], null))
        entities.clear()

        when:
        jdbc.update('DELETE FROM DSO_PRODUCT WHERE ID = ?', product.id())

        then:
        profiles.find(product.id()) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM DSO_CHANGE_PROFILE_PRIVILEGED_USER', Integer) == 0
        with(changes.load(saved.id()).get()) {
            productId() == null
            productName() == 'CertScanner'
            tasks()*.serviceName() == ['gui']
            it.template().privilegedAccess().users().size() == 2
        }
    }

    private Product save(String code, String name) {
        products.save(Product.create(details(code: code, name: name), account(),
                [new ServiceDraft(null, 'gui', 'Angular', settings()), new ServiceDraft(null, 'api', null, settings())],
                products))
    }
}
