package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator

import java.time.LocalDate

import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.PLANNING
import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.RELEASE_DATE
import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.suggestedTasks
import static com.bbh.itss.dso.portal.gui.support.StubApi.SIGNED_IN_USER
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.OPTION
import static java.time.LocalDate.now

class ProductionChangeSpec extends EditorSpecification {

    static final List<String> STEPS = ['Request data', 'Jira', 'Approval', 'Schedule', 'Planning', 'Privileged access',
                                       'Risk assessment', 'Secure coding', 'Review', 'Raised']

    def "a release manager raises a ProTech change section by section, with the lookups, the downtime window and the risk lists"() {
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
        assertThat(select(step(), 'Category')).hasText('Application')
        assertThat(select(step(), 'Type')).hasText('Standard')
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

        when:
        checkbox(step(), 'CERT-122').uncheck()
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
        assertThat(select(step(), 'Downtime')).hasText('No')
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
        assertThat(select(step(), 'How many privileged accounts')).hasText('None')
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
        assertThat(select(step(), 'Number of BBH users impacted')).hasText('5-25')
        assertThat(select(step(), 'Business impact')).hasText('Low')

        when:
        select(step(), 'Business impact').click()

        then:
        assertThat(page.getByRole(OPTION)).hasText(['Not assessed', 'None', 'Low', 'Medium', 'High'] as String[])

        when:
        page.getByRole(OPTION).last().click()
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
        assertThat(taskRows()).hasCount(2)
        hasValues(taskRows().nth(0), ['Short description': 'Deploy CertScanner to production',
                                      'Description'      : CERT_TASKS[0].description])
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
        with(awaitRequest('POST', '/api/changes/preview').json()) {
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
            tasks == CERT_TASKS
            !it.containsKey('serviceIds')
        }

        when:
        button('Remove change task 2', true).click()
        button('Add a change task', true).click()
        input(taskRows().nth(1), 'Short description').fill('Run the database scripts')
        button('Raise the change in ProTech', true).click()

        then:
        assertThat(currentStep()).hasText('Review')
        assertThat(errorOf(taskRows().nth(1), 'Description')).hasText('Required')
        assertThat(choiceError()).hasText('Check the change tasks')
        api.requests('POST', '/api/changes').isEmpty()

        when:
        input(taskRows().nth(1), 'Description').fill('Run the Liquibase changesets of CertScanner.')
        input(texts(), 'Short description').fill('CertScanner 4.2 release')
        button('Raise the change in ProTech', true).click()

        then:
        assertThat(step().locator('h2')).hasText('CHG0031002 is raised')
        assertThat(step().locator('.review-list li')).hasText(['CTASK0310021 · Deploy CertScanner to production',
                                                                   'CTASK0310022 · Run the database scripts'] as String[])
        assertThat(step().locator('.next-steps')).containsText('Grace Turner, Olivia Bennett, William Hayes approve the change in ProTech')
        with(awaitRequest('POST', '/api/changes').json()) {
            productId == 1
            !it.containsKey('serviceIds')
            tasks == [CERT_TASKS[0], [shortDescription: 'Run the database scripts',
                                      description     : 'Run the Liquibase changesets of CertScanner.']]
            fixVersion == 'CERT 4.2'
            epicKeys == ['CERT-120']
            storyKeys == ['CERT-121']
            shortDescription == 'CertScanner 4.2 release'
            schedule.downtimeEnd == "${RELEASE_DATE}T19:30:00.000Z"
            template.secureCodingTicket == 'SEC-4711'
        }

        when:
        link('Open the change', true).click()

        then:
        page.waitForURL('**/beadle/changes/5')
        assertThat(page.locator('h1')).hasText('CHG0031002')
        assertThat(page.locator('.tasks')).containsText('CTASK0310021')
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
                           'Direct business service': '', 'Risk': ''])

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
        assertThat(input(step(), 'Installation start')).hasValue('')

        when:
        button('Continue', true).click()

        then:
        assertThat(currentStep()).hasText('Schedule')
        assertThat(step().locator('dso-change-schedule .choice-error')).hasText('Enter the date and time of the installation start')
        assertThat(step().locator('.step-problem')).hasText('Some fields need your attention.')

        when:
        input(step(), 'Installation start').fill("${inDays(3)}T18:00")

        then:
        hasValues(step(), ['Post-install validation start': "${inDays(3)}T20:00", 'First use': "${inDays(3)}T21:00"])
        assertThat(hintOf(step(), 'Installation hours')).hasText(~/^until .+, 20:00$/)

        when:
        continueTo('Review')

        then:
        assertThat(review('Risk')).hasText('not set')
        assertThat(review('L1 approver')).hasText('Emma Brooks')
        with(awaitRequest('POST', '/api/changes/preview').json()) {
            productId == 2
            tasks == suggestedTasks(2)
            epicKeys == ['PAYHUB-130']
            storyKeys == ['PAYHUB-132']
            template.jiraProjectKey == 'PAYHUB'
            template.requestedFor == SIGNED_IN_USER
            template.department == 'Fund Services'
            template.approvers == [businessApprover: null, l1Manager: 'Emma Brooks', l2Manager: null]
            (template.riskAssessment as Map).values().every { it == null }
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
                          [field: 'tasks[1].shortDescription', message: 'is used twice']]]))

        when:
        open('/beadle/new-change')
        choose(step(), 'Your department', 'Corporate Technology')
        choose(step(), 'Product', 'CertScanner (CERTSCANNER)')
        button('Continue', true).click()
        select(step(), 'FixVersion').fill('CERT 4.2')
        select(step(), 'FixVersion').press('Enter')
        checkbox(step(), 'CERT-130').check()
        continueTo('Review')

        then:
        assertThat(review('Jira')).hasText('CERT-130 CERT-131')

        when:
        button('Raise the change in ProTech', true).click()

        then:
        assertThat(page.locator('.save-problem strong')).hasText('3 fields are invalid')
        assertThat(page.locator('.save-problem li'))
                .hasText(['Installation end: must be after the start', 'Backout plan: must not mention Nexus',
                          'Change task 2: short description: is used twice'] as String[])
        assertThat(currentStep()).hasText('Review')
        assertThat(errorOf(taskRows().nth(1), 'Short description')).hasText('is used twice')

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

    Locator summary() {
        page.locator('dso-change-summary')
    }

    Locator taskRows() {
        step().locator('dso-change-tasks-form .task-row')
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
        LocalDate.parse(RELEASE_DATE).plusDays(1).toString()
    }

    static String inDays(int days) {
        now().plusDays(days).toString()
    }
}
