package com.bbh.itss.dso.portal.frontend.smoke

import com.bbh.itss.dso.portal.frontend.support.BeadleSpecification
import spock.lang.IgnoreIf

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class BeadleSmokeSpec extends BeadleSpecification {

    static final List<Map<String, String>> SECTIONS = [
            [label      : 'Changes', heading: 'ProTech Changes', path: '/changes',
             description: 'The ProTech changes of your department and where each one is in its approval workflow. A change is read again from ProTech when you open it.'],
            [label      : 'New Change', heading: 'New ProTech Change', path: '/new-change',
             description: 'Raise a ProTech change for a production release in guided steps. The product\'s change template fills in the answers and Jira provides the scope.'],
            [label      : 'Admin', heading: 'Beadle Admin', path: '/admin/products',
             description: 'Departments, products and each product\'s change template: the answers every new change of the product starts with.']]

    static final Map<String, String> ADMIN_TABS = [Departments: '/admin/departments', Products: '/admin/products']

    static final String CORPORATE_TECHNOLOGY = "localStorage.setItem('dso.beadle.department', '3')"

    def "Beadle shows its title, only its own sections in the menu and the footer"() {
        when:
        open('/admin/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH Beadle')
        assertThat(menuLinks()).hasText(MENU as String[])
        assertThat(activeMenuLink()).hasText('Admin')
        !page.locator('body').textContent().contains('DevSecOps')
        SECTIONS.every { section -> !page.locator('header.topbar').textContent().contains(section.description) }
        assertThat(page.locator('footer')).containsText('BBH 2026')
        ownErrors().isEmpty()
    }

    def "every menu link opens its section with the full name and the description on top"() {
        given:
        open('/changes')

        expect:
        (SECTIONS.drop(1) + SECTIONS.take(1)).every { section ->
            menuLink(section.label).click()
            page.waitForURL("**${section.path}")
            assertThat(page.locator('h1')).hasText(section.heading)
            assertThat(page.locator('.page-header .page-description')).hasText(section.description)
            true
        }
        ownErrors().isEmpty()
    }

    def "#path opens from a direct link"() {
        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        [path, heading] << SECTIONS.collect { [it.path, it.heading] } +
                ADMIN_TABS.values().collect { [it, 'Beadle Admin'] } +
                [['/', 'ProTech Changes'], ['/admin', 'Beadle Admin'], ['/changes/new', 'New ProTech Change']]
    }

    def "every tab of Beadle Admin opens its page with the tab marked"() {
        given:
        open(ADMIN_TABS.values().first())

        expect:
        assertThat(page.locator('nav.tab-bar a')).hasText(ADMIN_TABS.keySet() as String[])
        ADMIN_TABS.every { label, path ->
            tab(label).click()
            page.waitForURL("**$path")
            assertThat(page.locator('h1')).hasText('Beadle Admin')
            assertThat(page.locator('nav.tab-bar a.active')).hasText(label)
            assertThat(tab(label)).hasAttribute('aria-current', 'page')
            assertThat(page.locator('.page.admin > router-outlet + *')).hasCount(1)
            true
        }
        ownErrors().isEmpty()
    }

    @IgnoreIf({ BeadleSpecification.remoteBaseUrl() })
    def "#path shows #heading from the API"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        assertThat(page.locator('h1').first()).containsText(heading)
        ownErrors().isEmpty()

        where:
        path                | heading
        '/admin/products/1' | 'CertScanner'
        '/changes/4'        | 'CHG0031001'
        '/changes/4/edit'   | 'Edit CHG0031001'
    }

    @IgnoreIf({ BeadleSpecification.remoteBaseUrl() })
    def "#path shows no icons but the magnifiers of the lookups"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        assertThat(page.locator('button.lookup svg-icon')).hasCount(lookups)
        page.locator('svg-icon').count() == lookups
        page.locator("button.lookup svg-icon[name='search']").count() == lookups
        ownErrors().isEmpty()

        where:
        path                 | lookups
        '/changes'           | 0
        '/new-change'        | 0
        '/changes/4'         | 0
        '/changes/4/edit'    | 20
        '/changes/2'         | 0
        '/admin/departments' | 0
        '/admin/products'    | 0
        '/admin/products/1'  | 20
    }

    @IgnoreIf({ BeadleSpecification.remoteBaseUrl() })
    def "#path fits a #width px window without horizontal scrolling"() {
        given:
        page.setViewportSize(width, 900)
        page.addInitScript(CORPORATE_TECHNOLOGY)

        when:
        open(path)

        then:
        (page.evaluate('() => document.documentElement.scrollWidth - document.documentElement.clientWidth') as int) == 0
        ownErrors().isEmpty()

        where:
        [path, width] << [['/changes', '/new-change', '/changes/4', '/changes/4/edit', '/changes/2', '/admin/departments',
                           '/admin/products', '/admin/products/1'], [800, 600]].combinations()
    }

    def "an unknown address falls back to the changes"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/changes')
        assertThat(page.locator('h1')).hasText('ProTech Changes')
    }
}
