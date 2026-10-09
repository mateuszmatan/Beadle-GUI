package com.bbh.itss.dso.portal.frontend.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.STALE
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static com.microsoft.playwright.options.AriaRole.OPTION

class ChangeTemplateAdminSpec extends EditorSpecification {

    def "an admin keeps the change template of a product section by section, privileged accounts and default change tasks included"() {
        when:
        open('/beadle/admin/products/1')

        then:
        assertThat(page.locator('.breadcrumb a, .breadcrumb span:not(.sep)'))
                .hasText(['Beadle Admin', 'Products', 'CertScanner'] as String[])
        assertThat(page.locator('h1')).hasText('CertScanner')
        assertThat(page.locator('.banner.info')).hasCount(0)
        assertThat(defaults().locator('.template-card h3')).hasText(['Generic request data', 'Jira', 'Approval and Notification',
                                                                     'Schedule', 'Planning', 'Privileged access',
                                                                     'Risk assessment', 'Secure coding'] as String[])
        assertThat(input(defaults(), 'Change number')).hasCount(0)
        hasValues(defaults(), ['Requested For'     : '', 'Department': '', 'Assignment group': 'Technology Architecture',
                               'Affected CI'       : 'CertScanner', 'Direct business service': 'Certificate Management',
                               'Risk'              : 'Moderate', 'Jira project': 'CERT', 'Business approver': 'Grace Turner',
                               'L1 approver'       : 'Olivia Bennett', 'L2 approver': 'James Carter',
                               'Installation start': '18:00', 'Installation hours': '2', 'Validation hours': '1'])
        assertThat(hintOf(defaults(), 'Requested For')).hasText('left empty: the user who opens the change')
        assertThat(hintOf(defaults(), 'Assigned to')).hasText('left empty: the user who opens the change')
        assertThat(hintOf(defaults(), 'Department')).hasText('left empty: the department of the product')
        assertThat(select(defaults(), 'Downtime')).hasText('No')
        assertThat(select(defaults(), 'How many privileged accounts')).hasText('None')
        assertThat(defaults().locator('fieldset.account')).hasCount(0)
        assertThat(select(defaults(), 'Number of BBH users impacted')).hasText('5-25')
        assertThat(select(defaults(), 'Platform status')).hasText('Existing')
        assertThat(page.locator('.default-tasks h3')).hasText('Default change tasks')
        assertThat(taskRows()).hasCount(2)
        hasValues(taskRows().nth(1), ['Short description': 'Validate CertScanner in production',
                                      'Description'      : CERT_TASKS[1].description])

        when:
        fillIn(defaults(), ['Assignment group': 'Certificate Services', 'Installation hours': '3',
                            'First use plan'  : 'The security office confirms the first scan.',
                            'Secure coding ticket number': 'SEC-1234'])
        lookUp(defaults(), 'Problem', 'tls', 'PRB0040319')
        choose(defaults(), 'Downtime', 'Yes')
        select(defaults(), 'Complexity of the change').click()

        then:
        assertThat(input(defaults(), 'Problem')).hasValue('PRB0040319')
        assertThat(page.getByRole(OPTION)).hasText(['Simple', 'Moderate', 'Very'] as String[])

        when:
        page.getByRole(OPTION, new Page.GetByRoleOptions().setName('Very').setExact(true)).click()
        choose(defaults(), 'How many privileged accounts', '2')
        fillIn(account(defaults(), 0), ['Person': 'Jane Smith', 'Privileged account': 'adm_jsmith'])
        fillIn(account(defaults(), 1), ['Person': 'Tom Brown', 'Privileged account': 'adm_tbrown'])
        button('Add a change task', true).click()
        fillIn(taskRows().nth(2), ['Short description': 'Run the database scripts',
                                   'Description'      : 'Run the Liquibase changesets of CertScanner.'])

        then:
        assertThat(input(defaults(), 'Risk')).hasValue('High')
        assertThat(account(defaults(), 1).locator('legend')).hasText('Privileged account 2')

        when:
        button('Save the template', true).click()

        then:
        assertThat(snackBar()).containsText('The change template of CertScanner is saved')
        with(awaitRequest('PUT', '/api/products/1/change-profile').json()) {
            version == 2
            template.assignmentGroup == 'Certificate Services'
            template.problem == 'PRB0040319'
            template.requestedFor == null
            template.downtime
            template.risk == null
            template.timing == [installationStart: '18:00', installationHours: 3, validationHours: 1]
            template.planning.firstUsePlan == 'The security office confirms the first scan.'
            template.riskAssessment.changeComplexity == 'Very'
            template.privilegedAccess == [required: true, users: [[user: 'Jane Smith', account: 'adm_jsmith'],
                                                                  [user: 'Tom Brown', account: 'adm_tbrown']]]
            template.secureCodingTicket == 'SEC-1234'
            tasks == CERT_TASKS + [[shortDescription: 'Run the database scripts',
                                    description     : 'Run the Liquibase changesets of CertScanner.']]
        }

        when:
        choose(defaults(), 'How many privileged accounts', '1')
        button('Remove change task 1', true).click()
        button('Save the template', true).click()

        then:
        with(awaitRequest('PUT', '/api/products/1/change-profile', 2).json()) {
            version == 3
            template.privilegedAccess.users == [[user: 'Jane Smith', account: 'adm_jsmith']]
            tasks*.shortDescription == ['Validate CertScanner in production', 'Run the database scripts']
        }

        when:
        open('/beadle/admin/products/1')

        then:
        hasValues(defaults(), ['Assignment group': 'Certificate Services', 'Person': 'Jane Smith',
                               'Privileged account': 'adm_jsmith', 'Risk': 'High'])
        assertThat(select(defaults(), 'How many privileged accounts')).hasText('1')
        assertThat(select(defaults(), 'Complexity of the change')).hasText('Very')
        assertThat(select(defaults(), 'Downtime')).hasText('Yes')
        assertThat(taskRows()).hasCount(2)
        hasValues(taskRows().nth(0), ['Short description': 'Validate CertScanner in production'])
        ownErrors().isEmpty()
    }

    def "a suggested change template is checked, a value the portal refuses is marked on its field and a conflict is reloaded"() {
        when:
        open('/beadle/admin/products/2')

        then:
        assertThat(page.locator('h1')).hasText('Payments Hub')
        assertThat(page.locator('.banner.info')).containsText('Not saved yet')
        hasValues(defaults(), ['Jira project': 'PAYHUB', 'Assignment group': 'Payments Engineering',
                               'Affected CI' : 'Payments Hub', 'L1 approver': '', 'Risk': 'Low'])
        assertThat(select(defaults(), 'Number of BBH users impacted')).hasText('Less than 5')
        assertThat(select(defaults(), 'Business impact')).hasText('None')
        hasValues(taskRows().nth(0), ['Short description': 'Deploy Payments Hub to production'])

        when:
        input(defaults(), 'Backout plan').fill('')
        input(defaults(), 'Installation start').fill('')
        choose(defaults(), 'How many privileged accounts', '1')
        input(taskRows().nth(1), 'Short description').fill(' ')
        button('Save the template', true).click()

        then:
        hasErrors(defaults(), ['Backout plan': 'Required', 'Installation start': 'Required', 'Person': 'Required',
                               'Privileged account': 'Required'])
        assertThat(errorOf(taskRows().nth(1), 'Short description')).hasText('Required')
        assertThat(saveError()).hasText('Some fields need your attention.')
        api.requests('PUT', '/api/products/2/change-profile').isEmpty()

        when:
        fillIn(defaults(), ['Backout plan': 'Switch the gateway back to the previous release.', 'Installation start': '19:30'])
        lookUp(defaults(), 'Person', 'ann', 'Ann Lee')
        input(defaults(), 'Privileged account').fill('alee')
        input(taskRows().nth(1), 'Short description').fill('Validate the gateway')
        saveFromAnotherTab('/beadle/admin/products/2')
        button('Save the template', true).click()

        then:
        assertThat(saveError()).hasText(STALE)
        awaitRequest('PUT', '/api/products/2/change-profile', 2).json().version == null

        when:
        button('Reload', true).click()

        then:
        assertThat(saveError()).hasCount(0)
        assertThat(page.locator('.banner.info')).hasCount(0)
        hasValues(defaults(), ['Installation start': '18:00'])
        assertThat(select(defaults(), 'How many privileged accounts')).hasText('None')

        when:
        fillIn(defaults(), ['Backout plan': 'Switch the gateway back to the previous release.', 'Installation start': '19:30'])
        choose(defaults(), 'How many privileged accounts', '1')
        lookUp(defaults(), 'Person', 'ann', 'Ann Lee')
        input(defaults(), 'Privileged account').fill('alee')
        input(taskRows().nth(1), 'Short description').fill('Validate the gateway')
        api.respond('PUT', '/api/products/2/change-profile', problem(400, 'Bad Request', '1 field is invalid',
                [errors: [[field: 'template.privilegedAccess.users[0].account', message: 'must be a privileged account']]]))
        button('Save the template', true).click()

        then:
        assertThat(errorOf(defaults(), 'Privileged account')).hasText('must be a privileged account')
        assertThat(saveError()).hasText('The portal did not accept some values. They are marked below.')
        with(awaitRequest('PUT', '/api/products/2/change-profile', 3).json()) {
            version == 0
            template.timing.installationStart == '19:30'
            template.privilegedAccess.users == [[user: 'Ann Lee', account: 'alee']]
            tasks*.shortDescription == ['Deploy Payments Hub to production', 'Validate the gateway']
        }
        ownErrors().findAll { !it.contains('400') && !it.contains('409') }.isEmpty()
    }

    Locator defaults() {
        page.locator('section.defaults')
    }

    Locator taskRows() {
        page.locator('.default-tasks .task-row')
    }

    void saveFromAnotherTab(String path) {
        Page other = context.newPage()
        other.navigate(url(path))
        other.getByRole(BUTTON, new Page.GetByRoleOptions().setName('Save the template').setExact(true)).click()
        assertThat(other.locator('mat-snack-bar-container')).containsText('is saved')
        other.close()
    }
}
