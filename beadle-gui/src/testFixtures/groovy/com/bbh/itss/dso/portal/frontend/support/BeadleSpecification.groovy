package com.bbh.itss.dso.portal.frontend.support

import com.microsoft.playwright.Locator

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

abstract class BeadleSpecification extends GuiSpecification {

    static final List<String> MENU = ['Changes', 'New Change', 'Admin']

    static final List<String> RELEASE_TASK_FIELDS = ['Number', 'Change number', 'Assignment group', 'Assigned to', 'Affected CI',
                                                     'Approval', 'Installation start', 'Installation end', 'Platform', 'Task start',
                                                     'Application', 'Packages', 'Backout packages', 'Short description',
                                                     'Description', 'Additional comments']

    static final List<String> OTHER_TASK_FIELDS = ['Number', 'Change number', 'Assignment group', 'Assigned to', 'Importance',
                                                   'Affected CI', 'Approval', 'Installation start', 'Installation end',
                                                   'Short description', 'Description', 'Additional comments']

    @Override
    StubApi newApi() {
        new BeadleStubApi()
    }

    BeadleStubApi getBeadle() {
        api as BeadleStubApi
    }

    Locator templateCard(String title) {
        page.locator("section.template-card[aria-label='${title}']")
    }

    Locator taskRows() {
        page.locator('dso-change-tasks-form .task-row')
    }

    Locator found() {
        dialog().locator('.results .value')
    }

    void lookUp(Locator scope, String label, String search, String value) {
        buttonIn(scope, "Find $label").click()
        assertThat(dialog().locator('h2')).hasText("Find $label")
        input(dialog(), 'Search').fill(search)
        assertThat(found().first()).isVisible()
        holding(dialog().locator('.results button'), ".value:text-is('${value}')").click()
        assertThat(dialog()).hasCount(0)
    }
}
