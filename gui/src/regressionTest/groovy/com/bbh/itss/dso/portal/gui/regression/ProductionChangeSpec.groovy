package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator

import java.time.Instant

import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static com.microsoft.playwright.options.AriaRole.RADIO
import static java.time.DayOfWeek.SATURDAY
import static java.time.ZoneOffset.UTC

class ProductionChangeSpec extends EditorSpecification {

    def "a release manager raises a production change from Jira epics and stories with a task per service"() {
        when:
        open('/beadle')
        link('Open', true).click()
        page.waitForURL('**/beadle/changes')

        then:
        assertThat(page.locator('h1')).hasText('Production Change')
        assertThat(page.locator('dso-integration-note')).containsText('Jira is not connected yet')
        assertThat(page.locator('tbody tr td:first-child')).hasText(['CHG0031001'] as String[])

        when:
        link('Raise a production change', true).click()
        button('Continue', true).click()

        then:
        assertThat(page.locator('.step-bar .step-label'))
                .hasText(['Product', 'Jira scope', 'Window', 'Review', 'Raised'] as String[])
        assertThat(choiceError()).hasText('Choose the product')

        when:
        choose(step(), 'Department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')

        then:
        assertThat(review('Risk and impact')).hasText('Moderate risk · Low impact')
        assertThat(review('Approvers')).hasText('Olivia Bennett, James Carter')
        assertThat(checkbox(step(), 'gui')).isChecked()
        assertThat(checkbox(step(), 'backend-api')).isChecked()

        when:
        checkbox(step(), 'backend-api').uncheck()
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Jira scope')
        assertThat(step().locator('.issues .key')).hasText(['CERT-120', 'CERT-130', 'CERT-140'] as String[])
        assertThat(choiceError()).hasText('Choose at least one epic')

        when:
        checkbox(step(), 'CERT-120').check()

        then:
        assertThat(step().locator('.story-group h4')).hasText('CERT-120 Expiry alerts for certificates')
        assertThat(checkbox(step(), 'CERT-121')).isChecked()
        assertThat(checkbox(step(), 'CERT-122')).isChecked()

        when:
        checkbox(step(), 'CERT-122').uncheck()
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(choiceError()).hasText('Choose the change window')

        when:
        tile(step(), 'This weekend').click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(input(step(), 'Short description')).hasValue('CertScanner release: Expiry alerts for certificates')
        assertThat(input(step(), 'Description')).hasValue(
                'Production release of CertScanner (CERTSCANNER).\n\nScope from Jira project CERT:\n'
                        + 'CERT-120 Expiry alerts for certificates (In Review)')
        assertThat(step().locator('.tasks strong')).hasText(['Deploy gui of CertScanner to production'] as String[])
        assertThat(review('Jira scope')).hasText('1 epic and 1 story')

        when:
        input(step(), 'Short description').fill('CertScanner 2.4 release')
        button('Raise the change in ServiceNow', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CHG0031002 is raised')
        assertThat(step().locator('.review-services li')).hasText(['CTASK0310021 · Deploy gui of CertScanner to production'] as String[])
        with(awaitRequest('POST', '/api/changes').json()) {
            productId == 1
            serviceIds == [1]
            epicKeys == ['CERT-120']
            storyKeys == ['CERT-121']
            shortDescription == 'CertScanner 2.4 release'
            Instant.parse(start as String).atZone(UTC).dayOfWeek == SATURDAY
            Instant.parse(start as String).atZone(UTC).hour == 6
        }

        when:
        link('Open the change', true).click()

        then:
        assertThat(page.locator('h1')).hasText('CHG0031002')
        assertThat(page.locator('.tasks')).containsText('CTASK0310021')
        ownErrors().isEmpty()
    }

    def "a product without a ServiceNow change template gets one before its first change"() {
        when:
        open('/beadle/changes/new')
        choose(step(), 'Department', 'Fund Services')
        choose(step(), 'Product', 'Payments Hub (PAYHUB)')
        button('Continue', true).click()

        then:
        assertThat(step().locator('.banner')).containsText('Payments Hub has no ServiceNow change template yet')
        assertThat(choiceError()).hasText('The product needs its ServiceNow change template first')

        when:
        link('Fill it in', true).click()
        page.waitForURL('**/products/2/change')

        then:
        assertThat(page.locator('h1')).hasText('ServiceNow change template of Payments Hub')
        assertThat(page.locator('.banner.info')).containsText('Not saved yet')
        assertThat(input(page.locator('form'), 'Jira project key')).hasValue('PAYH')

        when:
        button('Save template', true).click()

        then:
        hasErrors(page.locator('form'), ['Approvers': 'Required', 'Risk assessment': 'Required'])
        assertThat(saveError()).hasText('Some fields need your attention.')

        when:
        fillIn(page.locator('form'), ['Approvers': 'Emma Brooks\nHenry Collins',
                                      'Risk assessment': 'Payments run through it during the day.'])
        choose(page.locator('form'), 'Risk', 'High')
        button('Save template', true).click()

        then:
        assertThat(snackBar()).containsText('The ServiceNow change template of Payments Hub is saved')
        with(awaitRequest('PUT', '/api/products/2/change-profile').json()) {
            version == null
            template.approvers == ['Emma Brooks', 'Henry Collins']
            template.risk == 'HIGH'
            template.jiraProjectKey == 'PAYH'
        }
        ownErrors().isEmpty()
    }

    def "a change ServiceNow refuses stays on the review with the reasons"() {
        given:
        api.respond('POST', '/api/changes', problem(400, 'Bad Request', '2 fields are invalid',
                [errors: [[field: 'start', message: 'must be in the future'],
                          [field: 'epicKeys', message: 'CERT-120 is not in Jira project CERT']]]))

        when:
        open('/beadle/changes/new')
        choose(step(), 'Department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')
        button('Continue', true).click()
        checkbox(step(), 'CERT-120').check()
        button('Continue', true).click()
        tile(step(), 'Tonight').click()
        button('Continue', true).click()
        button('Raise the change in ServiceNow', true).click()

        then:
        assertThat(page.locator('.save-problem strong')).hasText('2 fields are invalid')
        assertThat(page.locator('.save-problem li'))
                .hasText(['must be in the future', 'CERT-120 is not in Jira project CERT'] as String[])
        assertThat(currentStep()).hasText('Review')

        when:
        page.locator('.step-bar').getByRole(BUTTON)
                .filter(new Locator.FilterOptions().setHasText('Window')).click()
        tile(step(), 'Another time').click()

        then:
        assertThat(input(step(), 'Starts')).hasValue(~/.*T06:00$/)
        assertThat(step().locator('.window-text')).containsText('06:00 to 10:00')
    }

    Locator step() {
        page.locator('section.step')
    }

    Locator currentStep() {
        page.locator('.step-bar .current .step-label')
    }

    Locator choiceError() {
        step().locator('.choice-error')
    }

    Locator review(String term) {
        step().locator("dl.rows dt:text-is('${term}') + dd")
    }

    Locator tile(Locator scope, String label) {
        scope.getByRole(RADIO, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }
}
