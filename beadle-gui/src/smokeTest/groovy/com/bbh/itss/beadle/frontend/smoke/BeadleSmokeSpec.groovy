package com.bbh.itss.beadle.frontend.smoke

import com.bbh.itss.beadle.frontend.support.BeadleSpecification
import spock.lang.IgnoreIf

import java.util.function.BooleanSupplier

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat

class BeadleSmokeSpec extends BeadleSpecification {

    static final List<Map<String, String>> SECTIONS = [
            [label      : 'Changes', heading: 'ProTech Changes', path: '/changes',
             description: 'The ProTech changes of your business service and where each one is in its approval workflow. A change is read again from ProTech when you open it.'],
            [label      : 'New Change', heading: 'New ProTech Change', path: '/new-change',
             description: 'Raise a ProTech change for a production release in guided steps. The product\'s change template fills in the answers and Jira provides the scope.'],
            [label      : 'Admin', heading: 'Beadle Admin', path: '/admin/products',
             description: 'Business services, products and each product\'s change template: the answers every new change of the product starts with.'],
            [label      : 'Action History', heading: 'Action History', path: '/action-history',
             description: 'What people did in Beadle and what Beadle-ProTech and Beadle-Internal reported back, newest first, with the request and the outcome of every action.']]

    static final Map<String, String> ADMIN_TABS = ['Business services': '/admin/business-services', Products: '/admin/products']

    static final String CORPORATE_TECHNOLOGY = "localStorage.setItem('beadle.business-service', '3')"

    def "Beadle shows its title, only its own sections in the menu, the signed-in user and the footer"() {
        when:
        open('/admin/products')

        then:
        assertThat(page.locator('header .brand-name')).hasText('BBH Beadle')
        assertThat(menuLinks()).hasText(MENU as String[])
        assertThat(page.locator('header .account .user-name')).isVisible()
        assertThat(buttonIn(page.locator('header .account'), 'Sign out')).isVisible()
        assertThat(activeMenuLink()).hasText('Admin')
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

    def "the short addresses lead to their pages"() {
        expect:
        [['/', 'ProTech Changes'], ['/admin', 'Beadle Admin'], ['/changes/new', 'New ProTech Change']].every { path, heading ->
            open(path)
            assertThat(page.locator('h1').first()).hasText(heading)
            true
        }
        ownErrors().isEmpty()
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
    def "detail and edit pages load data from the API and show only lookup magnifier icons"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)

        expect:
        [['/admin/products/1', 'CertScanner', 20],
         ['/changes/4', 'CHG0031001', 0],
         ['/changes/4/edit', 'Edit CHG0031001', 20]].every { String path, String heading, int lookups ->
            open(path)
            assertThat(page.locator('h1').first()).containsText(heading)
            assertThat(page.locator('button.lookup svg-icon')).hasCount(lookups)
            assert page.locator('svg-icon').count() == lookups
            assert page.locator("button.lookup svg-icon[name='search']").count() == lookups
            true
        }
        ownErrors().isEmpty()
    }

    @IgnoreIf({ BeadleSpecification.remoteBaseUrl() })
    def "key pages fit narrow windows without page-level horizontal scrolling"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)
        page.setViewportSize(800, 900)

        expect:
        ['/changes', '/new-change', '/changes/4', '/admin/products', '/action-history'].every { path ->
            open(path)
            [800, 600].every { width ->
                page.setViewportSize(width, 900)
                assert (page.evaluate('() => document.documentElement.scrollWidth - document.documentElement.clientWidth') as int) == 0
                true
            }
        }
        ownErrors().isEmpty()
    }

    @IgnoreIf({ BeadleSpecification.remoteBaseUrl() })
    def "the grids fit a laptop window and scroll sideways with the bar ag-grid sizes in a narrower one"() {
        given:
        page.addInitScript(CORPORATE_TECHNOLOGY)
        page.setViewportSize(1280, 900)

        expect: 'the changes and the action history fit 1280 px without a horizontal scrollbar'
        ['/changes', '/action-history'].every { path ->
            open(path)
            page.locator('beadle-grid .ag-row').first().waitFor()
            assert !(barOf('beadle-grid').wider as boolean)
            true
        }

        when: 'the window gets narrower than the columns of the changes'
        open('/changes')
        page.setViewportSize(800, 900)
        def raised = page.locator("beadle-grid .ag-header-cell[col-id='raised']")

        then: 'the bar keeps the height ag-grid gave it and scrolls to the last columns'
        page.waitForCondition({ barOf('beadle-grid').wider as boolean } as BooleanSupplier)
        barOf('beadle-grid').height == barOf('beadle-grid').sized
        assertThat(raised).not().isInViewport()

        when:
        page.locator('beadle-grid .ag-body-horizontal-scroll-viewport').evaluate('bar => { bar.scrollLeft = bar.scrollWidth }')

        then:
        assertThat(raised).isInViewport()
        ownErrors().isEmpty()
    }

    Map barOf(String grid) {
        page.evaluate("""() => {
            const bar = document.querySelector("${grid} .ag-body-horizontal-scroll-viewport");
            return { wider: bar.scrollWidth > bar.clientWidth, height: getComputedStyle(bar).height, sized: bar.style.height };
        }""") as Map
    }

    def "an unknown address falls back to the changes"() {
        when:
        open('/no-such-page')

        then:
        page.waitForURL('**/changes')
        assertThat(page.locator('h1')).hasText('ProTech Changes')
    }
}
