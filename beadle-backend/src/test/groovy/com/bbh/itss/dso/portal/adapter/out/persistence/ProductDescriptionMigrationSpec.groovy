package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:product-description;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter])
class ProductDescriptionMigrationSpec extends BeadleMigrationSpecification {

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    def "021 drops the product description of templates and changes, gets it back on rollback and drops it again"() {
        given:
        liquibase.update('')
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES ('CERTSCANNER', 'CertScanner', 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'CERTSCANNER'", Long)
        profiles.save(ChangeProfile.create(productId, template(), tasks(1)))
        ProductionChange change = inTransaction {
            changes.save(raised(id: null, version: null, productId: productId,
                    template: template().releasedAs(FIX_VERSION)))
        }

        expect:
        described() == [false, false]

        when:
        rollBackSince('021-')

        then:
        described() == [true, true]

        when:
        liquibase.update('')
        jdbc.update('UPDATE DSO_CHANGE_PROFILE SET RISK_BBH_USERS = NULL, RISK_BUSINESS_IMPACT = NULL')
        jdbc.update('UPDATE DSO_PRODUCTION_CHANGE SET RISK_PLATFORM_STATUS = NULL')
        def answers = profiles.find(productId).get().template().riskAssessment()

        then:
        described() == [false, false]
        [answers.bbhUsers(), answers.businessImpact()] == ['Less than 5', 'None']
        inTransaction { changes.load(change.id()).get() }.template().riskAssessment().platformStatus() == 'Existing'
    }

    private List<Boolean> described() {
        [columns('DSO_CHANGE_PROFILE').contains('DESCRIPTION'),
         columns('DSO_PRODUCTION_CHANGE').contains('PRODUCT_DESCRIPTION')]
    }
}
