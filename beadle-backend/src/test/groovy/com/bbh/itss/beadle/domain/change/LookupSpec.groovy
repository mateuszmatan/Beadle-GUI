package com.bbh.itss.beadle.domain.change

import spock.lang.Specification

import static com.bbh.itss.beadle.domain.change.LookupKind.ASSIGNMENT_GROUPS
import static com.bbh.itss.beadle.domain.change.LookupKind.CONFIGURATION_ITEMS

class LookupSpec extends Specification {

    def "a lookup kind is found by the path of its URL"() {
        expect:
        LookupKind.values()*.path() == ['users', 'business-services', 'assignment-groups', 'releases', 'configuration-items',
                                        'incidents', 'problems', 'clients']
        LookupKind.values().every { LookupKind.of(it.path()) == it }
        LookupKind.of('assignment-groups') == ASSIGNMENT_GROUPS
        LookupKind.of('configuration-items') == CONFIGURATION_ITEMS
    }

    def "an unknown lookup kind #path does not exist"() {
        when:
        LookupKind.of(path)

        then:
        def missing = thrown(NoSuchElementException)
        missing.message == "Lookup $path does not exist"

        where:
        path << ['owners', 'USERS', 'assignment_groups', null]
    }
}
