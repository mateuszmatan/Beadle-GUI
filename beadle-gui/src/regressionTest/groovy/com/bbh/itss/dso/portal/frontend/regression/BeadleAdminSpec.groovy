package com.bbh.itss.dso.portal.frontend.regression

import com.bbh.itss.dso.portal.frontend.support.BeadleSpecification
import com.microsoft.playwright.Locator

import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.frontend.support.ChangeStubs.CERT_TEMPLATE
import static com.bbh.itss.dso.portal.frontend.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static java.time.Instant.now
import static java.time.temporal.ChronoUnit.DAYS

class BeadleAdminSpec extends BeadleSpecification {

    static final List<String> DEPARTMENTS = ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']
    static final String NOT_REACHED = 'The database is not reachable at the moment.'

    def setup() {
        api.get('/api/change-profiles') {
            [[productId: 1, productName: 'CertScanner', version: 2, updatedAt: now().minus(2, DAYS).toString()]]
        }
    }

    def "Beadle Admin has a Departments and a Products tab, and its departments count their changes"() {
        when:
        open('/')
        page.waitForURL('**/changes')
        menuLink('Admin').click()
        page.waitForURL('**/admin/products')

        then:
        assertThat(page.locator('h1')).hasText('Beadle Admin')
        assertThat(page.locator('nav.tab-bar a')).hasText(['Departments', 'Products'] as String[])
        assertThat(page.locator('nav.tab-bar a.active')).hasText('Products')

        when:
        link('Departments', true).click()
        page.waitForURL('**/admin/departments')

        then:
        assertThat(gridHeaders()).hasText(['Department', 'Products', ''] as String[])
        assertThat(page.locator('section.chart')).hasCount(0)
        assertThat(page.locator('.list-header .summary')).hasText('5 departments with 2 products')
        assertThat(page.locator('.departments .section-help')).hasText('Every product belongs to one department, and people ' +
                'choose their department to see its changes. Only an empty department can be deleted.')
        assertThat(buttonIn(gridRow(page.locator('body'), 'Custody'), 'Delete Custody')).isEnabled()
        assertThat(buttonIn(gridRow(page.locator('body'), 'AI Lab'), 'Delete AI Lab')).isEnabled()

        when:
        button('Add department', true).click()
        input(dialog(), 'Name').fill('Treasury')
        dialogButton('Add department').click()

        then:
        assertThat(snackBar()).containsText('Treasury added')
        awaitRequest('POST', '/api/departments').json() == [name: 'Treasury']
        assertThat(gridRows().locator('.name')).hasText((DEPARTMENTS + 'Treasury') as String[])
        ownErrors().isEmpty()
    }

    def "the Products tab lists the products by department with the state of their change template"() {
        when:
        open('/admin/products')

        then:
        assertThat(cardNames()).hasText(DEPARTMENTS as String[])
        assertThat(card('Corporate Technology').locator('.tally')).hasText('1 product')
        assertThat(card('Custody').locator('.no-products')).hasText('No products in Custody yet.')
        assertThat(page.locator('.toolbar .count')).hasText('2 products in 5 departments')
        assertThat(gridHeaders(card('Fund Services'))).hasText(['Product', 'Owner team', 'Change template'] as String[])
        assertThat(productRow('CertScanner').locator('.ag-cell')).hasText(['CertScannerCERTSCANNER', 'Technology Architecture',
                                                                          'Filled in · saved 2 days ago'] as String[])
        assertThat(productRow('CertScanner')).hasClass(~/\bclickable\b/)
        assertThat(gridCell(productRow('Payments Hub'), 'template')).hasText('Not filled in yet')
        assertThat(page.locator('.tab-help')).hasText('Open a product to see its details and fill in its change template. ' +
                'Until then, its new changes start with values suggested from its name, code and owner team.')
        !page.locator('dso-beadle-products').textContent().contains('service')

        when:
        page.locator('input[aria-label="Search products"]').fill('pay')

        then:
        assertThat(cardNames()).hasText(['Fund Services'] as String[])
        assertThat(page.locator('.toolbar .count')).hasText('1 product in 1 department')

        when:
        api.respond('GET', '/api/change-profiles', problem(503, 'Unavailable', 'The change templates are not available'))
        open('/admin/products')

        then:
        assertThat(gridCell(productRow('CertScanner'), 'template')).hasText('Not known')
        assertThat(page.locator('.banner')).containsText('The products are listed, but the state of their change templates ' +
                'could not be loaded. The change templates are not available')

        when:
        gridCell(productRow('Payments Hub'), 'ownerTeam').click()

        then:
        page.waitForURL('**/admin/products/2')
        assertThat(facts()).hasText(['PAYHUB', 'Fund Services', 'Payments Engineering', 'payments-eng@bbh.com'] as String[])
        assertThat(page.locator('section.defaults h2')).hasText('Change template')
        assertThat(page.locator('dso-product-admin table')).hasCount(0)
        assertThat(page.getByText('mobile-app')).hasCount(0)
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    def "an administrator adds a product without any DevSecOps setting and lands on its page"() {
        given:
        api.respond('POST', '/api/products', problem(400, 'Bad Request', 'The portal did not accept some values.',
                [errors: [[field: 'ownerTeam', message: 'is not a BBH team']]]))
        api.get('/api/products/3/change-profile') {
            [productId: 3, productName: 'Trade Archive', version: null, updatedAt: null, template: CERT_TEMPLATE, tasks: CERT_TASKS]
        }

        when:
        open('/admin/products')
        buttonIn(card('Custody'), 'Add product to Custody').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Add product')
        assertThat(dialog().locator('dso-label')).hasText(['Product name', 'Product code', 'Department', 'Owner team',
                                                           'Contact e-mail'] as String[])
        assertThat(hintOf(dialog(), 'Product code')).hasText('Short unique name used in reports, for example PAYHUB. Made from the name.')
        assertThat(selected(dialog(), 'Department')).hasText('Custody')

        when:
        dialogButton('Add product').click()

        then:
        hasErrors(dialog(), ['Product name': 'Required', 'Product code': 'Required'])
        api.requests('POST', '/api/products').isEmpty()

        when:
        input(dialog(), 'Product name').fill('Trade Archive')

        then:
        assertThat(input(dialog(), 'Product code')).hasValue('TRADEARCHIVE')

        when:
        fillIn(dialog(), ['Owner team': 'Custody'])
        dialogButton('Add product').click()

        then:
        assertThat(errorOf(dialog(), 'Owner team')).hasText('is not a BBH team')
        assertThat(dialog().locator('[role=alert]')).hasCount(0)

        when:
        beadle.installProducts()
        input(dialog(), 'Owner team').fill('Custody Technology')
        dialogButton('Add product').click()

        then:
        page.waitForURL('**/admin/products/3')
        assertThat(snackBar()).containsText('Trade Archive added. Now fill in its change template.')
        def request = awaitRequest('POST', '/api/products', 2)
        request.params() == [:]
        request.json() == [code        : 'TRADEARCHIVE', name: 'Trade Archive', ownerTeam: 'Custody Technology',
                           contactEmail: null, departmentId: 4, version: null]
        beadle.product(3).name == 'Trade Archive'
        assertThat(facts()).hasText(['TRADEARCHIVE', 'Custody', 'Custody Technology', '–'] as String[])
        assertThat(page.locator('section.defaults .banner.info')).containsText('Not saved yet')
        assertThat(page.locator('.defaults-header .chip')).hasText('Not filled in yet')
        assertThat(page.locator('dso-change-tasks-form .task-row')).hasCount(2)
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "an administrator changes the name and department of a product and is told when someone else changed it"() {
        when:
        open('/admin/products/1')
        buttonIn(factsCard(), 'Edit details').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Edit the details of CertScanner')
        assertThat(dialog().locator('dso-label')).hasText(['Product name', 'Department', 'Owner team', 'Contact e-mail'] as String[])
        hasValues(dialog(), ['Product name': 'CertScanner', 'Owner team': 'Technology Architecture', 'Contact e-mail': 'ta-team@bbh.com'])

        when:
        input(dialog(), 'Product name').fill('CertScanner Pro')
        choose(dialog(), 'Department', 'Fund Services')
        dialogButton('Save details').click()

        then:
        assertThat(snackBar()).containsText('CertScanner Pro saved')
        assertThat(facts()).hasText(['CERTSCANNER', 'Fund Services', 'Technology Architecture', 'ta-team@bbh.com'] as String[])
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [:]
        request.json() == [code        : null, name: 'CertScanner Pro', departmentId: 5, ownerTeam: 'Technology Architecture',
                           contactEmail: 'ta-team@bbh.com', version: 0]
        beadle.product(1).subMap('code', 'version', 'departmentName') == [code: 'CERTSCANNER', version: 1, departmentName: 'Fund Services']

        when:
        beadle.product(1).putAll(version: 4, ownerTeam: 'Security Engineering')
        buttonIn(factsCard(), 'Edit details').click()
        assertThat(input(dialog(), 'Product name')).isFocused()
        input(dialog(), 'Contact e-mail').fill('certs@bbh.com')
        dialogButton('Save details').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('CertScanner Pro was changed by someone else. Its latest version is shown now; make your change again.')
        assertThat(facts()).hasText(['CERTSCANNER', 'Fund Services', 'Security Engineering', 'ta-team@bbh.com'] as String[])
        awaitRequest('PUT', '/api/products/1', 2).json().version == 1

        when:
        buttonIn(factsCard(), 'Edit details').click()
        assertThat(input(dialog(), 'Product name')).isFocused()
        input(dialog(), 'Contact e-mail').fill('certs@bbh.com')
        dialogButton('Save details').click()

        then:
        assertThat(snackBar()).containsText('CertScanner Pro saved')
        awaitRequest('PUT', '/api/products/1', 3).json().subMap('version', 'ownerTeam', 'contactEmail') ==
                [version: 4, ownerTeam: 'Security Engineering', contactEmail: 'certs@bbh.com']
        assertThat(facts().last()).hasText('certs@bbh.com')
        api.requests('GET', '/api/products/1').size() == 3
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "an administrator deletes a product together with its change template"() {
        given:
        api.respond('DELETE', '/api/products/1', problem(503, 'Service Unavailable', NOT_REACHED))

        when:
        open('/admin/products/1')
        button('Delete product', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Delete the product CertScanner?')
        assertThat(dialog().locator('.message')).hasText('CertScanner and its change template are deleted. This cannot be undone.')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('DELETE', '/api/products/1').isEmpty()

        when:
        button('Delete product', true).click()
        dialogButton('Delete product').click()

        then:
        assertThat(snackBar()).containsText("CertScanner could not be deleted. $NOT_REACHED")
        awaitRequest('DELETE', '/api/products/1')
        page.url().endsWith('/admin/products/1')

        when:
        beadle.installProducts()
        button('Delete product', true).click()
        dialogButton('Delete product').click()

        then:
        page.waitForURL('**/admin/products')
        awaitRequest('DELETE', '/api/products/1', 2)
        assertThat(snackBar()).containsText('CertScanner deleted')
        assertThat(page.locator('h1')).hasText('Beadle Admin')
        beadle.product(1) == null
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    Locator cardNames() {
        page.locator('section.department h2')
    }

    Locator card(String name) {
        holding(page.locator('section.department'), "h2:text-is('${name}')")
    }

    Locator productRow(String name) {
        holding(gridRows(), "a.name:text-is('${name}')")
    }

    Locator factsCard() {
        page.locator('dso-product-admin .facts')
    }

    Locator facts() {
        factsCard().locator('dd')
    }
}
