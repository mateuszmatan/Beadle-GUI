package com.bbh.itss.dso.portal.gui.support

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import com.microsoft.playwright.TimeoutError
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.LoadState
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Predicate

abstract class GuiSpecification extends Specification {

    static final int WIDTH = 1440
    static final int HEIGHT = 1000

    static final Map<String, List<String>> MENUS = [
            'Beadle'              : ['Overview', 'Product Onboarding', 'Production Change'],
            'DevSecOps Management': ['Product Management', 'Pipeline Monitoring', 'Change Evidence', 'Global Settings']]

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
        def options = new BrowserType.LaunchOptions().setHeadless(!Boolean.getBoolean('gui.headed'))
        def executable = System.getProperty('gui.browser')
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
        if (page != null) {
            try {
                def target = reportsDir().resolve('screenshots').resolve(getClass().simpleName)
                Files.createDirectories(target)
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
        page.waitForLoadState(LoadState.NETWORKIDLE)
        page
    }

    Locator button(String name, boolean exact = false) {
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator buttonIn(Locator scope, String name, boolean exact = true) {
        scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(exact))
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
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator menuButton(String name) {
        page.locator('nav.menu').getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(name).setExact(true))
    }

    Locator menuLink(String label) {
        menuButton(MENUS.find { it.value.contains(label) }.key).click()
        page.locator('.mat-mdc-menu-panel').getByRole(AriaRole.MENUITEM, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    Locator field(String label) {
        page.getByLabel(label, new Page.GetByLabelOptions().setExact(true))
    }

    Locator input(Locator scope, String label) {
        scope.getByLabel(label, new Locator.GetByLabelOptions().setExact(true))
    }

    Locator formField(Locator scope, String label) {
        holding(scope.locator('mat-form-field'), "mat-label:text-is('${label}')")
    }

    Locator errorOf(Locator scope, String label) {
        formField(scope, label).locator('mat-error')
    }

    Locator select(Locator scope, String label) {
        scope.getByRole(AriaRole.COMBOBOX, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    void choose(Locator scope, String label, String option) {
        select(scope, label).click()
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(option).setExact(true)).click()
    }

    Locator checkbox(Locator scope, String label) {
        scope.getByRole(AriaRole.CHECKBOX, new Locator.GetByRoleOptions().setName(label))
    }

    Locator radio(Locator scope, String name) {
        scope.getByRole(AriaRole.RADIO, new Locator.GetByRoleOptions().setName(name).setExact(true))
    }

    Locator dialog() {
        page.locator('mat-dialog-container')
    }

    Locator snackBar() {
        page.locator('mat-snack-bar-container:not(.mat-snack-bar-container-exit)').last()
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
        consoleErrors.findAll { !it.contains('localhost:3000') && !it.contains('grafana') } +
                failedRequests.findAll { it.contains(origin) }
    }

    static String remoteBaseUrl() {
        System.getProperty('smoke.baseUrl') ?: null
    }

    static Path distDir() {
        Paths.get(System.getProperty('gui.dist', 'build/dist/browser'))
    }

    static Path reportsDir() {
        Paths.get(System.getProperty('gui.reports', 'build/reports/gui'))
    }
}
