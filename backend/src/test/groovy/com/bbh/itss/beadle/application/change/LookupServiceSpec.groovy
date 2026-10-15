package com.bbh.itss.beadle.application.change

import com.bbh.itss.beadle.application.change.port.out.ProTechLookupPort
import com.bbh.itss.beadle.domain.change.Lookup
import spock.lang.Specification

import static com.bbh.itss.beadle.domain.change.LookupKind.DEPARTMENTS
import static com.bbh.itss.beadle.domain.change.LookupKind.USERS

class LookupServiceSpec extends Specification {

    ProTechLookupPort port = Mock()
    def service = new LookupService(port)

    def "a lookup asks ProTech for at most 20 entries of its kind with the trimmed query"() {
        given:
        def found = [new Lookup('Grace Turner', 'grace.turner@bbh.com')]

        when:
        def users = service.find('users', '  grace ')
        def departments = service.find('departments', null)

        then:
        1 * port.find(USERS, 'grace', 20) >> found
        1 * port.find(DEPARTMENTS, '', 20) >> []
        users == found
        departments == []
    }

    def "a lookup of an unknown kind is not found and asks nothing"() {
        when:
        service.find('owners', 'x')

        then:
        thrown(NoSuchElementException)
        0 * port._
    }
}
