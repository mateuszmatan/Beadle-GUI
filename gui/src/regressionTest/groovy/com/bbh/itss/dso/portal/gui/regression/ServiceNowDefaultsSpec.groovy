package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON

class ServiceNowDefaultsSpec extends EditorSpecification {

    def "an admin keeps the ServiceNow defaults of a product, privileged users included"() {
        when:
        open('/beadle/admin/products/1')

        then:
        assertThat(page.locator('.breadcrumb a, .breadcrumb span:not(.sep)'))
                .hasText(['Beadle Admin', 'Products', 'CertScanner'] as String[])
        assertThat(page.locator('h1')).hasText('ServiceNow defaults of CertScanner')
        assertThat(page.locator('.banner.info')).hasCount(0)
        assertThat(defaults().locator('.template-card h3')).hasText(['Change', 'Approvers', 'Schedule defaults',
                                                                     'Planning', 'Privileged access', 'Risk assessment'] as String[])
        hasValues(defaults(), ['Jira project'      : 'CERT', 'Assignment group': 'Technology Architecture',
                               'Affected CI'       : 'CertScanner', 'L1 manager': 'Olivia Bennett',
                               'Business approver' : 'Grace Turner', 'Installation start': '18:00',
                               'Installation hours': '2', 'Validation hours': '1', 'BBH users': '25',
                               'Platform status'   : 'Existing platform'])
        assertThat(checkbox(defaults(), 'Privileged access needed')).not().isChecked()
        assertThat(defaults().locator('.user-row')).hasCount(0)

        when:
        fillIn(defaults(), ['Assignment group': 'Certificate Services', 'Problem': 'PRB0001234',
                            'Installation hours': '3', 'First use plan': 'The security office confirms the first scan.'])
        input(defaults(), 'Change complexity').click()

        then:
        assertThat(page.locator('mat-option')).hasText(['Low', 'Medium', 'High'] as String[])

        when:
        page.locator('mat-option').filter(new Locator.FilterOptions().setHasText('Medium')).click()
        checkbox(defaults(), 'Privileged access needed').check()
        fillIn(userRow(0), ['User': 'Jane Smith', 'Privileged account': 'adm_jsmith'])
        button('Add user', true).click()
        fillIn(userRow(1), ['User': 'Tom Brown', 'Privileged account': 'adm_tbrown'])
        button('Save defaults', true).click()

        then:
        assertThat(snackBar()).containsText('The ServiceNow defaults of CertScanner are saved')
        with(awaitRequest('PUT', '/api/products/1/change-profile').json()) {
            version == 2
            template.assignmentGroup == 'Certificate Services'
            template.problem == 'PRB0001234'
            template.timing == [installationStart: '18:00', installationHours: 3, validationHours: 1]
            template.planning.firstUsePlan == 'The security office confirms the first scan.'
            template.riskAssessment.changeComplexity == 'Medium'
            template.privilegedAccess == [required: true, users: [[user: 'Jane Smith', account: 'adm_jsmith'],
                                                                  [user: 'Tom Brown', account: 'adm_tbrown']]]
        }

        when:
        button('Remove user 1', true).click()
        button('Save defaults', true).click()

        then:
        with(awaitRequest('PUT', '/api/products/1/change-profile', 2).json()) {
            version == 3
            template.privilegedAccess.users == [[user: 'Tom Brown', account: 'adm_tbrown']]
        }

        when:
        open('/beadle/admin/products/1')

        then:
        hasValues(defaults(), ['Assignment group': 'Certificate Services', 'User': 'Tom Brown',
                               'Privileged account': 'adm_tbrown', 'Change complexity': 'Medium'])
        ownErrors().isEmpty()
    }

    def "suggested defaults are checked, a value the portal refuses is marked on its field and a conflict is reported"() {
        when:
        open('/beadle/admin/products/2')

        then:
        assertThat(page.locator('h1')).hasText('ServiceNow defaults of Payments Hub')
        assertThat(page.locator('.banner.info')).containsText('Not saved yet')
        hasValues(defaults(), ['Jira project': 'PAYHUB', 'Assignment group': 'Payments Engineering',
                               'Affected CI' : 'Payments Hub', 'L1 manager': '', 'BBH users': '', 'Business impact': ''])

        when:
        input(defaults(), 'Backout plan').fill('')
        input(defaults(), 'Installation start').fill('')
        checkbox(defaults(), 'Privileged access needed').check()
        button('Save defaults', true).click()

        then:
        hasErrors(defaults(), ['Backout plan': 'Required', 'Installation start': 'Required', 'User': 'Required',
                               'Privileged account': 'Required'])
        assertThat(saveError()).hasText('Some fields need your attention.')
        api.requests('PUT', '/api/products/2/change-profile').isEmpty()

        when:
        fillIn(defaults(), ['Backout plan': 'Switch the gateway back to the previous release.', 'Installation start': '19:30',
                            'User': 'Ann Lee', 'Privileged account': 'alee'])
        saveFromAnotherTab('/beadle/admin/products/2')
        button('Save defaults', true).click()

        then:
        assertThat(saveError()).hasText('The ServiceNow defaults of Payments Hub were changed by someone else. Reload the page.')
        awaitRequest('PUT', '/api/products/2/change-profile', 2).json().version == null

        when:
        api.respond('PUT', '/api/products/2/change-profile', problem(400, 'Bad Request', '1 field is invalid',
                [errors: [[field: 'template.privilegedAccess.users[0].account', message: 'must be a privileged account']]]))
        button('Save defaults', true).click()

        then:
        assertThat(errorOf(defaults(), 'Privileged account')).hasText('must be a privileged account')
        assertThat(saveError()).hasText('The portal did not accept some values. They are marked below.')
        with(awaitRequest('PUT', '/api/products/2/change-profile', 3).json()) {
            template.timing.installationStart == '19:30'
            template.privilegedAccess.users == [[user: 'Ann Lee', account: 'alee']]
        }
        ownErrors().findAll { !it.contains('400') && !it.contains('409') }.isEmpty()
    }

    Locator defaults() {
        page.locator('section.defaults')
    }

    Locator userRow(int index) {
        defaults().locator('.user-row').nth(index)
    }

    void saveFromAnotherTab(String path) {
        Page other = context.newPage()
        other.navigate(url(path))
        other.getByRole(BUTTON, new Page.GetByRoleOptions().setName('Save defaults').setExact(true)).click()
        assertThat(other.locator('mat-snack-bar-container')).containsText('are saved')
        other.close()
    }
}
