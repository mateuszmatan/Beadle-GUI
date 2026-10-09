package com.bbh.itss.dso.portal.frontend.support

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.SelectOption
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Predicate

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import static com.microsoft.playwright.options.AriaRole.BUTTON
import static com.microsoft.playwright.options.AriaRole.CHECKBOX
import static com.microsoft.playwright.options.AriaRole.COMBOBOX
import static com.microsoft.playwright.options.AriaRole.LINK
import static com.microsoft.playwright.options.AriaRole.MENUITEM
import static com.microsoft.playwright.options.AriaRole.RADIO
import static com.microsoft.playwright.options.LoadState.NETWORKIDLE
import static java.nio.file.Files.createDirectories

abstract class GuiSpecification extends Specification {

    static final int WIDTH = 1440
    static final int HEIGHT = 1000

    static final Map<String, List<String>> MENUS = [
            'Beadle'              : ['Changes', 'New Change', 'Admin'],
            'DevSecOps Management': ['Pipelines', 'Self-service', 'Pipeline Monitoring', 'Change Evidence', 'Admin']]

    static final String CLIPBOARD_RECORDER = '''
        window.dsoCopiedTexts = [];
        const execCommand = Document.prototype.execCommand;
        Document.prototype.execCommand = function (command, ...rest) {
          if (String(command).toLowerCase() !== 'copy') {
            return execCommand.call(this, command, ...rest);
          }
          const active = this.activeElement;
          const text = active && typeof active.value === 'string'
            ? active.value.substring(active.selectionStart ?? 0, active.selectionEnd ?? active.value.length)
            : String(this.getSelection());
          window.dsoCopiedTexts.push(text);
          return true;
        };
    '''

    @Shared
    Playwright playwright

    @Shared
    Browser browser

    @Shared
    GuiServer server

    @Shared
    StubApi api = new StubApi()

    BrowserContext context
    Page page
    List<String> consoleErrors = []
    List<String> failedRequests = []

    def setupSpec() {
        if (!remoteBaseUrl()) {
            server = GuiServer.start(distDir(), api)
        }
        playwright = Playwright.create()
        def options = new BrowserType.LaunchOptions().setHeadless(!Boolean.getBoolean('frontend.headed'))
        def executable = System.getProperty('frontend.browser')
        if (executable) {
            options.setExecutablePath(Paths.get(executable))
        }
        browser = playwright.chromium().launch(options)
    }

    def setup() {
        api.reset()
        newPage()
    }

    def cleanup() {
        if (page) {
            try {
                def target = reportsDir().resolve('screenshots').resolve(getClass().simpleName)
                createDirectories(target)
                def name = specificationContext.currentIteration.displayName.replaceAll('[^A-Za-z0-9._-]+', '_')
                page.screenshot(new Page.ScreenshotOptions().setPath(target.resolve(name.take(120) + '.png')).setFullPage(true))
            } catch (Exception ignored) {
            }
        }
        context?.close()
    }

    def cleanupSpec() {
        browser?.close()
        playwright?.close()
        server?.close()
    }

    void newPage() {
        context?.close()
        context = newContext()
        page = context.newPage()
        watchErrors(page)
    }

    BrowserContext newContext() {
        def created = browser.newContext(contextOptions())
        if (!remoteBaseUrl()) {
            isolateFromOtherHosts(created)
        }
        created
    }

    Browser.NewContextOptions contextOptions() {
        new Browser.NewContextOptions()
                .setViewportSize(viewportWidth(), viewportHeight())
                .setLocale('en-US')
                .setTimezoneId('UTC')
    }

    void watchErrors(Page target) {
        target.onConsoleMessage { message ->
            if (message.type() == 'error') {
                consoleErrors << message.text()
            }
        }
        target.onPageError { error -> consoleErrors << String.valueOf(error) }
        target.onRequestFailed { request ->
            if (request.failure() != 'net::ERR_ABORTED') {
                failedRequests << "${request.method()} ${request.url()} ${request.failure()}".toString()
            }
        }
    }

    void isolateFromOtherHosts(BrowserContext target) {
        def origin = server.url
        target.route({ String address -> !address.startsWith(origin) } as Predicate<String>, { Route route ->
            route.fulfill(new Route.FulfillOptions().setStatus(200).setContentType('text/html').setBody(''))
        } as Consumer<Route>)
    }

    int viewportWidth() {
        WIDTH
    }

    int viewportHeight() {
        HEIGHT
    }

    String baseUrl() {
        remoteBaseUrl() ?: server.url
    }

    String url(String path) {
        baseUrl().replaceAll('/+$', '') + path
    }

    Page open(String path) {
        page.navigate(url(path))
        page.waitForLoadState(NETWORKIDLE)
        page
    }

    Locator button(String name, boolean exact = false) {
        page.getByRole(BUTTON, new Page.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator buttonIn(Locator scope, String name, boolean exact = true) {
        scope.getByRole(BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator dialogButton(String name) {
        buttonIn(dialog(), name)
    }

    Locator stat(String label) {
        holding(page.locator('.stats .stat'), "span:text-is('${label}')").locator('strong')
    }

    Locator holding(Locator scope, String selector) {
        scope.filter(new Locator.FilterOptions().setHas(page.locator(selector)))
    }

    Locator holdingText(Locator scope, String text) {
        scope.filter(new Locator.FilterOptions().setHasText(text))
    }

    Locator link(String name, boolean exact = false) {
        page.getByRole(LINK, new Page.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator menuButton(String name) {
        page.locator('nav.menu').getByRole(BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true))
    }

    Locator menuLink(String label) {
        menuLink(MENUS.find { it.value.contains(label) }.key, label)
    }

    Locator menuLink(String menu, String label) {
        menuButton(menu).click()
        page.locator('.dso-menu').getByRole(MENUITEM, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    Locator tab(String label) {
        page.locator('nav.tab-bar').getByRole(LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    Locator field(String label) {
        page.getByLabel(label, new Page.GetByLabelOptions().setExact(true))
    }

    Locator input(Locator scope, String label) {
        scope.getByLabel(label, new Locator.GetByLabelOptions().setExact(true))
    }

    Locator formField(Locator scope, String label) {
        holding(scope.locator('dso-form-field'), "dso-label:text-is('${label}')")
    }

    Locator errorOf(Locator scope, String label) {
        formField(scope, label).locator('dso-error')
    }

    Locator select(Locator scope, String label) {
        scope.getByRole(COMBOBOX, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    void choose(Locator scope, String label, String option) {
        select(scope, label).selectOption(new SelectOption().setLabel(option))
    }

    List<String> optionsOf(Locator scope, String label) {
        select(scope, label).locator('option').allTextContents()*.trim()
    }

    Locator checkbox(Locator scope, String label) {
        scope.getByRole(CHECKBOX, new Locator.GetByRoleOptions().setName(label))
    }

    Locator radio(Locator scope, String name) {
        scope.getByRole(RADIO, new Locator.GetByRoleOptions().setName(name).setExact(true))
    }

    Locator dialog() {
        page.locator('.cdk-dialog-container')
    }

    Locator snackBar() {
        page.locator('dso-toast').last()
    }

    Locator gridRows(Locator scope = page.locator('body')) {
        scope.locator('.ag-center-cols-container .ag-row')
    }

    Locator gridRow(Locator scope, String text) {
        holdingText(gridRows(scope), text)
    }

    Locator gridCells(Locator scope, String column) {
        gridRows(scope).locator(".ag-cell[col-id='${column}']")
    }

    Locator gridCell(Locator row, String column) {
        row.locator(".ag-cell[col-id='${column}']")
    }

    Locator gridHeaders(Locator scope = page.locator('body')) {
        scope.locator('.ag-header-row-column .ag-header-cell')
    }

    void sortBy(String column, Locator scope = page.locator('body')) {
        scope.locator(".ag-header-cell[col-id='${column}'] .ag-header-cell-label").click()
    }

    Locator gridFilter(String label, Locator scope = page.locator('body')) {
        scope.locator(".ag-floating-filter [aria-label='Filter by ${label}']")
    }

    void recordClipboard() {
        context.addInitScript(CLIPBOARD_RECORDER)
    }

    List<String> copiedTexts() {
        page.evaluate('() => window.dsoCopiedTexts ?? []') as List<String>
    }

    RecordedRequest awaitRequest(String method, String pathPattern, int count = 1) {
        try {
            page.waitForCondition({ api.requests(method, pathPattern).size() >= count } as BooleanSupplier)
        } catch (TimeoutError ignored) {
            throw new AssertionError("Expected $count $method request(s) to $pathPattern, got " +
                    "${api.requests(method, pathPattern).size()}; the gui sent " +
                    "${api.requests().collect { "$it.method $it.path" }}")
        }
        api.requests(method, pathPattern)[count - 1]
    }

    List<String> ownErrors() {
        def origin = baseUrl()
        consoleErrors.findAll { !it.contains('localhost:3000') && !it.contains('grafana') && !it.startsWith('*') } +
                failedRequests.findAll { it.contains(origin) }
    }

    static String remoteBaseUrl() {
        System.getProperty('smoke.baseUrl') ?: null
    }

    static Path distDir() {
        Paths.get(System.getProperty('frontend.dist', 'dist'))
    }

    static Path reportsDir() {
        Paths.get(System.getProperty('frontend.reports', 'build/reports/frontend'))
    }
}
