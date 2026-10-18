package com.bbh.itss.beadle.application.user

import com.bbh.itss.beadle.application.user.port.in.SignedInUser
import com.bbh.itss.beadle.application.user.port.out.SignedInUserPort
import com.bbh.itss.beadle.domain.audit.ActionObject
import spock.lang.Specification

import static com.bbh.itss.beadle.domain.audit.ObjectType.USER

class SignedInUserServiceSpec extends Specification {

    def "the signed-in user is the name and the e-mail address the port knows"() {
        given:
        SignedInUserPort port = Stub() {
            name() >> 'Ann Lee'
            email() >> Optional.of('ann.lee@bbh.com')
        }

        expect:
        new SignedInUserService(port).signedInUser() == new SignedInUser('Ann Lee', 'ann.lee@bbh.com')
        new SignedInUserService({ -> 'Mateusz Matan' } as SignedInUserPort).signedInUser() ==
                new SignedInUser('Mateusz Matan', null)
    }

    def "signing #inOrOut answers the signed-in user, whose name is the key of the row"() {
        when:
        def user = signing(new SignedInUserService({ -> 'Mateusz Matan' } as SignedInUserPort))

        then:
        user == new SignedInUser('Mateusz Matan', null)
        user.auditedObject() == ActionObject.keyed(USER, 'Mateusz Matan')

        where:
        inOrOut | signing
        'in'    | { SignedInUserService users -> users.signIn() }
        'out'   | { SignedInUserService users -> users.signOut() }
    }
}
