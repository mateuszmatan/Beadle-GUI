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

    def "signing in answers the signed-in user, whose name is the key of the USER_SIGNED_IN row"() {
        when:
        def user = new SignedInUserService({ -> 'Mateusz Matan' } as SignedInUserPort).signIn()

        then:
        user == new SignedInUser('Mateusz Matan')
        user.auditedObject() == ActionObject.keyed(USER, 'Mateusz Matan')
    }
}
