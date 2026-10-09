package com.bbh.itss.dso.portal.frontend.regression

import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.OPTIONS
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.PLANNING
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.RELEASE_DATE
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.details
import static com.bbh.itss.dso.portal.frontend.support.StubApi.SIGNED_IN_USER
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.OPTION
import static java.time.LocalDate.now
import static java.time.LocalDate.parse

class NewChangeSpec extends EditorSpecification {

    static final List<String> STEPS = ['Request data', 'Jira', 'Approval', 'Schedule', 'Planning', 'Privileged access',
                                       'Risk assessment', 'Secure coding', 'Review', 'Change tasks', 'Raised']

    def "a release manager raises a ProTech change section by section, with the lookups, the downtime window and the risk lists, then creates its change tasks"() {
        when:
        open('/beadle')
        menuLink('New Change').click()
        page.waitForURL('**/beadle/new-change')

        then:
        assertThat(page.locator('h1')).hasText('New ProTech Change')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('Raise a ProTech change (CHG) with its change tasks (CTASK), written from Jira')
        assertThat(page.locator('dso-integration-note')).containsText('Jira is not connected yet')
        assertThat(page.locator('dso-integration-note')).containsText('ProTech is not connected yet')

        when:
        button('Continue', true).click()

        then:
        assertThat(page.locator('.step-bar .step-label')).hasText(STEPS as String[])
        assertThat(step().locator('h2')).hasText('Generic request data')
        assertThat(choiceError()).hasText('Choose the product')

        when:
        choose(step(), 'Your department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')

        then:
        hasValues(step(), ['Change number'   : '', 'Approval': 'Not Yet Requested', 'Opened By': SIGNED_IN_USER, 'State': 'Draft',
                           'Requested For'   : SIGNED_IN_USER, 'Requested By': SIGNED_IN_USER, 'Assigned to': SIGNED_IN_USER,
                           'Department'      : 'Corporate Technology', 'Assignment group': 'Technology Architecture',
                           'Release'         : '', 'Affected CI': 'CertScanner', 'Direct business service': 'Certificate Management',
                           'Risk'            : 'Moderate', 'Affected clients': ''])
        assertThat(input(step(), 'Change number')).hasAttribute('placeholder', 'Given by ProTech when raised')
        assertThat(input(step(), 'Opened By')).not().isEditable()
        assertThat(input(step(), 'Direct business service')).not().isEditable()
        assertThat(selected(step(), 'Category')).hasText('Application')
        assertThat(selected(step(), 'Type')).hasText('Standard')
        assertThat(step().locator('.defaults-note')).hasCount(0)

        when:
        input(step(), 'Affected CI').fill('Cert')

        then:
        assertThat(input(step(), 'Direct business service')).hasValue('')

        when:
        lookUp(step(), 'Affected CI', 'cert', 'CertScanner')
        buttonIn(step(), 'Find Affected clients').click()

        then:
        assertThat(input(step(), 'Direct business service')).hasValue('Certificate Management')
        assertThat(dialog().locator('h2')).hasText('Find Affected clients')
        assertThat(found()).hasText(['Alder Ridge Pension Fund', 'Aurora Global Equity Fund', 'Bluewater Insurance Group',
                                     'Kestrel Sovereign Wealth Fund', 'Northwind Family Office'] as String[])

        when:
        input(dialog(), 'Search').fill('fund')

        then:
        assertThat(found()).hasText(['Alder Ridge Pension Fund', 'Aurora Global Equity Fund', 'Kestrel Sovereign Wealth Fund'] as String[])
        awaitRequest('GET', '/api/lookups/clients', 2).params() == [q: 'fund']

        when:
        holding(dialog().locator('.results button'), ".value:text-is('Aurora Global Equity Fund')").click()
        buttonIn(step(), 'Find Affected clients').click()
        input(dialog(), 'Search').fill('kestrel')

        then:
        assertThat(found()).hasText(['Kestrel Sovereign Wealth Fund'] as String[])

        when:
        input(dialog(), 'Search').press('Enter')
        lookUp(step(), 'Incident', 'expiry', 'INC0105126')

        then:
        assertThat(input(step(), 'Affected clients')).hasValue('Aurora Global Equity Fund, Kestrel Sovereign Wealth Fund')
        assertThat(input(step(), 'Incident')).hasValue('INC0105126')

        when:
        button('Continue', true).click()
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Jira')
        assertThat(choiceError()).hasText('Enter the FixVersion of the release')
        assertThat(input(step(), 'Jira project')).hasValue('CERT')
        assertThat(input(step(), 'Jira project')).not().isEditable()

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
        awaitRequest('GET', '/api/products/1/jira/versions').params() == [:]

        when:
        checkbox(step(), 'CERT-120').check()

        then:
        assertThat(step().locator('.story-group h4')).hasText('CERT-120 Expiry alerts for certificates')
        assertThat(checkbox(step(), 'CERT-121')).isChecked()
        assertThat(checkbox(step(), 'CERT-122')).isChecked()
        awaitRequest('GET', '/api/products/1/jira/stories').params() == [fixVersion: 'CERT 4.2', epics: 'CERT-120']
        assertThat(step().locator('h3:has-text("Short description and description")')).isVisible()
        assertThat(input(texts(), 'Short description')).hasValue('CertScanner CERT 4.2: Expiry alerts for certificates')
        assertThat(input(texts(), 'Description')).hasValue(
                ~/Stories:\nCERT-121 E-mail the certificate owner\nCERT-122 Teams alert on expiry$/)

        when:
        checkbox(step(), 'CERT-122').uncheck()

        then:
        assertThat(input(texts(), 'Description')).hasValue(~/Stories:\nCERT-121 E-mail the certificate owner$/)

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Approval')
        assertThat(step().locator('h2')).hasText('Approval and Notification')
        hasValues(step(), ['Business approver': 'Grace Turner', 'L1 approver': 'Olivia Bennett', 'L2 approver': 'James Carter'])

        when:
        lookUp(step(), 'L2 approver', 'hay', 'William Hayes')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Schedule')
        hasValues(step(), ['Installation start'           : "${RELEASE_DATE}T18:00", 'Installation hours': '2',
                           'Post-install validation start': "${RELEASE_DATE}T20:00", 'Validation hours': '1',
                           'First use'                    : "${RELEASE_DATE}T21:00"])
        assertThat(hintOf(step(), 'Installation hours')).hasText(~/^until .+, 20:00$/)
        assertThat(selected(step(), 'Downtime')).hasText('No')
        assertThat(input(step(), 'Downtime start')).hasCount(0)
        assertThat(step().locator('dso-change-schedule .note')).hasText('Times are in your time zone, UTC.')

        when:
        choose(step(), 'Downtime', 'Yes')

        then:
        hasValues(step(), ['Downtime start': "${RELEASE_DATE}T18:00", 'Downtime hours': '2'])

        when:
        fillIn(step(), ['Downtime hours': '1.5', 'First use': "${nextDay()}T08:00"])

        then:
        assertThat(hintOf(step(), 'Downtime hours')).hasText(~/^until .+, 19:30$/)

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Planning')
        hasValues(step(), ['Test summary': PLANNING.testSummary, 'Backout plan': PLANNING.backoutPlan])

        when:
        input(step(), 'Backout plan').fill('')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Planning')
        assertThat(errorOf(step(), 'Backout plan')).hasText('Required')
        assertThat(choiceError()).hasText('Some fields need your attention.')

        when:
        input(step(), 'Backout plan').fill('Redeploy CERT 4.1 from Nexus.')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Privileged access')
        assertThat(selected(step(), 'How many privileged accounts')).hasText('None')
        assertThat(step().locator('fieldset.account')).hasCount(0)

        when:
        choose(step(), 'How many privileged accounts', '1')
        button('Continue', true).click()

        then:
        assertThat(account(step(), 0).locator('legend')).hasText('Privileged account 1')
        hasErrors(account(step(), 0), ['Person': 'Required', 'Privileged account': 'Required'])

        when:
        lookUp(account(step(), 0), 'Person', 'jane', 'Jane Smith')
        input(account(step(), 0), 'Privileged account').fill('adm_jsmith')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Risk assessment')
        assertThat(selected(step(), 'Number of BBH users impacted')).hasText('5-25')
        assertThat(selected(step(), 'Business impact')).hasText('Low')

        when:
        select(step(), 'Business impact').click()

        then:
        assertThat(select(step(), 'Business impact').locator('option')).hasText(['None', 'Low', 'Medium', 'High'] as String[])

        when:
        choose(step(), 'Business impact', 'High')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Secure coding')

        when:
        input(step(), 'Secure coding ticket number').fill('SEC-4711')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(input(texts(), 'Short description')).hasValue('CertScanner CERT 4.2: Expiry alerts for certificates')
        assertThat(input(texts(), 'Description')).hasValue(
                'Production release of CertScanner (CERTSCANNER), FixVersion CERT 4.2.\n\nEpics:\n'
                        + 'CERT-120 Expiry alerts for certificates (In Review)\n\nStories:\nCERT-121 E-mail the certificate owner')
        assertThat(step().locator('.lead')).containsText('Its change tasks follow once ProTech has given the change its number.')
        assertThat(taskRows()).hasCount(0)
        assertThat(step().locator('dso-change-summary h3')).hasText(['Generic request data', 'Jira', 'Schedule',
                                                                    'Approval and Notification', 'Risk assessment',
                                                                    'Privileged access', 'Secure coding', 'Planning'] as String[])
        assertThat(review('Change number')).hasText('Given by ProTech when raised')
        assertThat(review('Approval')).hasText('Not Yet Requested')
        assertThat(review('Opened By')).hasText(SIGNED_IN_USER)
        assertThat(review('Release')).hasText('CERT 4.2')
        assertThat(review('Incident')).hasText('INC0105126')
        assertThat(review('Affected clients')).hasText('Aurora Global Equity Fund, Kestrel Sovereign Wealth Fund')
        assertThat(review('Risk')).hasText('High')
        assertThat(review('L2 approver')).hasText('William Hayes')
        assertThat(review('Business impact')).hasText('High')
        assertThat(review('Jane Smith')).hasText('adm_jsmith')
        assertThat(review('Downtime')).hasText(~/, 18:00 to 19:30$/)
        assertThat(review('Secure coding ticket number')).hasText('SEC-4711')
        assertThat(step().locator('.scope')).hasText('1 epic and 1 story of FixVersion CERT 4.2')
        with(reviewed()) {
            fixVersion == 'CERT 4.2'
            schedule == [installationStart: "${RELEASE_DATE}T18:00:00.000Z".toString(),
                         installationEnd  : "${RELEASE_DATE}T20:00:00.000Z".toString(),
                         validationStart  : "${RELEASE_DATE}T20:00:00.000Z".toString(),
                         validationEnd    : "${RELEASE_DATE}T21:00:00.000Z".toString(),
                         firstUsage       : "${nextDay()}T08:00:00.000Z".toString(),
                         downtimeStart    : "${RELEASE_DATE}T18:00:00.000Z".toString(),
                         downtimeEnd      : "${RELEASE_DATE}T19:30:00.000Z".toString()]
            template.requestedFor == SIGNED_IN_USER
            template.department == 'Corporate Technology'
            template.release == 'CERT 4.2'
            template.configurationItem == 'CertScanner'
            template.directBusinessService == 'Certificate Management'
            template.incident == 'INC0105126'
            template.affectedClients == 'Aurora Global Equity Fund, Kestrel Sovereign Wealth Fund'
            template.risk == null
            template.approvers == [businessApprover: 'Grace Turner', l1Manager: 'Olivia Bennett', l2Manager: 'William Hayes']
            template.downtime
            template.planning.backoutPlan == 'Redeploy CERT 4.1 from Nexus.'
            template.privilegedAccess == [required: true, users: [[user: 'Jane Smith', account: 'adm_jsmith']]]
            template.riskAssessment.businessImpact == 'High'
            template.secureCodingTicket == 'SEC-4711'
            !containsKey('tasks')
        }

        when:
        input(texts(), 'Short description').fill('CertScanner 4.2 release')
        button('Raise the change in ProTech', true).click()

        then:
        assertThat(currentStep()).hasText('Change tasks')
        assertThat(step().locator('h2')).hasText('Change tasks of CHG0031002')
        assertThat(stepButton('Review')).isDisabled()
        assertThat(button('Back', true)).hasCount(0)
        assertThat(taskRows().locator('.kind')).hasText(['Release Management', 'Change task'] as String[])
        assertThat(taskRows().locator('.chip')).hasText(['Open', 'Open'] as String[])
        assertThat(taskRows().nth(0).locator('mat-label')).hasText(RELEASE_TASK_FIELDS as String[])
        assertThat(taskRows().nth(1).locator('mat-label')).hasText(OTHER_TASK_FIELDS as String[])
        hasValues(taskRows().nth(0), ['Number'            : '', 'Change number': 'CHG0031002', 'Assignment group': 'Release Management',
                                      'Affected CI'       : 'CertScanner', 'Approval': 'Not Yet Requested',
                                      'Installation start': "${RELEASE_DATE}T18:00", 'Installation end': "${RELEASE_DATE}T20:00",
                                      'Task start'        : "${RELEASE_DATE}T18:01", 'Application': 'CertScanner',
                                      'Short description' : CERT_TASKS[0].shortDescription])
        assertThat(input(taskRows().nth(0), 'Number')).hasAttribute('placeholder', 'Given by ProTech when created')
        assertThat(input(taskRows().nth(0), 'Number')).isDisabled()
        assertThat(input(taskRows().nth(0), 'Installation start')).isDisabled()
        assertThat(select(taskRows().nth(0), 'Platform')).hasText('None')
        assertThat(hintOf(taskRows().nth(0), 'Application')).hasText('OCP on OpenShift')
        hasValues(taskRows().nth(1), ['Assignment group': 'Technology Architecture', 'Affected CI': 'CertScanner',
                                      'Description'     : CERT_TASKS[1].description])
        assertThat(select(taskRows().nth(1), 'Importance')).hasText('3 - Moderate')
        with(awaitRequest('POST', '/api/changes').json()) {
            productId == 1
            !containsKey('tasks')
            fixVersion == 'CERT 4.2'
            epicKeys == ['CERT-120']
            storyKeys == ['CERT-121']
            shortDescription == 'CertScanner 4.2 release'
            !description.contains('Change tasks')
            schedule.downtimeEnd == "${RELEASE_DATE}T19:30:00.000Z"
            template.secureCodingTicket == 'SEC-4711'
        }

        when:
        choose(taskRows().nth(0), 'Platform', 'OpenShift')
        input(taskRows().nth(0), 'Task start').fill("${RELEASE_DATE}T18:00")
        button('Add a change task', true).click()
        lookUp(taskRows().nth(2), 'Assignment group', 'cloud', 'Cloud Engineering')
        lookUp(taskRows().nth(2), 'Assigned to', 'jane', 'Jane Smith')
        choose(taskRows().nth(2), 'Importance', '2 - High')
        input(taskRows().nth(2), 'Short description').fill('Run the database scripts')
        button('Remove change task 2', true).click()
        button('Create the change tasks in ProTech', true).click()

        then:
        assertThat(currentStep()).hasText('Change tasks')
        assertThat(input(taskRows().nth(0), 'Application')).hasValue('OCP')
        assertThat(input(taskRows().nth(0), 'Application')).isDisabled()
        assertThat(errorOf(taskRows().nth(0), 'Task start')).hasText('At least a minute after the installation start')
        assertThat(taskRows().nth(1).locator('mat-label')).hasText(OTHER_TASK_FIELDS as String[])
        hasValues(taskRows().nth(1), ['Number': '', 'Change number': 'CHG0031002', 'Approval': 'Not Yet Requested', 'Affected CI': ''])
        assertThat(hintOf(taskRows().nth(1), 'Affected CI')).hasText('left empty: the Affected CI of the change')
        assertThat(errorOf(taskRows().nth(1), 'Description')).hasText('Required')
        assertThat(choiceError()).hasText('Check the change tasks')
        api.requests('POST', '/api/changes/5/tasks').isEmpty()

        when:
        input(taskRows().nth(0), 'Task start').fill("${RELEASE_DATE}T20:01")

        then:
        assertThat(errorOf(taskRows().nth(0), 'Task start')).hasText('Not after the installation end')

        when:
        input(taskRows().nth(0), 'Task start').fill('')

        then:
        assertThat(errorOf(taskRows().nth(0), 'Task start')).hasText('Choose when the task starts')

        when:
        input(taskRows().nth(0), 'Task start').fill("${RELEASE_DATE}T18:30")
        input(taskRows().nth(1), 'Description').fill('Run the Liquibase changesets of CertScanner.')
        button('Create the change tasks in ProTech', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CHG0031002 is raised')
        assertThat(currentStep()).hasText('Raised')
        assertThat(step().locator('.review-list li')).hasText(['CTASK0310021 · Deploy CertScanner to production · Release Management',
                                                               'CTASK0310022 · Run the database scripts · Cloud Engineering'] as String[])
        assertThat(step().locator('.next-steps')).containsText('Grace Turner, Olivia Bennett, William Hayes approve the change in ProTech')
        assertThat(step().locator('.next-steps a')).hasText(['CHG0031002', 'Changes'] as String[])
        assertThat(step().locator('.next-steps a').first()).hasAttribute('href', '/beadle/changes/5')
        with(awaitRequest('POST', '/api/changes/5/tasks').json()) {
            version == 0
            departmentId == 3
            tasks == [[number : null, start: "${RELEASE_DATE}T18:30:00.000Z".toString(),
                       details: CERT_TASKS[0] + [configurationItem: 'CertScanner', platform: 'OpenShift', application: 'OCP']],
                      [number : null, start: null,
                       details: details('Cloud Engineering', 'Run the database scripts', 'Run the Liquibase changesets of CertScanner.',
                               [assignedTo: 'Jane Smith', importance: '2 - High'])]]
        }

        when:
        link('Open the change', true).click()

        then:
        page.waitForURL('**/beadle/changes/5')
        assertThat(page.locator('h1')).hasText('CHG0031002')
        assertThat(page.locator('.tasks li strong')).hasText([CERT_TASKS[0].shortDescription, 'Run the database scripts'] as String[])
        assertThat(page.locator('.tasks li').first()).containsText('CTASK0310021')
        assertThat(page.locator('.tasks li').first()).containsText('Not Yet Requested')
        assertThat(page.locator('.tasks .facts').first()).hasText(~/^Release Management · CertScanner · OpenShift · OCP · starts .+, 18:30$/)
        assertThat(page.locator('.tasks .facts').last()).hasText('Cloud Engineering · Jane Smith · CertScanner · 2 - High')
        assertThat(page.locator('dso-workflow-progress li[aria-current=step]')).containsText('Draft')
        assertThat(page.locator('.note.sync')).hasText('Read from ProTech just now')
        assertThat(term(summary(), 'Change number')).hasText('CHG0031002')
        assertThat(term(summary(), 'Opened By')).hasText(SIGNED_IN_USER)
        assertThat(term(summary(), 'Risk')).hasText('High')
        assertThat(term(summary(), 'Jane Smith')).hasText('adm_jsmith')
        assertThat(term(summary(), 'FixVersion')).hasText('CERT 4.2')
        assertThat(term(summary(), 'Downtime')).hasText(~/, 18:00 to 19:30$/)
        ownErrors().isEmpty()
    }

    def "a product without a change template raises a change with the suggested values"() {
        when:
        open('/beadle/changes/new')
        page.waitForURL('**/beadle/new-change')
        choose(step(), 'Your department', 'Fund Services')
        choose(step(), 'Product', 'Payments Hub (PAYHUB)')

        then:
        assertThat(step().locator('.defaults-note')).containsText(
                'Payments Hub has no change template yet, so the suggested values are filled in. An admin can set it in Beadle Admin.')
        assertThat(link('Set the template', true)).isVisible()
        assertThat(step().locator('.defaults-note a')).hasAttribute('href', '/beadle/admin/products/2')
        hasValues(step(), ['Requested For'          : SIGNED_IN_USER, 'Department': 'Fund Services',
                           'Assignment group'       : 'Payments Engineering', 'Affected CI': 'Payments Hub',
                           'Direct business service': '', 'Risk': 'Low'])

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Jira')
        assertThat(input(step(), 'Jira project')).hasValue('PAYHUB')

        when:
        select(step(), 'FixVersion').fill('PAYHUB 4.3')
        select(step(), 'FixVersion').press('Escape')
        button('Find epics', true).click()

        then:
        assertThat(step().locator('.issues .key')).hasText(['PAYHUB-130'] as String[])
        awaitRequest('GET', '/api/products/2/jira/epics').params() == [fixVersion: 'PAYHUB 4.3']

        when:
        checkbox(step(), 'PAYHUB-130').check()

        then:
        assertThat(checkbox(step(), 'PAYHUB-132')).isChecked()

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Approval')
        hasValues(step(), ['Business approver': '', 'L1 approver': '', 'L2 approver': ''])

        when:
        input(step(), 'L1 approver').fill('Emma Brooks')
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Schedule')
        hasValues(step(), ['Installation start': "${inDays(1)}T18:00", 'Post-install validation start': "${inDays(1)}T20:00",
                           'First use'         : "${inDays(1)}T21:00"])

        when:
        input(step(), 'Installation start').fill("${inDays(3)}T18:00")

        then:
        hasValues(step(), ['Post-install validation start': "${inDays(3)}T20:00", 'First use': "${inDays(3)}T21:00"])
        assertThat(hintOf(step(), 'Installation hours')).hasText(~/^until .+, 20:00$/)

        when:
        continueTo('Review')

        then:
        assertThat(review('Risk')).hasText('Low')
        assertThat(review('L1 approver')).hasText('Emma Brooks')
        with(reviewed()) {
            productId == 2
            !containsKey('tasks')
            epicKeys == ['PAYHUB-130']
            storyKeys == ['PAYHUB-132']
            template.jiraProjectKey == 'PAYHUB'
            template.requestedFor == SIGNED_IN_USER
            template.department == 'Fund Services'
            template.approvers == [businessApprover: null, l1Manager: 'Emma Brooks', l2Manager: null]
            template.riskAssessment == (OPTIONS.risk as Map<String, List>).collectEntries { question, answers ->
                [question, answers.first()]
            }
        }

        when:
        stepButton('Request data').click()
        step().locator('.defaults-note a').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Discard your changes?')

        when:
        dialogButton('Discard').click()
        page.waitForURL('**/beadle/admin/products/2')

        then:
        assertThat(page.locator('h1')).hasText('Payments Hub')
        ownErrors().isEmpty()
    }

    def "a change ProTech refuses stays on the review with the reasons, marked on the fields of each step"() {
        given:
        api.respond('POST', '/api/changes', problem(400, 'Bad Request', '3 fields are invalid',
                [errors: [[field: 'schedule.installationEnd', message: 'must be after the start'],
                          [field: 'template.planning.backoutPlan', message: 'must not mention Nexus'],
                          [field: 'template.secureCodingTicket', message: 'is not a Jira issue']]]))

        when:
        reviewCertScanner()

        then:
        assertThat(review('Jira')).hasText('CERT-130 CERT-131')

        when:
        button('Raise the change in ProTech', true).click()

        then:
        assertThat(page.locator('.save-problem strong')).hasText('3 fields are invalid')
        assertThat(page.locator('.save-problem li'))
                .hasText(['Installation end: must be after the start', 'Backout plan: must not mention Nexus',
                          'Secure coding ticket number: is not a Jira issue'] as String[])
        assertThat(currentStep()).hasText('Review')
        api.requests('POST', '/api/changes/\\d+/tasks').isEmpty()

        when:
        stepButton('Secure coding').click()

        then:
        assertThat(errorOf(step(), 'Secure coding ticket number')).hasText('is not a Jira issue')

        when:
        stepButton('Planning').click()

        then:
        assertThat(errorOf(step(), 'Backout plan')).hasText('must not mention Nexus')

        when:
        input(step(), 'Backout plan').fill('Redeploy the previous release.')
        button('Back', true).click()

        then:
        assertThat(currentStep()).hasText('Schedule')
        assertThat(errorOf(step(), 'Installation hours')).hasText('must be after the start')

        when:
        input(step(), 'Installation hours').fill('3')

        then:
        assertThat(hintOf(step(), 'Installation hours')).hasText(~/^until .+, 21:00$/)
        assertThat(input(step(), 'Post-install validation start')).hasValue("${RELEASE_DATE}T21:00")
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "change tasks ProTech refuses are marked on their fields, and the change gets them later on its own page"() {
        given:
        api.respond('POST', '/api/changes/5/tasks', problem(400, 'Bad Request', '2 fields are invalid',
                [errors: [[field: 'tasks[0].start', message: 'must be inside the installation window'],
                          [field: 'tasks[1].details.shortDescription', message: 'is used twice']]]))

        when:
        reviewCertScanner()
        button('Raise the change in ProTech', true).click()

        then:
        assertThat(step().locator('h2')).hasText('Change tasks of CHG0031002')
        assertThat(taskRows()).hasCount(2)

        when:
        button('Create the change tasks in ProTech', true).click()

        then:
        assertThat(page.locator('.save-problem strong')).hasText('The change tasks could not be created: 2 fields are invalid')
        assertThat(page.locator('.save-problem li'))
                .hasText(['Change task 1: task start: must be inside the installation window',
                          'Change task 2: short description: is used twice'] as String[])
        assertThat(currentStep()).hasText('Change tasks')
        assertThat(errorOf(taskRows().nth(0), 'Task start')).hasText('must be inside the installation window')
        assertThat(errorOf(taskRows().nth(1), 'Short description')).hasText('is used twice')

        when:
        button('Remove change task 2', true).click()
        button('Remove change task 1', true).click()
        button('Create the change tasks in ProTech', true).click()

        then:
        assertThat(choiceError()).hasText('Add at least one change task, or add them later')
        assertThat(step().locator('.task-actions .muted')).hasText('0 change tasks')
        api.requests('POST', '/api/changes/5/tasks').size() == 1

        when:
        button('Add them later', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CHG0031002 is raised')
        assertThat(page.locator('.save-problem')).hasCount(0)
        assertThat(step().locator('.review-list li')).hasText(['None yet: add them on the change page.'] as String[])

        when:
        link('Open the change', true).click()
        page.waitForURL('**/beadle/changes/5')

        then:
        assertThat(page.locator('.tasks li')).hasText(['None yet: add them with Edit.'] as String[])

        when:
        link('Edit', true).click()
        page.waitForURL('**/beadle/changes/5/edit')
        button('Add a change task', true).click()
        input(taskRows().first(), 'Assignment group').fill('Release Management')

        then:
        assertThat(taskRows().locator('.kind')).hasText(['Release Management'] as String[])
        hasValues(taskRows().first(), ['Number'            : '', 'Change number': 'CHG0031002',
                                       'Installation start': "${RELEASE_DATE}T18:00", 'Task start': "${RELEASE_DATE}T18:01"])

        when:
        fillIn(taskRows().first(), ['Short description': CERT_TASKS[0].shortDescription, 'Description': CERT_TASKS[0].description])
        def reads = api.requests('GET', '/api/changes/5').size()
        button('Publish to ProTech', true).click()
        page.waitForURL('**/beadle/changes/5')

        then:
        assertThat(page.locator('.tasks li').first()).containsText('not in ProTech yet')
        awaitRequest('PUT', '/api/changes/5').json().tasks == [[number : null, start: "${RELEASE_DATE}T18:01:00.000Z".toString(),
                                                                 details: CERT_TASKS[0] + [application: null]]]
        awaitRequest('GET', '/api/changes/5', reads + 1)
        assertThat(page.locator('.tasks li').first()).containsText('CTASK0320001')
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    void reviewCertScanner() {
        open('/beadle/new-change')
        choose(step(), 'Your department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')
        button('Continue', true).click()
        select(step(), 'FixVersion').fill('CERT 4.2')
        select(step(), 'FixVersion').press('Enter')
        checkbox(step(), 'CERT-130').check()
        continueTo('Review')
    }

    void continueTo(String label) {
        (STEPS.indexOf(label) - STEPS.indexOf(currentStep().textContent().trim())).times { button('Continue', true).click() }
        assertThat(currentStep()).hasText(label)
    }

    Locator step() {
        page.locator('section.step')
    }

    Locator texts() {
        step().locator('.texts')
    }

    Map reviewed() {
        assertThat(step().locator('.scope')).isVisible()
        api.requests('POST', '/api/changes/preview').last().json() as Map
    }

    Locator summary() {
        page.locator('dso-change-summary')
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
