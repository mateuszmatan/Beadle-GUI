package com.bbh.itss.dso.portal.application.user

import com.bbh.itss.dso.portal.application.user.port.in.SignedInUser
import com.bbh.itss.dso.portal.application.user.port.out.SignedInUserPort
import spock.lang.Specification

class SignedInUserServiceSpec extends Specification {

    def "the signed-in user is the name the portal is configured with until single sign-on comes"() {
        expect:
        new SignedInUserService({ -> 'Mateusz Matan' } as SignedInUserPort).signedInUser() ==
                new SignedInUser('Mateusz Matan')
    }
}
