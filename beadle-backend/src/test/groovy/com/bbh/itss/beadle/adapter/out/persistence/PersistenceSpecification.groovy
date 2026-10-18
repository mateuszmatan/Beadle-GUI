package com.bbh.itss.beadle.adapter.out.persistence

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import spock.lang.Specification

import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE

/**
 * The JPA slice of every persistence spec: H2 in Oracle mode after the Liquibase changelog, with every persistence
 * adapter, so that Spring starts it once for all of them. Each feature runs in a transaction that is rolled back.
 */
@DataJpaTest(properties = [
        'spring.datasource.url=jdbc:h2:mem:persistence;MODE=Oracle;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1',
        'spring.datasource.username=sa'])
@AutoConfigureTestDatabase(replace = NONE)
@Import([ActionHistoryPersistenceAdapter, BusinessServicePersistenceAdapter, ChangeProductPersistenceAdapter,
        ChangeProfilePersistenceAdapter, ChangeUsageAdapter, ObjectKeyAdapter, ProductPersistenceAdapter,
        ProductionChangePersistenceAdapter])
abstract class PersistenceSpecification extends Specification {
}
