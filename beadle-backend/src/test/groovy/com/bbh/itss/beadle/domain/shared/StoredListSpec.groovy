package com.bbh.itss.beadle.domain.shared

import spock.lang.Specification

import static com.bbh.itss.beadle.domain.shared.StoredList.LINES_1000
import static com.bbh.itss.beadle.domain.shared.StoredList.LINES_4000
import static com.bbh.itss.beadle.support.ChangeFixtures.reported

class StoredListSpec extends Specification {

    def "a list fits its column when its entries joined by the separator take at most the column's bytes"() {
        expect:
        new StoredList(separator, 10).fits(values) == fits

        where:
        separator | values                     || fits
        '\n'      | []                         || true
        '\n'      | ['12345', '1234']          || true
        '\n'      | ['12345', '12345']         || false
        ','       | ['abc', 'def', 'gh']       || true
        ','       | ['\u017c\u00f3\u0142\u0107', 'a']  || true
        ','       | ['\u017c\u00f3\u0142\u0107', 'ab'] || false
    }

    def "an entry list longer than its column is reported against its field, each list with the size of its column"() {
        given:
        def problems = new ValidationProblems()

        when:
        LINES_1000.check(problems.at('flutter'), 'modules', ['m' * 600, 'n' * 400])
        LINES_4000.check(problems, 'agentLabels', ['a' * 2000, 'b' * 1999])

        then:
        reported(problems)*.field == ['flutter.modules']
        reported(problems)*.message == ['is too long: all entries together may take at most 1000 bytes']
        [LINES_1000, LINES_4000].collect { [it.separator(), it.maxBytes()] } == [['\n', 1000], ['\n', 4000]]
    }
}
