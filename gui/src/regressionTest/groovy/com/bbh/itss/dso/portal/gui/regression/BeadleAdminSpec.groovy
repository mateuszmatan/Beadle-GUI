package com.bbh.itss.dso.portal.gui.regression

import com.bbh.itss.dso.portal.gui.support.ProductStore
import com.microsoft.playwright.Locator

import java.time.Instant

import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.CERT_TASKS
import static com.bbh.itss.dso.portal.gui.support.ChangeStubs.CERT_TEMPLATE
import static com.bbh.itss.dso.portal.gui.support.StubApi.fixture
import static com.bbh.itss.dso.portal.gui.support.StubResponse.empty
import static com.bbh.itss.dso.portal.gui.support.StubResponse.problem
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static java.time.temporal.ChronoUnit.DAYS

class BeadleAdminSpec extends EditorSpecification {

    static final List<String> DEPARTMENTS = ['AI Lab', 'Capital Partners', 'Corporate Technology', 'Custody', 'Fund Services']
    static final String APP_SCAN_KEY = 'bbh_7d1e2f3a-0000-4abc-9def-123456789abc'

    def setup() {
        api.get('/api/change-profiles') {
            [[productId: 1, productName: 'CertScanner', version: 2, updatedAt: Instant.now().minus(2, DAYS).toString()]]
        }
    }

    def "Beadle Admin has a Departments and a Products tab, and its departments have no pipeline or service columns"() {
        when:
        open('/beadle')
        page.waitForURL('**/beadle/changes')
        menuLink('Beadle', 'Admin').click()
        page.waitForURL('**/beadle/admin/products')

        then:
        assertThat(page.locator('h1')).hasText('Beadle Admin')
        assertThat(page.locator('nav.tab-bar a')).hasText(['Departments', 'Products'] as String[])
        assertThat(page.locator('nav.tab-bar a.active')).hasText('Products')

        when:
        link('Departments', true).click()
        page.waitForURL('**/beadle/admin/departments')

        then:
        assertThat(page.locator('th.mat-mdc-header-cell')).hasText(['Department', 'Products', ''] as String[])
        assertThat(page.locator('section.chart')).hasCount(0)
        assertThat(page.locator('.toolbar .count')).hasText('5 departments · 2 products')

        when:
        button('Add department', true).click()
        input(dialog(), 'Name').fill('Treasury')
        dialogButton('Save').click()

        then:
        assertThat(snackBar()).containsText('Treasury added')
        awaitRequest('POST', '/api/departments').json() == [name: 'Treasury']
        assertThat(page.locator('td.name')).hasText((DEPARTMENTS + 'Treasury') as String[])
        ownErrors().isEmpty()
    }

    def "the Products tab lists the products by department with the state of their change template"() {
        when:
        open('/beadle/admin/products')

        then:
        assertThat(cardNames()).hasText(DEPARTMENTS as String[])
        assertThat(card('Corporate Technology').locator('.tally')).hasText('1 product')
        assertThat(card('Custody').locator('.no-products')).hasText('No products in Custody yet.')
        assertThat(page.locator('.toolbar .count')).hasText('2 products in 5 departments')
        assertThat(card('Fund Services').locator('th')).hasText(['Product', 'Owner team', 'Change template'] as String[])
        assertThat(productRow('CertScanner').locator('td')).hasText(['CertScannerCERTSCANNER', 'Technology Architecture',
                                                                    'Saved · 2 days ago'] as String[])
        assertThat(productRow('Payments Hub').locator('.mat-column-template')).hasText('Suggested values')

        when:
        page.locator('input[aria-label="Search products"]').fill('pay')

        then:
        assertThat(cardNames()).hasText(['Fund Services'] as String[])
        assertThat(page.locator('.toolbar .count')).hasText('1 product in 1 department')

        when:
        api.respond('GET', '/api/change-profiles', problem(503, 'Unavailable', 'The change templates are not available'))
        open('/beadle/admin/products')

        then:
        assertThat(productRow('CertScanner').locator('.mat-column-template')).hasText('Unknown')
        assertThat(productRow('CertScanner').locator('.mat-column-template span')).hasAttribute('title', 'The change templates are not available')

        when:
        productRow('Payments Hub').locator('td.mat-column-ownerTeam').click()

        then:
        page.waitForURL('**/beadle/admin/products/2')
        assertThat(facts()).hasText(['PAYHUB', 'Fund Services', 'Payments Engineering', 'payments-eng@bbh.com'] as String[])
        assertThat(page.locator('section.defaults h2')).hasText('Change template')
        assertThat(page.locator('dso-product-admin table')).hasCount(0)
        assertThat(page.getByText('mobile-app')).hasCount(0)
        ownErrors().findAll { !it.contains('503') }.isEmpty()
    }

    def "an administrator adds a product to a department and lands on its page"() {
        given:
        api.respond('POST', '/api/products', problem(400, 'Bad Request', 'The portal did not accept some values.',
                [errors: [[field: 'appScan.keyId', message: 'is not an AppScan API key ID']]]))
        api.get('/api/products/3/change-profile') {
            [productId: 3, productName: 'Trade Archive', version: null, updatedAt: null, template: CERT_TEMPLATE, tasks: CERT_TASKS]
        }

        when:
        open('/beadle/admin/products')
        buttonIn(card('Custody'), 'Add product').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Add product')
        assertThat(select(dialog(), 'Department')).hasText('Custody')

        when:
        dialogButton('Add product').click()

        then:
        hasErrors(dialog(), ['Product name': 'Required', 'Code': 'Required', 'AppScan API key ID': 'Required'])
        api.requests('POST', '/api/products').isEmpty()

        when:
        input(dialog(), 'Product name').fill('Trade Archive')

        then:
        assertThat(input(dialog(), 'Code')).hasValue('TRADEARCHIVE')

        when:
        fillIn(dialog(), ['Owner team': 'Custody Technology', 'AppScan API key ID': 'not-a-key'])
        dialogButton('Add product').click()

        then:
        assertThat(errorOf(dialog(), 'AppScan API key ID')).hasText('is not an AppScan API key ID')
        assertThat(dialog().locator('[role=alert]')).hasCount(0)

        when:
        def store = ProductStore.created(api, 3)
        input(dialog(), 'AppScan API key ID').fill(APP_SCAN_KEY)
        dialogButton('Add product').click()

        then:
        page.waitForURL('**/beadle/admin/products/3')
        assertThat(snackBar()).containsText('Trade Archive added')
        def request = awaitRequest('POST', '/api/products', 2)
        request.params() == [:]
        request.json() == [code        : 'TRADEARCHIVE', name: 'Trade Archive', description: null,
                           ownerTeam   : 'Custody Technology', contactEmail: null, departmentId: 4,
                           appScan     : [keyId: APP_SCAN_KEY, secretCredentialsId: null], version: null, services: []]
        store.product.name == 'Trade Archive'
        assertThat(facts()).hasText(['TRADEARCHIVE', 'Custody', 'Custody Technology', '–'] as String[])
        assertThat(page.locator('section.defaults .banner.info')).containsText('Not saved yet')
        assertThat(page.locator('dso-change-tasks-form .task-row')).hasCount(2)
        ownErrors().findAll { !it.contains('400') }.isEmpty()
    }

    def "an administrator changes the name and department of a product and is told when someone else changed it"() {
        given:
        def store = ProductStore.recorded(api, 1)
        def services = fixture('product-1.json').services

        when:
        open('/beadle/admin/products/1')
        buttonIn(factsCard(), 'Change').click()

        then:
        assertThat(dialog().locator('h2')).hasText('Change CertScanner')
        assertThat(dialog().locator('mat-label')).hasText(['Product name', 'Department', 'Owner team', 'Contact e-mail'] as String[])
        hasValues(dialog(), ['Product name': 'CertScanner', 'Owner team': 'Technology Architecture', 'Contact e-mail': 'ta-team@bbh.com'])

        when:
        input(dialog(), 'Product name').fill('CertScanner Pro')
        choose(dialog(), 'Department', 'Fund Services')
        dialogButton('Save').click()

        then:
        assertThat(snackBar()).containsText('CertScanner Pro saved')
        assertThat(facts()).hasText(['CERTSCANNER', 'Fund Services', 'Technology Architecture', 'ta-team@bbh.com'] as String[])
        def request = awaitRequest('PUT', '/api/products/1')
        request.params() == [:]
        with(request.json() as Map) {
            code == 'CERTSCANNER'
            name == 'CertScanner Pro'
            departmentId == 5
            version == 0
            appScan == fixture('product-1.json').appScan
            it.services == services
        }
        store.product.version == 1

        when:
        store.product.version = 4
        store.product.ownerTeam = 'Security Engineering'
        buttonIn(factsCard(), 'Change').click()
        assertThat(input(dialog(), 'Product name')).isFocused()
        input(dialog(), 'Contact e-mail').fill('certs@bbh.com')
        dialogButton('Save').click()

        then:
        assertThat(dialog()).hasCount(0)
        assertThat(snackBar()).containsText('CertScanner Pro was changed by someone else. Its latest version is shown now; make your change again.')
        assertThat(facts()).hasText(['CERTSCANNER', 'Fund Services', 'Security Engineering', 'ta-team@bbh.com'] as String[])
        awaitRequest('PUT', '/api/products/1', 2).json().version == 1

        when:
        buttonIn(factsCard(), 'Change').click()
        assertThat(input(dialog(), 'Product name')).isFocused()
        input(dialog(), 'Contact e-mail').fill('certs@bbh.com')
        dialogButton('Save').click()

        then:
        assertThat(snackBar()).containsText('CertScanner Pro saved')
        awaitRequest('PUT', '/api/products/1', 3).json().subMap('version', 'ownerTeam', 'contactEmail') ==
                [version: 4, ownerTeam: 'Security Engineering', contactEmail: 'certs@bbh.com']
        assertThat(facts().last()).hasText('certs@bbh.com')
        ownErrors().findAll { !it.contains('409') }.isEmpty()
    }

    def "an administrator deletes a product with its change template after confirming it"() {
        given:
        ProductStore.recorded(api, 1)
        api.on('DELETE', '/api/products/1') { empty() }

        when:
        open('/beadle/admin/products/1')
        button('Delete product', true).click()

        then:
        assertThat(dialog().locator('h2')).hasText('Delete CertScanner?')
        assertThat(dialog().locator('.message')).hasText('CertScanner is deleted with its change template and its DevSecOps ' +
                'pipelines and keys. Jenkins jobs using those keys stop working. This cannot be undone.')

        when:
        dialogButton('Cancel').click()

        then:
        assertThat(dialog()).hasCount(0)
        api.requests('DELETE', '/api/products/1').isEmpty()

        when:
        button('Delete product', true).click()
        dialogButton('Delete product').click()

        then:
        page.waitForURL('**/beadle/admin/products')
        awaitRequest('DELETE', '/api/products/1')
        assertThat(snackBar()).containsText('CertScanner deleted')
        assertThat(page.locator('h1')).hasText('Beadle Admin')
        ownErrors().isEmpty()
    }

    Locator cardNames() {
        page.locator('section.department h2')
    }

    Locator card(String name) {
        holding(page.locator('section.department'), "h2:text-is('${name}')")
    }

    Locator productRow(String name) {
        holding(page.locator('tr.mat-mdc-row'), "a.name:text-is('${name}')")
    }

    Locator factsCard() {
        page.locator('dso-product-admin .facts')
    }

    Locator facts() {
        factsCard().locator('dd')
    }
}
