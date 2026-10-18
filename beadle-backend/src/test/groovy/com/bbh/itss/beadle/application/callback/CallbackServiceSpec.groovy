package com.bbh.itss.beadle.application.callback

import com.bbh.itss.beadle.application.change.port.in.ProductionChangesUseCase
import com.bbh.itss.beadle.domain.callback.InternalCallback
import com.bbh.itss.beadle.domain.callback.ProTechCallback
import spock.lang.Specification

import java.time.Instant

import static com.bbh.itss.beadle.domain.callback.InternalSystem.CYBERTRACK
import static com.bbh.itss.beadle.domain.callback.ProTechEvent.APPROVAL_CHANGED
import static com.bbh.itss.beadle.domain.change.ChangeState.SUPPORT_APPROVAL
import static com.bbh.itss.beadle.support.ChangeFixtures.issued

class CallbackServiceSpec extends Specification {

    static final Instant AT = Instant.parse('2026-10-15T09:00:00Z')

    ProductionChangesUseCase changes = Mock()
    def service = new CallbackService(changes)

    def "a ProTech callback reads the change again, if Beadle created it"() {
        given:
        def change = issued(state: SUPPORT_APPROVAL)

        when:
        def read = service.proTech(new ProTechCallback(number, APPROVAL_CHANGED, SUPPORT_APPROVAL, AT))

        then:
        1 * changes.resync(number) >> Optional.ofNullable(known ? change : null)
        read == Optional.ofNullable(known ? change : null)

        where:
        number       | known
        'CHG0031001' | true
        'CHG0099999' | false
    }

    def "an Internal callback asks no other system"() {
        when:
        service.internal(new InternalCallback(CYBERTRACK, 'SCP-123', 'Done', AT))

        then:
        0 * changes._
    }
}
