package com.bbh.itss.beadle.domain.change

import com.bbh.itss.beadle.domain.shared.InvalidRequestException
import com.bbh.itss.beadle.domain.shared.InvalidRequestException.FieldProblem
import spock.lang.Specification

import static com.bbh.itss.beadle.domain.change.SecureCodingTicket.DATE_MESSAGE
import static com.bbh.itss.beadle.support.ChangeFixtures.raised
import static com.bbh.itss.beadle.support.ChangeFixtures.secureCoding

class SecureCodingTicketSpec extends Specification {

    def "a ticket carries the change number, the product name, the trimmed date and the inputs"() {
        when:
        def ticket = SecureCodingTicket.of(raised(), secureCoding(), ' 10152026 ')

        then:
        ticket == new SecureCodingTicket('CHG0031001', 'CertScanner', '10152026', secureCoding())
    }

    def "the implementation date #date is refused"() {
        when:
        SecureCodingTicket.of(raised(), secureCoding(), date)

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('implementationDate', message)]

        where:
        date         || message
        null         || 'is required'
        ' '          || 'is required'
        '2026-10-15' || DATE_MESSAGE
        '15102026'   || DATE_MESSAGE
        '02302026'   || DATE_MESSAGE
        '1015202'    || DATE_MESSAGE
        '101520260'  || DATE_MESSAGE
        '+1015202'   || DATE_MESSAGE
    }

    def "every input of a ticket is required and every link must be one"() {
        when:
        SecureCodingTicket.of(raised(), new SecureCoding(' ', 'https://bitbucket', null, 'cert.qc.bbh.com'), '10152026')

        then:
        def refused = thrown(InvalidRequestException)
        refused.problems() == [new FieldProblem('apoNumber', 'is required'),
                               new FieldProblem('artifactLink', 'is required'),
                               new FieldProblem('qcApplicationLink', SecureCoding.LINK_MESSAGE)]
    }
}
