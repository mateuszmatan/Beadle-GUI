package com.bbh.itss.beadle.frontend.regression

import com.bbh.itss.beadle.frontend.support.BeadleSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.beadle.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class ActionHistorySpec extends BeadleSpecification {

    static final String DESCRIPTION = 'What people did in Beadle and what Beadle-ProTech and Beadle-Internal reported ' +
            'back, newest first, with the request and the outcome of every action.'
    static final List<String> OBJECTS = ['Change CHG0031001', 'User Mateusz Matan', 'Ticket SCP-123',
                                         'Product CERTSCANNER', 'Change CHG0030995', 'Product PAYHUB']

    def "an auditor reads who did what, newest first, and opens the request and the message of an action"() {
        when:
        open('/')
        menuLink('Action History').click()
        page.waitForURL('**/action-history')

        then:
        assertThat(page.locator('h1')).hasText('Action History')
        assertThat(page.locator('.page-header .page-description')).hasText(DESCRIPTION)
        assertThat(activeMenuLink()).hasText('Action History')
        assertThat(gridHeaders()).hasText(['At', 'User', 'Action', 'Object', 'Outcome', 'Correlation'] as String[])
        assertThat(column('object')).hasText(OBJECTS as String[])
        assertThat(column('at').first()).hasText('15 Oct 2026, 09:00:05')
        assertThat(column('user')).hasText(['Mateusz Matan', 'Mateusz Matan', 'system:internal', 'Ann Lee',
                                            'system:protech', 'Ann Lee'] as String[])
        assertThat(column('action')).hasText(['Change raised', 'Signed in', 'Beadle-Internal callback',
                                              'Product updated', 'ProTech callback', 'Change raised'] as String[])
        assertThat(column('outcome')).hasText(['Success', 'Success', 'Failure', 'Success', 'Success', 'Failure'] as String[])
        assertThat(column('outcome').locator('.chip.danger')).hasCount(2)
        assertThat(column('correlation').first()).hasText('5b1e7c1a-2f4d-4c8e-9a10-000000000006')
        assertThat(page.locator('.list .shown')).hasText('Rows 1–6 of 6')
        assertThat(page.locator('.pager .position')).hasText('Page 1 of 1')
        assertThat(button('Previous', true)).isDisabled()
        assertThat(button('Next', true)).isDisabled()
        page.locator('.ag-header-cell-sortable').count() == 0
        awaitRequest('GET', '/api/action-history').params() == [page: '0', size: '50']

        when:
        gridCell(gridRow(page.locator('beadle-grid'), 'SCP-123'), 'action').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Beadle-Internal callback: Ticket SCP-123')
        assertThat(details('User')).hasText('system:internal')
        assertThat(details('Outcome')).hasText('Failure')
        assertThat(details('Duration')).hasText('15 ms')
        assertThat(details('Correlation id')).hasText('5b1e7c1a-2f4d-4c8e-9a10-000000000004')
        assertThat(dialog().locator('.message .banner')).hasText('No change waits for the ticket SCP-123')
        assertThat(dialog().locator('pre')).hasText('''{
  "callback": {
    "system": "CYBERTRACK",
    "key": "SCP-123",
    "status": "Done"
  }
}''')

        when:
        dialogButton('Close').click()

        then:
        assertThat(dialog()).hasCount(0)

        when:
        button('Details of Change raised at 15 Oct 2026, 09:00:05', true).focus()
        page.keyboard().press('Enter')

        then:
        assertThat(dialog().locator('h2')).hasText('Change raised: Change CHG0031001')
        assertThat(details('When')).hasText('15 Oct 2026, 09:00:05 in your time zone, UTC')
        assertThat(dialog().locator('.message')).hasCount(0)
        assertThat(dialog().locator('pre')).containsText('"fixVersion": "CERT 4.1"')

        when:
        page.keyboard().press('Escape')

        then:
        assertThat(dialog()).hasCount(0)
        ownErrors().isEmpty()
    }

    def "the filters ask the API again from the first page and a range that ends before it starts asks nothing"() {
        when:
        open('/action-history')

        then:
        assertThat(column('object')).hasCount(6)
        optionsOf(filters(), 'User') == ['Everyone', 'Ann Lee', 'Mateusz Matan', 'system:internal', 'system:protech']
        optionsOf(filters(), 'Action') == ['All actions', 'Beadle-Internal callback', 'ProTech callback', 'Change raised',
                                           'Product updated', 'Signed in']
        optionsOf(filters(), 'Outcome') == ['Any', 'Success', 'Failure']
        assertThat(button('Clear filters', true)).isDisabled()

        when:
        choose(filters(), 'User', 'Ann Lee')

        then:
        assertThat(column('object')).hasText(['Product CERTSCANNER', 'Product PAYHUB'] as String[])
        api.lastRequest('GET', '/api/action-history').params() == [user: 'Ann Lee', page: '0', size: '50']

        when:
        choose(filters(), 'Outcome', 'Failure')

        then:
        assertThat(column('object')).hasText(['Product PAYHUB'] as String[])
        api.lastRequest('GET', '/api/action-history').params() == [user: 'Ann Lee', outcome: 'FAILURE', page: '0', size: '50']

        when:
        choose(filters(), 'Action', 'Signed in')

        then:
        assertThat(page.locator('.beadle-grid-empty')).hasText('No action matches the filters.')
        assertThat(page.locator('.list .shown')).hasText('No rows')

        when:
        button('Clear filters', true).click()

        then:
        assertThat(column('object')).hasText(OBJECTS as String[])
        assertThat(selected(filters(), 'User')).hasText('Everyone')
        assertThat(button('Clear filters', true)).isDisabled()

        when:
        input(filters(), 'Object key').fill('chg')

        then:
        assertThat(column('object')).hasText(['Change CHG0031001', 'Change CHG0030995'] as String[])
        api.lastRequest('GET', '/api/action-history').params() == [objectKey: 'chg', page: '0', size: '50']

        when:
        input(filters(), 'Object key').fill('')
        input(filters(), 'From').fill('2026-10-14')
        input(filters(), 'To').fill('2026-10-14')

        then:
        assertThat(column('object')).hasText(['Ticket SCP-123', 'Product CERTSCANNER'] as String[])
        api.lastRequest('GET', '/api/action-history').params() ==
                [from: '2026-10-14T00:00:00.000Z', to: '2026-10-15T00:00:00.000Z', page: '0', size: '50']

        when:
        def asked = api.requests('GET', '/api/action-history').size()
        input(filters(), 'To').fill('2026-10-13')

        then:
        hasErrors(filters(), [To: 'The last day comes before the first'])
        assertThat(page.locator('.beadle-grid-empty')).hasText('The last day comes before the first: change From or To.')
        assertThat(column('object')).hasCount(0)
        api.requests('GET', '/api/action-history').size() == asked
        ownErrors().isEmpty()
    }

    Locator filters() {
        page.locator('section.filters')
    }

    Locator column(String key) {
        gridCells(page.locator('beadle-grid'), key)
    }

    Locator details(String term) {
        dialog().locator("dl.rows dt:text-is('${term}') + dd")
    }
}
