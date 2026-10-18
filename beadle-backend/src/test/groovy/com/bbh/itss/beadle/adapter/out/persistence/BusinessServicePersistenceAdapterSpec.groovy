package com.bbh.itss.beadle.adapter.out.persistence

import com.bbh.itss.beadle.domain.catalog.BusinessService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.jdbc.core.JdbcTemplate

import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION

class BusinessServicePersistenceAdapterSpec extends PersistenceSpecification {

    @Autowired
    BusinessServicePersistenceAdapter adapter

    @Autowired
    TestEntityManager entities

    @Autowired
    JdbcTemplate jdbc

    def "the database starts with the five BBH business services and finds them by id and by name in any case"() {
        expect:
        adapter.findAll().sort(false) { it.id() } == [
                new BusinessService(1L, 'AI Lab', 0), new BusinessService(2L, 'Capital Partners', 0),
                new BusinessService(3L, 'Corporate Technology', 0), new BusinessService(4L, 'Custody', 0),
                new BusinessService(5L, 'Fund Services', 0)]
        adapter.load(3L) == Optional.of(new BusinessService(3L, 'Corporate Technology', 0))
        adapter.load(9999L) == Optional.empty()
        adapter.findByName('fund SERVICES') == Optional.of(new BusinessService(5L, 'Fund Services', 0))
        adapter.findByName('Treasury') == Optional.empty()
    }

    def "a business service is created, renamed to a new version and deleted"() {
        when:
        def created = adapter.save(BusinessService.create('Treasury', { adapter.findByName(it) }))
        entities.clear()
        def renamed = adapter.save(adapter.load(created.id()).get().rename(0L, 'Treasury Services',
                { adapter.findByName(it) }))
        entities.clear()

        then:
        created.id() > 5
        created.version() == 0
        renamed == new BusinessService(created.id(), 'Treasury Services', 1)
        jdbc.queryForMap('SELECT NAME, VERSION FROM BEADLE_BUSINESS_SERVICE WHERE ID = ?', created.id()) ==
                [NAME: 'Treasury Services', VERSION: 1]

        when:
        adapter.delete(created.id())
        adapter.delete(9999L)
        entities.flush()

        then:
        adapter.load(created.id()) == Optional.empty()
        jdbc.queryForObject('SELECT COUNT(*) FROM BEADLE_BUSINESS_SERVICE', Integer) == 5
    }

    def "a business service #change is not written"() {
        when:
        adapter.save(businessService)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        jdbc.queryForObject('SELECT NAME FROM BEADLE_BUSINESS_SERVICE WHERE ID = 4', String) == 'Custody'

        where:
        change                                | businessService
        'read at another version than stored' | new BusinessService(4L, 'Stale', 3)
        'deleted in the meantime'             | new BusinessService(404L, 'Gone', 0)
    }
}
