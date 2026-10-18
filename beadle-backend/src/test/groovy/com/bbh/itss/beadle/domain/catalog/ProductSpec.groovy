package com.bbh.itss.beadle.domain.catalog

import spock.lang.Specification

import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION
import static com.bbh.itss.beadle.support.CatalogFixtures.details

class ProductSpec extends Specification {

    static final Product CERT = new Product(6L, details(), 2, null)

    def "a change of a product #based is refused as stale before its details are checked"() {
        given:
        ProductDirectory directory = Mock()

        when:
        CERT.change(version, details(name: ' '), directory)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION
        0 * directory._

        where:
        based                       | version
        'on an older version'       | 1L
        'on a version it never had' | 3L
        'without a version'         | null
    }
}
