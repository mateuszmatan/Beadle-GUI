package com.bbh.itss.beadle.application.audit

import com.bbh.itss.beadle.application.audit.port.out.ActionHistoryPort
import com.bbh.itss.beadle.domain.audit.ActionHistoryPage
import com.bbh.itss.beadle.domain.audit.ActionHistoryQuery
import spock.lang.Specification

import static com.bbh.itss.beadle.domain.audit.Action.CHANGE_RAISED
import static com.bbh.itss.beadle.domain.audit.Action.PRODUCT_CREATED

class ActionHistoryServiceSpec extends Specification {

    ActionHistoryPort history = Mock()
    def service = new ActionHistoryService(history)

    def "the audit trail is read through its port, with the filter lists"() {
        given:
        def query = ActionHistoryQuery.of('Ann Lee', CHANGE_RAISED, null, 'CHG', null, null, null, 2, 20)
        def page = new ActionHistoryPage([], 2, 20, 41)

        when:
        def found = service.find(query)
        def users = service.users()
        def actions = service.actions()

        then:
        found == page
        users == ['Ann Lee', 'Mateusz Matan']
        actions == [CHANGE_RAISED, PRODUCT_CREATED]
        1 * history.find(query) >> page
        1 * history.users() >> ['Ann Lee', 'Mateusz Matan']
        1 * history.actions() >> [CHANGE_RAISED, PRODUCT_CREATED]
    }

    def "a query without paging asks for the first 50 rows"() {
        expect:
        with(ActionHistoryQuery.of('  ', null, null, ' CHG0031001 ', null, null, null, null, null)) {
            page() == 0
            size() == ActionHistoryQuery.DEFAULT_SIZE
            user() == null
            objectKey() == 'CHG0031001'
        }
    }
}
