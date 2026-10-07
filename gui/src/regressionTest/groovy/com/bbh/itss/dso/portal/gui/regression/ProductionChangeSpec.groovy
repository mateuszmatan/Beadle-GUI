package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.RELEASE_DATE
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.OPTION
import static java.time.LocalDate.now
import static java.time.LocalDate.parse

class ProductionChangeSpec extends EditorSpecification {

    def "a release manager raises a production change from a FixVersion with the ServiceNow fields and the schedule"() {
        when:
        open('/beadle')
        menuLink('Production Change').click()
        page.waitForURL('**/beadle/changes')

        then:
        assertThat(page.locator('h1')).hasText('Production Change')
        assertThat(page.locator('dso-integration-note')).containsText('Jira is not connected yet')
        assertThat(page.locator('tbody tr td:first-child')).hasText(['CHG0031001'] as String[])
        assertThat(page.locator('tbody tr td:nth-child(3)')).hasText(['CERT 4.1'] as String[])

        when:
        link('Raise a production change', true).click()
        button('Continue', true).click()

        then:
        assertThat(page.locator('.step-bar .step-label'))
                .hasText(['Product', 'Jira scope', 'Details', 'Schedule', 'Review', 'Raised'] as String[])
        assertThat(choiceError()).hasText('Choose the product')

        when:
        choose(step(), 'Department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')

        then:
        assertThat(review('Approvers')).hasText('Olivia Bennett, James Carter, Grace Turner')
        assertThat(review('Jira project')).hasText('CERT')
        assertThat(step().locator('.defaults-note')).hasCount(0)

        when:
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Jira scope')
        assertThat(choiceError()).hasText('Enter the FixVersion of the release')
        assertThat(input(step(), 'Jira project')).hasValue('CERT')

        when:
        select(step(), 'FixVersion').click()

        then:
        assertThat(page.getByRole(OPTION)).hasText(["CERT 4.2 · unreleased · $RELEASE_DATE", 'CERT 4.3 · unreleased',
                                                    "CERT 4.1 · released · ${now().minusDays(20)}"] as String[])

        when:
        page.getByRole(OPTION).first().click()

        then:
        assertThat(select(step(), 'FixVersion')).hasValue('CERT 4.2')
        assertThat(step().locator('.issues .key')).hasText(['CERT-120', 'CERT-130'] as String[])
        awaitRequest('GET', '/api/products/1/jira/epics').params() == [fixVersion: 'CERT 4.2']

        when:
        checkbox(step(), 'CERT-120').check()

        then:
        assertThat(step().locator('.story-group h4')).hasText('CERT-120 Expiry alerts for certificates')
        assertThat(checkbox(step(), 'CERT-121')).isChecked()
        assertThat(checkbox(step(), 'CERT-122')).isChecked()
        awaitRequest('GET', '/api/products/1/jira/stories').params() == [fixVersion: 'CERT 4.2', epics: 'CERT-120']

        when:
        checkbox(step(), 'CERT-122').uncheck()
        checkbox(step(), 'backend-api').uncheck()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Details')
        hasValues(step(), ['Release': 'CERT 4.2', 'Assignment group': 'Technology Architecture', 'L1 manager': 'Olivia Bennett',
                           'Installation start': '18:00', 'BBH users': '25'])

        when:
        fillIn(step(), ['Incident': 'INC0012345', 'Backout plan': ''])
        select(step(), 'Business impact').fill('Medium')
        select(step(), 'Business impact').press('Escape')
        checkbox(step(), 'Privileged access needed').check()
        fillIn(step(), ['User': 'Jane Smith', 'Privileged account': 'adm_jsmith'])
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Details')
        assertThat(errorOf(step(), 'Backout plan')).hasText('Required')
        assertThat(choiceError()).hasText('Some fields need your attention.')

        when:
        input(step(), 'Backout plan').fill('Redeploy CERT 4.1 from Nexus.')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Schedule')
        hasValues(step(), ['Installation date'      : RELEASE_DATE,
                           'Installation start time': '18:00', 'Installation end time': '20:00',
                           'Validation start time'  : '20:00', 'Validation end time': '21:00',
                           'First usage date'       : RELEASE_DATE, 'First usage time': '21:00'])

        when:
        fillIn(step(), ['First usage date': nextDay(), 'First usage time': '08:00'])
        checkbox(step(), 'Downtime during the installation').check()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(input(step(), 'Short description')).hasValue('CertScanner CERT 4.2: Expiry alerts for certificates')
        assertThat(input(step(), 'Description')).hasValue(
                'Production release of CertScanner (CERTSCANNER), FixVersion CERT 4.2.\n\nEpics:\n'
                        + 'CERT-120 Expiry alerts for certificates (In Review)\n\nStories:\nCERT-121 E-mail the certificate owner')
        assertThat(review('FixVersion')).hasText('CERT 4.2')
        assertThat(review('Incident')).hasText('INC0012345')
        assertThat(review('Business impact')).hasText('Medium')
        assertThat(review('Jane Smith')).hasText('adm_jsmith')
        assertThat(review('Downtime')).hasText('Yes')
        assertThat(step().locator('.tasks strong')).hasText(['Deploy gui of CertScanner to production'] as String[])
        assertThat(step().locator('.scope')).hasText('1 epic and 1 story of FixVersion CERT 4.2')
        with(awaitRequest('POST', '/api/changes/preview').json()) {
            fixVersion == 'CERT 4.2'
            schedule == [installationStart: "${RELEASE_DATE}T18:00:00.000Z".toString(),
                         installationEnd  : "${RELEASE_DATE}T20:00:00.000Z".toString(),
                         validationStart  : "${RELEASE_DATE}T20:00:00.000Z".toString(),
                         validationEnd    : "${RELEASE_DATE}T21:00:00.000Z".toString(),
                         firstUsage       : "${nextDay()}T08:00:00.000Z".toString()]
            template.release == 'CERT 4.2'
            template.incident == 'INC0012345'
            template.downtime
            template.planning.backoutPlan == 'Redeploy CERT 4.1 from Nexus.'
            template.privilegedAccess == [required: true, users: [[user: 'Jane Smith', account: 'adm_jsmith']]]
            template.riskAssessment.businessImpact == 'Medium'
        }

        when:
        input(step(), 'Short description').fill('CertScanner 4.2 release')
        button('Raise the change in ServiceNow', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CHG0031002 is raised')
        assertThat(step().locator('.review-services li')).hasText(['CTASK0310021 · Deploy gui of CertScanner to production'] as String[])
        assertThat(step().locator('.next-steps')).containsText('Olivia Bennett, James Carter, Grace Turner approve the change')
        with(awaitRequest('POST', '/api/changes').json()) {
            productId == 1
            serviceIds == [1]
            fixVersion == 'CERT 4.2'
            epicKeys == ['CERT-120']
            storyKeys == ['CERT-121']
            shortDescription == 'CertScanner 4.2 release'
            schedule.installationStart == "${RELEASE_DATE}T18:00:00.000Z"
        }

        when:
        link('Open the change', true).click()

        then:
        assertThat(page.locator('h1')).hasText('CHG0031002')
        assertThat(page.locator('.tasks')).containsText('CTASK0310021')
        assertThat(term(page.locator('dso-change-summary'), 'Jane Smith')).hasText('adm_jsmith')
        assertThat(term(page.locator('dso-change-summary'), 'FixVersion')).hasText('CERT 4.2')
        ownErrors().isEmpty()
    }

    def "a product without ServiceNow defaults raises a change with the suggested values from another Jira project"() {
        when:
        open('/beadle/changes/new')
        choose(step(), 'Department', 'Fund Services')
        choose(step(), 'Product', 'Payments Hub (PAYHUB)')

        then:
        assertThat(step().locator('.defaults-note')).containsText(
                'Payments Hub has no ServiceNow defaults yet, so the suggested values are filled in.')
        assertThat(step().locator('.defaults-note a')).hasAttribute('href', '/beadle/admin/products/2')
        assertThat(review('Approvers')).hasText('not set')

        when:
        button('Continue', true).click()
        input(step(), 'Jira project').fill('pay')
        select(step(), 'FixVersion').fill('PAY 4.3')
        select(step(), 'FixVersion').press('Escape')
        button('Find epics', true).click()

        then:
        assertThat(step().locator('.issues .key')).hasText(['PAY-130'] as String[])
        awaitRequest('GET', '/api/products/2/jira/epics').params() == [fixVersion: 'PAY 4.3', project: 'PAY']

        when:
        checkbox(step(), 'PAY-130').check()

        then:
        assertThat(checkbox(step(), 'PAY-132')).isChecked()

        when:
        button('Continue', true).click()

        then:
        hasValues(step(), ['Jira project': 'pay', 'Release': 'PAY 4.3', 'Affected CI': 'Payments Hub',
                           'Assignment group': 'Payments Engineering', 'L1 manager': '', 'BBH users': ''])

        when:
        input(step(), 'L1 manager').fill('Emma Brooks')
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Schedule')
        assertThat(input(step(), 'Installation date')).hasValue('')
        assertThat(choiceError()).hasText('Enter the date and time of the installation start')

        when:
        input(step(), 'Installation date').fill(inDays(3))

        then:
        assertThat(step().locator('.window-text')).containsText('18:00 to 20:00')

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        with(awaitRequest('POST', '/api/changes/preview').json()) {
            productId == 2
            serviceIds == [3, 4, 5, 6]
            epicKeys == ['PAY-130']
            storyKeys == ['PAY-132']
            template.jiraProjectKey == 'PAY'
            template.approvers == [l1Manager: 'Emma Brooks', l2Manager: null, businessApprover: null]
            template.riskAssessment.bbhUsers == null
        }

        when:
        stepButton('Product').click()
        step().locator('.defaults-note a').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Discard').click()
        page.waitForURL('**/beadle/admin/products/2')

        then:
        assertThat(page.locator('h1')).hasText('ServiceNow defaults of Payments Hub')
        ownErrors().isEmpty()
    }

    def "a change ServiceNow refuses stays on the review with the reasons, marked on the fields"() {
        given:
        api.respond('POST', '/api/changes', problem(400, 'Bad Request', '2 fields are invalid',
                [errors: [[field: 'schedule.installationStart', message: 'must be in the future'],
                          [field: 'template.planning.backoutPlan', message: 'must not mention Nexus']]]))

        when:
        open('/beadle/changes/new')
        choose(step(), 'Department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')
        button('Continue', true).click()
        select(step(), 'FixVersion').fill('CERT 4.2')
        select(step(), 'FixVersion').press('Enter')
        checkbox(step(), 'CERT-130').check()
        button('Continue', true).click()
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(review('Jira')).hasText('CERT-130 CERT-131')

        when:
        button('Raise the change in ServiceNow', true).click()

        then:
        assertThat(page.locator('.save-problem strong')).hasText('2 fields are invalid')
        assertThat(page.locator('.save-problem li'))
                .hasText(['Installation start: must be in the future', 'Backout plan: must not mention Nexus'] as String[])
        assertThat(currentStep()).hasText('Review')

        when:
        stepButton('Details').click()

        then:
        assertThat(errorOf(step(), 'Backout plan')).hasText('must not mention Nexus')

        when:
        input(step(), 'Backout plan').fill('Redeploy the previous release.')
        button('Continue', true).click()
        input(step(), 'Installation date').fill(inDays(30))

        then:
        assertThat(input(step(), 'Installation start date')).hasValue(inDays(30))
        assertThat(step().locator('.window-text')).containsText(', 18:00 to 20:00')
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    Locator step() {
        page.locator('section.step')
    }

    Locator currentStep() {
        page.locator('.step-bar .current .step-label')
    }

    Locator stepButton(String label) {
        holdingText(page.locator('.step-bar button'), label)
    }

    Locator choiceError() {
        step().locator('.choice-error')
    }

    Locator review(String label) {
        term(step(), label)
    }

    Locator term(Locator scope, String label) {
        scope.locator("dl.rows dt:text-is('${label}') + dd")
    }

    static String nextDay() {
        parse(RELEASE_DATE).plusDays(1).toString()
    }

    static String inDays(int days) {
        now().plusDays(days).toString()
    }
}
