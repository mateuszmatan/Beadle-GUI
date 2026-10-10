package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import com.bbh.itss.dso.portal.domain.change.SecureCoding
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.secureCoding
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:secure-coding;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter])
class SecureCodingMigrationSpec extends MigrationSpecification {

    static final List<String> INPUTS = ['APO_NUMBER', 'BITBUCKET_URL', 'ARTIFACT_LINK', 'QC_APPLICATION_LINK']

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    def "025 adds the secure coding inputs, drops the ticket of every template, keeps the tickets of changes and rolls back"() {
        given:
        liquibase.update('')
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES ('PAYHUB', 'PayHub', 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'PAYHUB'", Long)
        profiles.save(ChangeProfile.create(productId, template(secureCodingTicket: 'APPSEC-1234',
                secureCoding: secureCoding()), tasks()))
        ProductionChange change = inTransaction {
            changes.save(raised(id: null, version: null, productId: productId, template: template(release: FIX_VERSION))
                    .withSecureCoding(secureCoding(), 'SCP-1001'))
        }

        when:
        rollBackSince('025-')

        then:
        INPUTS.every { !(it in columns('DSO_CHANGE_PROFILE')) && !(it in columns('DSO_PRODUCTION_CHANGE')) }
        jdbc.queryForObject('SELECT SECURE_CODING_TICKET FROM DSO_CHANGE_PROFILE WHERE PRODUCT_ID = ?', String,
                productId) == 'APPSEC-1234'

        when:
        liquibase.update('')
        def profile = profiles.find(productId).get()
        def migrated = inTransaction { changes.load(change.id()).get() }

        then:
        INPUTS.every { nullable('DSO_CHANGE_PROFILE', it) && nullable('DSO_PRODUCTION_CHANGE', it) }
        profile.template().secureCodingTicket() == null
        profile.template().secureCoding() == SecureCoding.NONE
        migrated.template().secureCodingTicket() == 'SCP-1001'

        when:
        profiles.save(profile.change(profile.version(), template(secureCoding: secureCoding()), tasks()))
        def ticketed = inTransaction { changes.save(migrated.withSecureCoding(secureCoding(), 'SCP-1001')) }

        then:
        profiles.find(productId).get().template().secureCoding() == secureCoding()
        inTransaction { changes.load(change.id()).get() }.template() == ticketed.template()

        when:
        jdbc.update('UPDATE DSO_PRODUCTION_CHANGE SET APO_NUMBER = ? WHERE ID = ?', 'A' * 41, change.id())

        then:
        thrown(DataIntegrityViolationException)
    }
}
