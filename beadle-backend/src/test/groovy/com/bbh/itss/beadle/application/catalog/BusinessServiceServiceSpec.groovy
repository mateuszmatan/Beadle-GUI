package com.bbh.itss.beadle.application.catalog

import com.bbh.itss.beadle.application.catalog.port.in.BusinessServiceView
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceRepositoryPort
import com.bbh.itss.beadle.application.catalog.port.out.BusinessServiceUsagePort
import com.bbh.itss.beadle.domain.catalog.BusinessService
import com.bbh.itss.beadle.domain.catalog.BusinessServiceUsage
import spock.lang.Specification

import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION
import static org.spockframework.mock.EmptyOrDummyResponse.INSTANCE

class BusinessServiceServiceSpec extends Specification {

    static final BusinessService AI_LAB = new BusinessService(1L, 'AI Lab', 0)
    static final BusinessService CORPORATE = new BusinessService(3L, 'corporate Technology', 1)
    static final BusinessService CUSTODY = new BusinessService(4L, 'Custody', 0)
    static final BusinessService FUND_SERVICES = new BusinessService(5L, 'Fund Services', 0)
    static final BusinessServiceUsage UNUSED = BusinessServiceUsage.UNUSED
    static final BusinessServiceUsage TWO_PRODUCTS = new BusinessServiceUsage(2, 3)
    static final BusinessServiceUsage IN_USE = new BusinessServiceUsage(0, 1)

    BusinessServiceRepositoryPort businessServices = Mock(defaultResponse: INSTANCE)
    BusinessServiceUsagePort usage = Stub() {
        perBusinessService() >> [3L: TWO_PRODUCTS, 4L: IN_USE]
    }
    def service = new BusinessServiceService(businessServices, usage)

    def "business services are listed by name in any case with what the application counts for them"() {
        given:
        businessServices.findAll() >> [FUND_SERVICES, AI_LAB, CORPORATE, new BusinessService(2L, 'Capital Partners', 0)]

        expect:
        service.list() == [new BusinessServiceView(1, 'AI Lab', 0, UNUSED),
                           new BusinessServiceView(2, 'Capital Partners', 0, UNUSED),
                           new BusinessServiceView(3, 'corporate Technology', 1, TWO_PRODUCTS),
                           new BusinessServiceView(5, 'Fund Services', 0, UNUSED)]
    }

    def "a new business service is stored under its trimmed name and shown unused"() {
        when:
        def created = service.create(' Treasury ')

        then:
        1 * businessServices.save(new BusinessService(null, 'Treasury', 0)) >> new BusinessService(6L, 'Treasury', 0)
        businessServices.findAll() >> [AI_LAB, new BusinessService(6L, 'Treasury', 0)]
        created == new BusinessServiceView(6, 'Treasury', 0, UNUSED)
    }

    def "a renamed business service is shown with its usage"() {
        given:
        def stored = new BusinessService(3L, 'Corporate Technology', 2)
        businessServices.load(3L) >> Optional.of(CORPORATE)

        when:
        def renamed = service.rename(3L, 1L, 'Corporate Technology')

        then:
        1 * businessServices.save(new BusinessService(3L, 'Corporate Technology', 1)) >> stored
        businessServices.findAll() >> [stored]
        renamed == new BusinessServiceView(3, 'Corporate Technology', 2, TWO_PRODUCTS)
    }

    def "an unused business service is deleted"() {
        given:
        businessServices.findAll() >> [AI_LAB, CORPORATE]

        when:
        service.delete(1L)

        then:
        1 * businessServices.delete(1L)
    }

    def "#refusal is refused before anything is stored"() {
        given:
        businessServices.findAll() >> [AI_LAB, CORPORATE, CUSTODY, FUND_SERVICES]
        businessServices.load(3L) >> Optional.of(CORPORATE)
        businessServices.findByName('fund services') >> Optional.of(FUND_SERVICES)

        when:
        action(service)

        then:
        def e = thrown(IllegalStateException)
        e.message == message
        0 * businessServices.save(_)
        0 * businessServices.delete(_)

        where:
        refusal                                     | action                                        || message
        'a new name in use'                         | { it.create('fund services') }                || 'A business service named Fund Services already exists'
        'a rename to a name in use'                 | { it.rename(3L, 1L, 'fund services') }        || 'A business service named Fund Services already exists'
        'a rename of an old version'                | { it.rename(3L, 0L, 'Corporate Technology') } || STALE_VERSION
        'deleting a business service with products'       | { it.delete(3L) }                             || 'corporate Technology still has 2 product(s). Move them to another business service first.'
        'deleting a business service with changes'         | { it.delete(4L) }                            || 'Custody still owns 1 change(s), so it cannot be deleted.'
    }

    def "an unknown business service is not found, renamed or deleted"() {
        when:
        action(service)

        then:
        def e = thrown(NoSuchElementException)
        e.message == 'Business service 9 does not exist'
        0 * businessServices.save(_)
        0 * businessServices.delete(_)

        where:
        action << [{ it.rename(9L, 0L, 'Custody') }, { it.delete(9L) }]
    }
}
