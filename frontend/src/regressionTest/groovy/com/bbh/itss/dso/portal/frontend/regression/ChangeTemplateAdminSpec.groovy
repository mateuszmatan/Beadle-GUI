package com.bbh.itss.dso.portal.frontend.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page

import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.STALE
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.details
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON

class ChangeTemplateAdminSpec extends EditorSpecification {

    def "an admin keeps the change template of a product section by section, privileged accounts and default change tasks included"() {
        when:
        open('/beadle/admin/products/1')

        then:
        assertThat(page.locator('.breadcrumb a, .breadcrumb span:not(.sep)'))
                .hasText(['Beadle Admin', 'Products', 'CertScanner'] as String[])
        assertThat(page.locator('h1')).hasText('CertScanner')
        assertThat(page.locator('.banner.info')).hasCount(0)
        assertThat(defaults().locator('.template-card h3')).hasText(['Request details', 'Jira', 'Approval and notification',
                                                                     'Schedule', 'Planning', 'Privileged access',
                                                                     'Risk assessment', 'Secure coding'] as String[])
        assertThat(input(defaults(), 'Change number')).hasCount(0)
        hasValues(defaults(), ['Requested for'     : '', 'Department': '', 'Assignment group': 'Technology Architecture',
                               'Affected CI'       : 'CertScanner', 'Direct business service': 'Certificate Management',
                               'Risk'              : 'Moderate', 'Jira project': 'CERT', 'Business approver': 'Grace Turner',
                               'L1 approver'       : 'Olivia Bennett', 'L2 approver': 'James Carter',
                               'Installation start': '18:00', 'Installation hours': '2', 'Validation hours': '1'])
        assertThat(hintOf(defaults(), 'Requested for')).hasText('If left empty: the user who opens the change')
        assertThat(hintOf(defaults(), 'Assigned to')).hasText('If left empty: the user who opens the change')
        assertThat(hintOf(defaults(), 'Department')).hasText('If left empty: the department of the product')
        assertThat(selected(defaults(), 'Downtime')).hasText('No')
        assertThat(selected(defaults(), 'How many privileged accounts')).hasText('None')
        assertThat(defaults().locator('fieldset.account')).hasCount(0)
        assertThat(selected(defaults(), 'Number of BBH users impacted')).hasText('5-25')
        assertThat(selected(defaults(), 'Platform status')).hasText('Existing')
        assertThat(page.locator('.default-tasks h3')).hasText('Default change tasks')
        assertThat(taskRows().locator('.kind')).hasText(['Release Management', 'Change task'] as String[])
        assertThat(taskRows().locator('.chip')).hasCount(0)
        assertThat(taskRows().nth(0).locator('dso-label')).hasText((RELEASE_TASK_FIELDS - CHANGE_ONLY_FIELDS) as String[])
        assertThat(taskRows().nth(1).locator('dso-label')).hasText((OTHER_TASK_FIELDS - CHANGE_ONLY_FIELDS) as String[])
        hasValues(taskRows().nth(0), ['Assignment group': 'Release Management', 'Affected CI': '', 'Application': 'CertScanner'])
        assertThat(selected(taskRows().nth(0), 'Platform')).hasText('None')
        hasValues(taskRows().nth(1), ['Assignment group' : 'Technology Architecture',
                                      'Short description': 'Validate CertScanner in production',
                                      'Description'      : CERT_TASKS[1].description])
        assertThat(selected(taskRows().nth(1), 'Importance')).hasText('3 - Moderate')

        when:
        fillIn(defaults(), ['Assignment group': 'Certificate Services', 'Installation hours': '3',
                            'First use plan'  : 'The security office confirms the first scan.',
                            'Secure coding ticket number': 'SEC-1234'])
        lookUp(defaults(), 'Problem', 'tls', 'PRB0040319')
        choose(defaults(), 'Downtime', 'Yes')
        select(defaults(), 'Complexity of the change').click()

        then:
        assertThat(input(defaults(), 'Problem')).hasValue('PRB0040319')
        assertThat(select(defaults(), 'Complexity of the change').locator('option')).hasText(['Simple', 'Moderate', 'Very'] as String[])

        when:
        choose(defaults(), 'Complexity of the change', 'Very')
        choose(defaults(), 'How many privileged accounts', '2')
        fillIn(account(defaults(), 0), ['Person': 'Jane Smith', 'Privileged account': 'adm_jsmith'])
        fillIn(account(defaults(), 1), ['Person': 'Tom Brown', 'Privileged account': 'adm_tbrown'])
        button('Add a change task', true).click()
        input(taskRows().nth(2), 'Assignment group').fill('release management')

        then:
        assertThat(input(defaults(), 'Risk')).hasValue('High')
        assertThat(account(defaults(), 1).locator('legend')).hasText('Privileged account 2')
        assertThat(taskRows().nth(2).locator('.kind')).hasText('Release Management')
        assertThat(taskRows().nth(2).locator('dso-label')).hasText((RELEASE_TASK_FIELDS - CHANGE_ONLY_FIELDS) as String[])

        when:
        lookUp(taskRows().nth(2), 'Assignment group', 'data', 'Data Movement - API')
        choose(taskRows().nth(2), 'Importance', '2 - High')
        fillIn(taskRows().nth(2), ['Short description': 'Run the database scripts',
                                   'Description'      : 'Run the Liquibase changesets of CertScanner.'])
        choose(taskRows().nth(0), 'Platform', 'OpenShift')

        then:
        assertThat(taskRows().nth(2).locator('.kind')).hasText('Change task')
        assertThat(taskRows().nth(2).locator('dso-label')).hasText((OTHER_TASK_FIELDS - CHANGE_ONLY_FIELDS) as String[])
        assertThat(input(taskRows().nth(0), 'Application')).hasValue('OCP')
        assertThat(input(taskRows().nth(0), 'Application')).isDisabled()

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
            tasks == [CERT_TASKS[0] + [platform: 'OpenShift', application: 'OCP'], CERT_TASKS[1],
                      details('Data Movement - API', 'Run the database scripts', 'Run the Liquibase changesets of CertScanner.',
                              [importance: '2 - High'])]
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
        assertThat(selected(defaults(), 'How many privileged accounts')).hasText('1')
        assertThat(selected(defaults(), 'Complexity of the change')).hasText('Very')
        assertThat(selected(defaults(), 'Downtime')).hasText('Yes')
        assertThat(taskRows().locator('.kind')).hasText(['Change task', 'Change task'] as String[])
        hasValues(taskRows().nth(0), ['Short description': 'Validate CertScanner in production'])
        hasValues(taskRows().nth(1), ['Assignment group': 'Data Movement - API'])
        assertThat(selected(taskRows().nth(1), 'Importance')).hasText('2 - High')

        when:
        button('Remove change task 2', true).click()

        then:
        assertThat(taskRows()).hasCount(1)
        assertThat(button('Remove change task 1', true)).isDisabled()
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
        assertThat(selected(defaults(), 'Number of BBH users impacted')).hasText('Less than 5')
        assertThat(selected(defaults(), 'Business impact')).hasText('None')
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
        assertThat(selected(defaults(), 'How many privileged accounts')).hasText('None')

        when:
        fillIn(defaults(), ['Backout plan': 'Switch the gateway back to the previous release.', 'Installation start': '19:30'])
        choose(defaults(), 'How many privileged accounts', '1')
        lookUp(defaults(), 'Person', 'ann', 'Ann Lee')
        input(defaults(), 'Privileged account').fill('alee')
        input(taskRows().nth(1), 'Short description').fill('Validate the gateway')
        api.respond('PUT', '/api/products/2/change-profile', problem(400, 'Bad Request', '2 fields are invalid',
                [errors: [[field: 'template.privilegedAccess.users[0].account', message: 'must be a privileged account'],
                          [field: 'tasks[1].assignedTo', message: 'is not a ProTech user']]]))
        button('Save the template', true).click()

        then:
        assertThat(errorOf(defaults(), 'Privileged account')).hasText('must be a privileged account')
        assertThat(errorOf(taskRows().nth(1), 'Assigned to')).hasText('is not a ProTech user')
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
        page.locator('section.defaults dso-change-template-form')
    }

    void saveFromAnotherTab(String path) {
        Page other = context.newPage()
        other.navigate(url(path))
        other.getByRole(BUTTON, new Page.GetByRoleOptions().setName('Save the template').setExact(true)).click()
        assertThat(other.locator('dso-toast')).containsText('is saved')
        other.close()
    }
}
