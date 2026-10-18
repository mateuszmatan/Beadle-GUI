package com.bbh.itss.beadle.frontend.support

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
import static com.microsoft.playwright.options.LoadState.NETWORKIDLE
import static java.nio.file.Files.createDirectories

abstract class GuiSpecification extends Specification {

    static final int WIDTH = 1440
    static final int HEIGHT = 1000

    private static Playwright sharedPlaywright
    private static Browser sharedBrowser

    static synchronized Browser obtainBrowser() {
        if (sharedBrowser == null) {
            sharedPlaywright = Playwright.create()
            def options = new BrowserType.LaunchOptions().setHeadless(!Boolean.getBoolean('frontend.headed'))
            def executable = System.getProperty('frontend.browser')
            if (executable) {
                options.setExecutablePath(Paths.get(executable))
            }
            sharedBrowser = sharedPlaywright.chromium().launch(options)
            Runtime.runtime.addShutdownHook(new Thread({
                try { sharedBrowser?.close() } catch (Throwable ignored) {}
                try { sharedPlaywright?.close() } catch (Throwable ignored) {}
            }))
        }
        sharedBrowser
    }

    @Shared
    Browser browser

    @Shared
    GuiServer server

    @Shared
    StubApi api = newApi()

    BrowserContext context
    Page page
    List<String> consoleErrors = []
    List<String> failedRequests = []

    abstract StubApi newApi()

    def setupSpec() {
        if (!remoteBaseUrl()) {
            server = GuiServer.start(distDir(), api)
        }
        browser = obtainBrowser()
    }

    def setup() {
        api.reset()
        newPage()
    }

    def cleanup() {
        if (page && Boolean.getBoolean('frontend.screenshots')) {
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

    Locator holding(Locator scope, String selector) {
        scope.filter(new Locator.FilterOptions().setHas(page.locator(selector)))
    }

    Locator holdingText(Locator scope, String text) {
        scope.filter(new Locator.FilterOptions().setHasText(text))
    }

    Locator link(String name, boolean exact = false) {
        page.getByRole(LINK, new Page.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator menuLinks() {
        page.locator('nav.menu a')
    }

    Locator menuLink(String label) {
        page.locator('nav.menu').getByRole(LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    Locator activeMenuLink() {
        page.locator('nav.menu a.active')
    }

    void hasValues(Locator scope, Map<String, String> expected) {
        expected.each { label, value -> assertThat(input(scope, label)).hasValue(value) }
    }

    void fillIn(Locator scope, Map<String, String> values) {
        values.each { label, value -> input(scope, label).fill(value) }
    }

    void hasErrors(Locator scope, Map<String, String> expected) {
        expected.each { label, message -> assertThat(errorOf(scope, label)).hasText(message) }
    }

    Locator hintOf(Locator scope, String label) {
        formField(scope, label).locator('beadle-hint')
    }

    Locator selected(Locator scope, String label) {
        select(scope, label).locator('option:checked')
    }

    Locator saveError() {
        page.locator('.save-bar .save-error')
    }

    Locator tab(String label) {
        page.locator('nav.tab-bar').getByRole(LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    Locator input(Locator scope, String label) {
        scope.getByLabel(label, new Locator.GetByLabelOptions().setExact(true))
    }

    Locator formField(Locator scope, String label) {
        holding(scope.locator('beadle-form-field'), "beadle-label:text-is('${label}')")
    }

    Locator errorOf(Locator scope, String label) {
        formField(scope, label).locator('beadle-error')
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

    Locator dialog() {
        page.locator('.cdk-dialog-container')
    }

    Locator snackBar() {
        page.locator('beadle-toast').last()
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
        consoleErrors.findAll { !it.contains('localhost:3000') && !it.contains('grafana') } +
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
