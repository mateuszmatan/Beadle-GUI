package com.bbh.itss.beadle.domain.shared

import spock.lang.Specification

import static com.bbh.itss.beadle.domain.shared.Text.abbreviateBytes
import static com.bbh.itss.beadle.domain.shared.Text.bytes
import static com.bbh.itss.beadle.domain.shared.Text.clean

class TextSpec extends Specification {

    def "clean trims the values and keeps the first occurrence of each, in their order, without blanks"() {
        expect:
        clean(['b', 'a', ' b', '', null, ' ']) == ['b', 'a']
        [clean(null), clean([])] == [[], []]
    }

    def "abbreviate cuts a text to the UTF-8 bytes of an Oracle column on a character boundary"() {
        expect:
        bytes('zażółć') == 10
        abbreviateBytes(null, 5) == null
        abbreviateBytes('abcdef', 6) == 'abcdef'
        abbreviateBytes('abc  defgh', 8) == 'abc...'
        abbreviateBytes('ąąąąą', 9) == 'ąąą...'
        abbreviateBytes('ab😀cd', 7) == 'ab...'
        bytes(abbreviateBytes('é' * 300, 160)) <= 160
    }

    def "a cleaned list cannot be changed"() {
        when:
        clean(['a']).add('b')

        then:
        thrown(UnsupportedOperationException)
    }
}
