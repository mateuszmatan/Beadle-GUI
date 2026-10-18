package com.bbh.itss.beadle.domain.catalog

import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import java.util.function.Function

import static com.bbh.itss.beadle.domain.shared.Failures.STALE_VERSION

class BusinessServiceSpec extends Specification {

    static final BusinessService CUSTODY = new BusinessService(4L, 'Custody', 2)

    static final Function<String, Optional<BusinessService>> nobody = { Optional.empty() }
    static final Function<String, Optional<BusinessService>> custody = { Optional.of(CUSTODY) }

    def "a new business service keeps its name trimmed and starts at version 0"() {
        expect:
        BusinessService.create('  AI Lab ', nobody) == new BusinessService(null, 'AI Lab', 0)
        BusinessService.create('D' * 100, nobody).name().length() == 100
        BusinessService.create('Ł' * 50, nobody).name().length() == 50
        new BusinessService(1L, null, 0).name() == null
    }

    def "a business service name that is #problem is refused"() {
        when:
        BusinessService.create(name, nobody)

        then:
        def e = thrown(InvalidRequestException)
        e.problems() == [new FieldProblem('name', message)]

        where:
        problem                          | name              || message
        'missing'                        | null              || 'must not be blank'
        'blank'                          | '   '             || 'must not be blank'
        'too long'                       | 'D' * 101         || 'is too long: it may take at most 100 bytes'
        'short in characters, not bytes' | 'Księgowość ' * 9 || 'is too long: it may take at most 100 bytes'
    }

    def "a name another business service uses, in any case, is refused"() {
        when:
        change()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'A business service named Custody already exists'

        where:
        change << [{ BusinessService.create(' custody ', custody) },
                   { new BusinessService(5L, 'Fund Services', 0).rename(0L, 'CUSTODY', custody) }]
    }

    def "a rename keeps the id and the version and may change the case of the business service's own name"() {
        expect:
        CUSTODY.rename(2L, ' CUSTODY ', custody) == new BusinessService(4L, 'CUSTODY', 2)
        CUSTODY.rename(2L, 'Custody and Trust', nobody) == new BusinessService(4L, 'Custody and Trust', 2)
    }

    def "a rename #based is refused as stale before the name is checked"() {
        when:
        CUSTODY.rename(version, ' ', nobody)

        then:
        def e = thrown(IllegalStateException)
        e.message == STALE_VERSION

        where:
        based                       | version
        'on an older version'       | 1L
        'on a version it never had' | 3L
        'without a version'         | null
    }
}
