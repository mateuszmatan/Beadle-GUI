package com.bbh.itss.dso.portal.gui.regression

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import groovy.json.JsonSlurper

import java.time.LocalDate
import java.util.function.BooleanSupplier

import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.CERT_TEMPLATE
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
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
        assertThat(page.locator('tr.mat-mdc-header-row').first().locator('th'))
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
        choose(page.locator('tr.filters'), 'Filter by state', 'Closed')

        then:
        assertThat(numbers()).hasText(['CHG0030990'] as String[])

        when:
        choose(page.locator('tr.filters'), 'Filter by state', 'Open')
        filter('short description').fill('no such change')

        then:
        assertThat(page.locator('.no-match')).hasText('No change matches the filters.')

        when:
        filter('short description').fill('')
        choose(page.locator('tr.filters'), 'Filter by state', 'All')
        sortBy('State')

        then:
        assertThat(numbers()).hasText(['CHG0031001', 'CHG0030995', 'CHG0030990'] as String[])

        when:
        sortBy('State')

        then:
        assertThat(numbers()).hasText(['CHG0030990', 'CHG0030995', 'CHG0031001'] as String[])

        when:
        sortBy('Installation')

        then:
        assertThat(numbers()).hasText(['CHG0030990', 'CHG0030995', 'CHG0031001'] as String[])

        when:
        open('/beadle/changes')

        then:
        assertThat(select(page.locator('.toolbar'), 'Your department')).hasText('Corporate Technology')
        assertThat(numbers()).hasText(['CHG0031001', 'CHG0030995', 'CHG0030990'] as String[])

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
        assertThat(select(page.locator('section.step'), 'Department')).hasText('Fund Services')
        assertThat(hintOf(page.locator('section.step'), 'Product')).hasText('1 product in the department')
        ownErrors().isEmpty()
    }

    def "the change page shows the workflow read from ProTech and only the department of the change can edit it"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        holdingText(page.locator('tbody tr'), 'CHG0031001').locator('td.mat-column-product').click()
        page.waitForURL('**/beadle/changes/4')

        then:
        assertThat(page.locator('h1')).hasText('CHG0031001')
        assertThat(page.locator('.breadcrumb')).hasText('Changes/CHG0031001')
        assertThat(page.locator('.note.sync')).hasText('Read from ProTech just now')
        assertThat(stages().locator('.label')).hasText(['Draft', 'Business Approval', 'Primary Approval', 'Secondary Approval',
                                                       'CTask approval', 'Escalated approval', 'Implementation', 'Closed'] as String[])
        assertThat(stages()).hasClass(['done', 'done', 'done', 'current', 'later', 'later', 'later', 'later'] as String[])
        assertThat(page.locator('dso-workflow-progress li[aria-current=step] .label')).hasText('Secondary Approval')
        assertThat(page.locator('.tasks li')).hasCount(2)
        assertThat(page.locator('.tasks li').first()).containsText('CTASK0310011')
        assertThat(page.locator('.tasks li').first()).containsText('Open')
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
        def date = LocalDate.now(UTC).plusDays(3).toString()

        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        link('Edit CHG0031001', true).click()
        page.waitForURL('**/beadle/changes/4/edit')

        then:
        assertThat(page.locator('h1')).hasText('Edit CHG0031001')
        assertThat(input(texts(), 'Short description')).hasValue('CertScanner CERT 4.1: Upgrade to Java 21')
        hasValues(schedule(), ['Installation start date': date, 'Installation start time': '17:00',
                               'Installation end time'  : '19:00'])
        hasValues(fields(), ['Assignment group': 'Technology Architecture', 'L1 manager': 'Olivia Bennett'])
        assertThat(input(fields(), 'Jira project')).hasCount(0)
        assertThat(input(fields(), 'Installation hours')).hasCount(0)
        assertThat(editedTasks().locator('.number')).hasText(['CTASK0310011', 'CTASK0310012'] as String[])

        when:
        input(schedule(), 'Installation start time').fill('16:00')
        input(fields(), 'Assignment group').fill('Certificate Services')
        button('Remove change task 2', true).click()
        button('Add a change task', true).click()
        fillIn(editedTasks().nth(1), ['Short description': 'Notify the users',
                                      'Description'      : 'Send the release notes to the users.'])
        button('Publish to ProTech', true).click()
        page.waitForURL('**/beadle/changes/4')

        then:
        assertThat(snackBar()).containsText('Your update of CHG0031001 is published to ProTech')
        assertThat(updateBanner()).hasClass(~/\binfo\b/)
        assertThat(updateBanner()).containsText('is waiting for ProTech: Installation start, Assignment group, Change tasks.')
        with(awaitRequest('PUT', '/api/changes/4').json()) {
            version == 3
            departmentId == 3
            shortDescription == 'CertScanner CERT 4.1: Upgrade to Java 21'
            schedule.installationStart == "${date}T16:00:00.000Z".toString()
            schedule.installationEnd == "${date}T19:00:00.000Z".toString()
            template == CERT_TEMPLATE + [release: 'CERT 4.1', assignmentGroup: 'Certificate Services']
            tasks == [[number: 'CTASK0310011'] + CERT_TASKS[0],
                      [number: null, shortDescription: 'Notify the users', description: 'Send the release notes to the users.']]
        }

        when:
        page.waitForCondition({ api.requests('GET', '/api/changes/4').size() >= 2 } as BooleanSupplier)

        then:
        assertThat(updateBanner()).hasClass(~/\bsuccess\b/)
        assertThat(updateBanner()).containsText('ProTech applied the update of')
        assertThat(updateBanner()).containsText('by Corporate Technology. Checked just now.')
        assertThat(page.locator('.tasks li')).hasCount(3)
        assertThat(page.locator('.tasks li').nth(1)).containsText('CTASK0320001')
        assertThat(page.locator('.tasks li').nth(1)).containsText('Notify the users')
        assertThat(page.locator('.tasks li.canceled')).containsText('CTASK0310012')
        assertThat(term(page.locator('dso-change-summary'), 'Assignment group')).hasText('Certificate Services')

        when:
        link('Edit', true).click()
        page.waitForURL('**/beadle/changes/4/edit')

        then:
        assertThat(editedTasks().locator('.number')).hasText(['CTASK0310011', 'CTASK0320001'] as String[])
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

    def "a change ProTech did not update says what it kept, and a closed change cannot be edited"() {
        when:
        open('/beadle/changes')
        choose(page.locator('.toolbar'), 'Your department', 'Corporate Technology')
        open('/beadle/changes/2')

        then:
        assertThat(page.locator('h1')).hasText('CHG0030995')
        assertThat(updateBanner()).hasClass(~/\bdanger\b/)
        assertThat(updateBanner()).containsText('ProTech did not apply the update of')
        assertThat(updateBanner()).containsText(': Installation start, Installation end. ProTech did not apply the update within a minute')
        assertThat(page.locator('dso-workflow-progress li[aria-current=step] .label')).hasText('Implementation')
        assertThat(stages().nth(5)).hasClass(~/\bskipped\b/)
        assertThat(stages().nth(5).locator('.when')).hasText('Skipped')
        assertThat(page.locator('.tasks li').first()).containsText('Work in progress')
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
        page.locator("tbody td.mat-column-${name}")
    }

    Locator filter(String label) {
        page.locator("input[aria-label='Filter by ${label}']")
    }

    void sortBy(String label) {
        holdingText(page.locator('th[mat-sort-header]'), label).click()
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
        holding(page.locator('section.block'), "h2:text-is('Schedule')")
    }

    Locator fields() {
        page.locator('section.fields')
    }

    Locator editedTasks() {
        page.locator('dso-change-tasks-form .task-row')
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
