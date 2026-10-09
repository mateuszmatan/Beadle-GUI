package com.bbh.itss.dso.portal.frontend.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import groovy.json.JsonSlurper

import java.util.function.BooleanSupplier
import java.util.regex.Pattern

import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TEMPLATE
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.details
import static com.bbh.itss.dso.portal.frontend.support.StubApi.SIGNED_IN_USER
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static java.time.LocalDate.now
import static java.time.ZoneOffset.UTC

class ChangesSpec extends EditorSpecification {

    static final String SYNC_PROBLEM = 'ProTech could not be reached: Connection refused.'

    def "a release manager picks the department and filters and sorts its ProTech changes in the table header"() {
        when:
        open('/beadle')
        page.waitForURL('**/beadle/changes')

        then:
        assertThat(page.locator('h1')).hasText('ProTech Changes')
        assertThat(page.locator('.page-header .page-description'))
                .hasText('The ProTech changes of your department, read from ProTech each time you open them')
        assertThat(page.locator('.empty-state h3')).hasText('Choose your department to see its ProTech changes.')
        api.requests('GET', '/api/changes').isEmpty()

        when:
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')

        then:
        assertThat(numbers()).hasText(['CHG0031001', 'CHG0030995', 'CHG0030990'] as String[])
        awaitRequest('GET', '/api/changes').params() == [departmentId: '3']
        assertThat(gridHeaders())
                .hasText(['Change', 'Product', 'FixVersion', 'State', 'Installation', 'Short description', 'Tasks', 'Raised', ''] as String[])
        assertThat(column('state')).hasText(['Secondary Approval', 'Implementation', 'Closed'] as String[])
        assertThat(column('tasks')).hasText(['2', '2', '2'] as String[])
        assertThat(page.locator('.toolbar .shown')).hasText('3 of 3 changes')
        assertThat(link('Edit CHG0031001', true)).hasAttribute('href', '/beadle/changes/4/edit')
        assertThat(link('Edit CHG0030995', true)).isVisible()
        assertThat(link('Edit CHG0030990', true)).hasCount(0)

        when:
        filter('FixVersion').fill('4.1')

        then:
        assertThat(numbers()).hasText(['CHG0031001', 'CHG0030995'] as String[])
        assertThat(page.locator('.toolbar .shown')).hasText('2 of 3 changes')

        when:
        filter('FixVersion').fill('')
        choose(page.locator('dso-grid'), 'Filter by state', 'Closed')

        then:
        assertThat(numbers()).hasText(['CHG0030990'] as String[])

        when:
        choose(page.locator('dso-grid'), 'Filter by state', 'Open')
        filter('short description').fill('no such change')

        then:
        assertThat(page.locator('.dso-grid-empty')).hasText('No change matches the filters.')

        when:
        filter('short description').fill('')
        choose(page.locator('dso-grid'), 'Filter by state', 'All')
        sortBy('State')

        then:
        assertThat(numbers()).hasText(['CHG0031001', 'CHG0030995', 'CHG0030990'] as String[])

        when:
        sortBy('State')

        then:
        assertThat(numbers()).hasText(['CHG0030990', 'CHG0030995', 'CHG0031001'] as String[])

        when:
        open('/beadle/changes')

        then:
        assertThat(selected(page.locator('.toolbar'), 'Your department')).hasText('Corporate Technology')
        assertThat(numbers()).hasText(['CHG0031001', 'CHG0030995', 'CHG0030990'] as String[])

        when:
        sortBy('Installation')

        then:
        assertThat(numbers()).hasText(['CHG0030990', 'CHG0030995', 'CHG0031001'] as String[])

        when:
        choose(page.locator('.toolbar'), 'Your department', 'AI Lab')

        then:
        assertThat(page.locator('.empty-state h3')).hasText('No ProTech change of AI Lab yet')
        assertThat(link('New change', true)).hasCount(2)

        when:
        choose(page.locator('.toolbar'), 'Your department', 'Fund Services')

        then:
        assertThat(numbers()).hasText(['CHG0031000'] as String[])
        assertThat(column('state')).hasText(['Escalated approval'] as String[])

        when:
        link('New change', true).click()
        page.waitForURL('**/beadle/new-change')

        then:
        assertThat(selected(page.locator('section.step'), 'Your department')).hasText('Fund Services')
        assertThat(hintOf(page.locator('section.step'), 'Product')).hasText('1 product in the department')
        ownErrors().isEmpty()
    }

    def "the change page shows the workflow read from ProTech and only the department of the change can edit it"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        gridCell(gridRow(page.locator('body'), 'CHG0031001'), 'product').click()
        page.waitForURL('**/beadle/changes/4')

        then:
        assertThat(page.locator('h1')).hasText('CHG0031001')
        assertThat(page.locator('.breadcrumb')).hasText('Changes/CHG0031001')
        assertThat(page.locator('.note.sync')).hasText('Read from ProTech just now')
        assertThat(stages().locator('.label')).hasText(['Draft', 'Business Approval', 'Primary Approval', 'Secondary Approval',
                                                       'CTask approval', 'Escalated approval', 'Implementation', 'Closed'] as String[])
        assertThat(stages()).hasClass(['done', 'done', 'done', 'current', 'later', 'later', 'later', 'later'] as String[])
        assertThat(page.locator('dso-workflow-progress li[aria-current=step] .label')).hasText('Secondary Approval')
        assertThat(page.locator('.tasks li strong')).hasText(CERT_TASKS*.shortDescription as String[])
        assertThat(page.locator('.tasks li').first()).containsText('CTASK0310011')
        assertThat(page.locator('.tasks li .chip')).hasText(['Open', 'Open'] as String[])
        assertThat(page.locator('.tasks li').first()).containsText('Not Yet Requested')
        assertThat(page.locator('.tasks .facts')).hasText([~/^Release Management · CertScanner · CertScanner · starts .+, 17:01$/,
                                                           ~/^Technology Architecture · CertScanner · 3 - Moderate$/] as Pattern[])
        assertThat(link('Edit', true)).hasAttribute('href', '/beadle/changes/4/edit')
        awaitRequest('GET', '/api/changes/4')

        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Fund Services')
        open('/beadle/changes/4')

        then:
        assertThat(button('Edit', true)).isDisabled()
        assertThat(page.locator('.edit-hint')).hasText('Only Corporate Technology can change it')

        when:
        open('/beadle/changes/4/edit')

        then:
        assertThat(page.locator('.banner.refused span').first()).hasText('Only Corporate Technology can change it')
        assertThat(page.locator('form')).hasCount(0)

        when:
        link('Back to the change', true).click()
        page.waitForURL('**/beadle/changes/4')
        newPage()
        open('/beadle/changes/4')

        then:
        assertThat(button('Edit', true)).isDisabled()
        assertThat(page.locator('.edit-hint')).hasText('Choose your department in Changes to change it')
        ownErrors().isEmpty()
    }

    def "a member of the department publishes an update to ProTech and sees that ProTech applied it"() {
        given:
        def date = now(UTC).plusDays(3).toString()

        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        link('Edit CHG0031001', true).click()
        page.waitForURL('**/beadle/changes/4/edit')

        then:
        assertThat(page.locator('h1')).hasText('Edit CHG0031001')
        assertThat(input(texts(), 'Short description')).hasValue('CertScanner CERT 4.1: Upgrade to Java 21')
        assertThat(fields().locator('.template-card h3')).hasText(['Generic request data', 'Approval and Notification', 'Schedule',
                                                                  'Planning', 'Privileged access', 'Risk assessment',
                                                                  'Secure coding'] as String[])
        hasValues(request(), ['Change number'   : 'CHG0031001', 'Approval': 'Requested', 'Opened By': SIGNED_IN_USER,
                              'State'           : 'Secondary Approval', 'Requested For': SIGNED_IN_USER,
                              'Department'      : 'Corporate Technology', 'Assignment group': 'Technology Architecture',
                              'Release'         : 'CERT 4.1', 'Risk': 'Moderate'])
        assertThat(select(request(), 'Type')).isDisabled()
        hasValues(schedule(), ['Installation start'           : "${date}T17:00", 'Installation hours': '2',
                               'Post-install validation start': "${date}T19:00", 'Validation hours': '1',
                               'First use'                    : "${date}T20:00"])
        assertThat(selected(schedule(), 'Downtime')).hasText('No')
        hasValues(fields(), ['L1 approver': 'Olivia Bennett'])
        assertThat(input(fields(), 'Jira project')).hasCount(0)
        hasTaskNumbers('CTASK0310011', 'CTASK0310012')
        assertThat(taskRows().locator('.kind')).hasText(['Release Management', 'Change task'] as String[])
        assertThat(taskRows().locator('.chip')).hasText(['Open', 'Open'] as String[])
        assertThat(input(taskRows().first(), 'Number')).isDisabled()
        hasValues(taskRows().first(), ['Change number'     : 'CHG0031001', 'Approval': 'Not Yet Requested',
                                       'Installation start': "${date}T17:00", 'Installation end': "${date}T19:00",
                                       'Task start'        : "${date}T17:01", 'Affected CI': 'CertScanner',
                                       'Application'       : 'CertScanner'])
        assertThat(selected(taskRows().first(), 'Platform')).hasText('None')
        assertThat(selected(taskRows().nth(1), 'Importance')).hasText('3 - Moderate')

        when:
        input(request(), 'Assignment group').fill('Certificate Services')
        lookUp(request(), 'Incident', 'cert', 'INC0105126')
        choose(schedule(), 'Downtime', 'Yes')
        input(schedule(), 'Downtime hours').fill('1')
        choose(templateCard('Risk assessment'), 'Business impact', 'High')

        then:
        assertThat(input(request(), 'Incident')).hasValue('INC0105126')
        assertThat(input(schedule(), 'Downtime start')).hasValue("${date}T17:00")
        assertThat(hintOf(schedule(), 'Downtime hours')).hasText(~/^until .+, 18:00$/)
        assertThat(input(request(), 'Risk')).hasValue('High')

        when:
        choose(taskRows().first(), 'Platform', 'Distributed')
        fillIn(taskRows().first(), ['Task start'      : "${date}T17:30", 'Packages': 'certscanner-4.1.0.jar',
                                    'Backout packages': 'certscanner-4.0.3.jar'])
        button('Add a change task', true).click()
        lookUp(taskRows().nth(2), 'Assignment group', 'ois', 'OIS Support')
        fillIn(taskRows().nth(2), ['Short description': 'Notify the users', 'Description': 'Send the release notes to the users.'])
        button('Remove change task 2', true).click()
        api.protech.applying = false
        button('Publish to ProTech', true).click()
        page.waitForURL('**/beadle/changes/4')

        then:
        page.evaluate('window.scrollY') == 0
        assertThat(snackBar()).containsText('Your update of CHG0031001 is published to ProTech')
        assertThat(updateBanner()).hasClass(~/\binfo\b/)
        assertThat(updateBanner()).containsText('is waiting for ProTech: Downtime start, Downtime end, Assignment group, Incident, '
                + 'Downtime, Risk assessment, Change tasks.')
        with(awaitRequest('PUT', '/api/changes/4').json()) {
            version == 3
            departmentId == 3
            shortDescription == 'CertScanner CERT 4.1: Upgrade to Java 21'
            schedule == [installationStart: "${date}T17:00:00.000Z".toString(), installationEnd: "${date}T19:00:00.000Z".toString(),
                         validationStart  : "${date}T19:00:00.000Z".toString(), validationEnd: "${date}T20:00:00.000Z".toString(),
                         firstUsage       : "${date}T20:00:00.000Z".toString(), downtimeStart: "${date}T17:00:00.000Z".toString(),
                         downtimeEnd      : "${date}T18:00:00.000Z".toString()]
            template == CERT_TEMPLATE + [release       : 'CERT 4.1', requestedFor: SIGNED_IN_USER, requestedBy: SIGNED_IN_USER,
                                         assignedTo    : SIGNED_IN_USER, department: 'Corporate Technology', risk: null,
                                         assignmentGroup: 'Certificate Services', incident: 'INC0105126', downtime: true,
                                         riskAssessment: (CERT_TEMPLATE.riskAssessment as Map) + [businessImpact: 'High']]
            tasks == [[number : 'CTASK0310011', start: "${date}T17:30:00.000Z".toString(),
                       details: CERT_TASKS[0] + [configurationItem: 'CertScanner', platform: 'Distributed',
                                                 packages         : 'certscanner-4.1.0.jar', backoutPackages: 'certscanner-4.0.3.jar']],
                      [number : null, start: null,
                       details: details('OIS Support', 'Notify the users', 'Send the release notes to the users.')]]
        }

        when:
        def reads = api.requests('GET', '/api/changes/4').size()
        api.protech.applying = true
        page.waitForCondition({ api.requests('GET', '/api/changes/4').size() > reads } as BooleanSupplier)

        then:
        assertThat(updateBanner()).hasClass(~/\bsuccess\b/)
        assertThat(updateBanner()).containsText('ProTech applied the update of')
        assertThat(updateBanner()).containsText('by Corporate Technology. Checked just now.')
        assertThat(page.locator('.tasks li')).hasCount(3)
        assertThat(page.locator('.tasks .facts').first())
                .hasText(~/^Release Management · CertScanner · Distributed · CertScanner · starts .+, 17:30$/)
        assertThat(page.locator('.tasks li').nth(1)).containsText('CTASK0320001')
        assertThat(page.locator('.tasks li').nth(1)).containsText('Notify the users')
        assertThat(page.locator('.tasks .facts').nth(1)).hasText('OIS Support · CertScanner · 3 - Moderate')
        assertThat(page.locator('.tasks li.canceled')).containsText('CTASK0310012')
        assertThat(term(page.locator('dso-change-summary'), 'Assignment group')).hasText('Certificate Services')
        assertThat(term(page.locator('dso-change-summary'), 'Risk')).hasText('High')
        assertThat(term(page.locator('dso-change-summary'), 'Downtime')).hasText(~/, 17:00 to 18:00$/)

        when:
        link('Edit', true).click()
        page.waitForURL('**/beadle/changes/4/edit')

        then:
        hasTaskNumbers('CTASK0310011', 'CTASK0320001')
        hasValues(taskRows().first(), ['Task start': "${date}T17:30", 'Packages': 'certscanner-4.1.0.jar'])
        assertThat(selected(taskRows().first(), 'Platform')).hasText('Distributed')
        hasValues(schedule(), ['Downtime start': "${date}T17:00", 'Downtime hours': '1'])
        ownErrors().isEmpty()
    }

    def "an update of a change that was changed meanwhile is refused until the change is reloaded"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        open('/beadle/changes/4/edit')
        input(texts(), 'Short description').fill('CertScanner 4.1 with Java 21')
        publishFromAnotherTab('/beadle/changes/4/edit')
        button('Publish to ProTech', true).click()

        then:
        assertThat(saveError()).hasText('The change was changed in ProTech or by someone else meanwhile. Reload it and apply your change again.')
        awaitRequest('PUT', '/api/changes/4', 2).json().version == 3

        when:
        button('Reload', true).click()

        then:
        assertThat(saveError()).hasCount(0)
        assertThat(input(texts(), 'Short description')).hasValue('Published elsewhere')

        when:
        input(texts(), 'Short description').fill('CertScanner 4.1 with Java 21')
        button('Publish to ProTech', true).click()
        page.waitForURL('**/beadle/changes/4')

        then:
        assertThat(page.locator('.page-header p')).hasText('CertScanner 4.1 with Java 21')
        awaitRequest('PUT', '/api/changes/4', 3).json().version == 5
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "an update is taken when ProTech only moved the change through its workflow meanwhile"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        open('/beadle/changes/4/edit')
        input(texts(), 'Short description').fill('CertScanner 4.1 with Java 21')
        api.protech.advance(4, 'CTASK_APPROVAL')
        button('Publish to ProTech', true).click()
        page.waitForURL('**/beadle/changes/4')

        then:
        awaitRequest('PUT', '/api/changes/4').json().version == 3
        assertThat(page.locator('.page-header p')).hasText('CertScanner 4.1 with Java 21')
        assertThat(page.locator('dso-workflow-progress li[aria-current=step] .label')).hasText('CTask approval')
        ownErrors().isEmpty()
    }

    def "a member removes every change task of a change and ProTech cancels them"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        open('/beadle/changes/4/edit')
        button('Remove change task 2', true).click()
        button('Remove change task 1', true).click()

        then:
        assertThat(taskRows()).hasCount(0)
        assertThat(page.locator('dso-change-tasks-form .task-actions .muted')).hasText('0 change tasks')
        assertThat(page.locator('dso-change-tasks-form .choice-error')).hasCount(0)

        when:
        def reads = api.requests('GET', '/api/changes/4').size()
        button('Publish to ProTech', true).click()
        page.waitForURL('**/beadle/changes/4')

        then:
        awaitRequest('PUT', '/api/changes/4').json().tasks == []
        awaitRequest('GET', '/api/changes/4', reads + 1)
        assertThat(page.locator('.tasks li.canceled')).hasCount(2)
        assertThat(page.locator('.tasks li .chip')).hasText(['Canceled', 'Canceled'] as String[])
        ownErrors().isEmpty()
    }

    def "a change ProTech did not update says what it kept, and a closed change cannot be edited"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        open('/beadle/changes/2')

        then:
        assertThat(page.locator('h1')).hasText('CHG0030995')
        assertThat(updateBanner()).hasClass(~/\bdanger\b/)
        assertThat(updateBanner()).containsText('ProTech did not apply the update of')
        assertThat(updateBanner()).containsText(': Installation start, Installation end. A minute later ProTech still held its own values, so Beadle shows those.')
        assertThat(page.locator('dso-workflow-progress li[aria-current=step] .label')).hasText('Implementation')
        assertThat(stages().nth(5)).hasClass(~/\bskipped\b/)
        assertThat(stages().nth(5).locator('.when')).hasText('Skipped')
        assertThat(page.locator('.tasks li').first()).containsText('Work in progress')
        assertThat(page.locator('.tasks li').first()).containsText('Approved')
        assertThat(link('Edit', true)).isVisible()

        when:
        open('/beadle/changes/1')

        then:
        assertThat(page.locator('h1')).hasText('CHG0030990')
        assertThat(page.locator('dso-workflow-progress li[aria-current=step] .label')).hasText('Closed')
        assertThat(page.locator('.tasks li').first()).containsText('Closed')
        assertThat(link('Edit', true)).hasCount(0)
        assertThat(button('Edit', true)).hasCount(0)

        when:
        open('/beadle/changes/1/edit')

        then:
        assertThat(page.locator('.banner.refused span').first()).hasText('CHG0030990 is closed in ProTech and can no longer be changed')
        assertThat(page.locator('form')).hasCount(0)
        ownErrors().isEmpty()
    }

    def "Beadle says when ProTech could not be reached and shows what it last read"() {
        given:
        def stored = new JsonSlurper().parseText(api.handle('GET', '/api/changes/4', null, null).body) as Map
        api.get('/api/changes') { [stored + [syncProblem: SYNC_PROBLEM]] }
        api.get('/api/changes/4') { stored + [syncProblem: SYNC_PROBLEM] }

        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')

        then:
        assertThat(page.locator('.banner[role=status]'))
                .hasText("$SYNC_PROBLEM The table shows what Beadle last read from ProTech.".toString())

        when:
        link('CHG0031001', true).click()

        then:
        assertThat(page.locator('.sync-problem')).containsText("$SYNC_PROBLEM Beadle shows what it last read from ProTech".toString())
        assertThat(page.locator('.note.sync')).hasCount(0)
        ownErrors().isEmpty()
    }

    Locator numbers() {
        column('number')
    }

    Locator column(String name) {
        gridCells(page.locator('body'), name)
    }

    Locator filter(String label) {
        gridFilter(label)
    }

    void sortBy(String label) {
        holdingText(gridHeaders(), label).locator('.ag-header-cell-label').click()
    }

    Locator stages() {
        page.locator('dso-workflow-progress li')
    }

    Locator updateBanner() {
        page.locator('.banner.update')
    }

    Locator texts() {
        page.locator('section.texts')
    }

    Locator schedule() {
        templateCard('Schedule')
    }

    Locator request() {
        templateCard('Generic request data')
    }

    Locator fields() {
        page.locator('section.fields')
    }

    void hasTaskNumbers(String... expected) {
        assertThat(taskRows()).hasCount(expected.length)
        expected.eachWithIndex { number, index -> assertThat(input(taskRows().nth(index), 'Number')).hasValue(number) }
    }

    Locator term(Locator scope, String label) {
        scope.locator("dl.rows dt:text-is('${label}') + dd")
    }

    void publishFromAnotherTab(String path) {
        Page other = context.newPage()
        other.navigate(url(path))
        other.getByLabel('Short description').first().fill('Published elsewhere')
        other.getByRole(BUTTON, new Page.GetByRoleOptions().setName('Publish to ProTech').setExact(true)).click()
        other.waitForURL('**/beadle/changes/4')
        other.close()
    }
}
