package com.bbh.itss.dso.portal.adapter.out.persistence

import com.bbh.itss.dso.portal.domain.change.ChangeProfile
import com.bbh.itss.dso.portal.domain.change.ProductionChange
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional

import java.time.Instant

import static com.bbh.itss.dso.portal.support.ChangeFixtures.FIX_VERSION
import static com.bbh.itss.dso.portal.support.ChangeFixtures.raised
import static com.bbh.itss.dso.portal.support.ChangeFixtures.tasks
import static com.bbh.itss.dso.portal.support.ChangeFixtures.template
import static java.time.Instant.parse
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE
import static org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED

@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:file:./build/change-flags-migration/db;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH',
        'spring.datasource.username=sa',
        'spring.liquibase.enabled=false'])
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = NOT_SUPPORTED)
@Import([ChangeProfilePersistenceAdapter, ProductionChangePersistenceAdapter])
class ChangeFlagsMigrationSpec extends MigrationSpecification {

    static final Instant SYNCED = parse('2026-10-09T07:00:00Z')
    static final List<String> FLAGS = ['CK_DSO_CHANGE_PROFILE_FLAGS', 'CK_DSO_PRODUCTION_CHANGE_FLAGS']

    @Autowired
    ChangeProfilePersistenceAdapter profiles

    @Autowired
    ProductionChangePersistenceAdapter changes

    def "a database from before the ProTech sync, reopened and upgraded, syncs and edits its changes in the same run"() {
        given:
        jdbc.execute('DROP ALL OBJECTS')
        liquibase.update('')
        def (long productId, long changeId) = stored()
        rollBackSince('016-')
        reopen()

        when:
        liquibase.update('')
        changes.synced([changeId], SYNCED)
        def change = inTransaction { changes.load(changeId).get() }
        def edited = inTransaction { changes.save(change.toBuilder().shortDescription('Synced from ProTech').build()) }
        def profile = profiles.find(productId).get()
        def saved = profiles.save(profile.change(profile.version(), profile.template().toBuilder()
                .usersAffected('Fund accountants').build(), profile.tasks()))

        then:
        jdbc.queryForList('SELECT ID FROM DATABASECHANGELOG WHERE ID LIKE ?', String, '%-change-flags-h2').sort() ==
                ['020-change-flags-h2', '021-change-flags-h2']
        change.syncedAt() == SYNCED
        change.template().downtime()
        edited.shortDescription() == 'Synced from ProTech'
        saved.template().usersAffected() == 'Fund accountants'
        jdbc.queryForList('SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_NAME IN (?, ?)',
                String, *FLAGS).sort() == FLAGS

        when:
        jdbc.update('UPDATE DSO_PRODUCTION_CHANGE SET DOWNTIME = 2 WHERE ID = ?', changeId)

        then:
        thrown(DataIntegrityViolationException)
    }

    private List<Long> stored() {
        jdbc.update('''INSERT INTO DSO_PRODUCT (CODE, NAME, ASOC_KEY_ID, CREATED_AT, UPDATED_AT)
                VALUES ('CERTSCANNER', 'Certscanner', 'bbh_key', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)''')
        long productId = jdbc.queryForObject("SELECT ID FROM DSO_PRODUCT WHERE CODE = 'CERTSCANNER'", Long)
        profiles.save(ChangeProfile.create(productId, template(downtime: true), tasks(1)))
        ProductionChange change = inTransaction {
            changes.save(raised(id: null, version: null, productId: productId, openedBy: 'Mateusz Matan',
                    template: template(downtime: true).releasedAs(FIX_VERSION)))
        }
        [productId, change.id()]
    }
}
