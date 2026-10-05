package com.bbh.itss.dso.portal.gui.support

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.LoadState
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer
import java.util.function.Predicate

abstract class GuiSpecification extends Specification {

    static final int WIDTH = 1440
    static final int HEIGHT = 1000

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
    StubApi api = StubApi.withDemoData()

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
        context = newContext()
        page = context.newPage()
        watchErrors(page)
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

    Locator link(String name, boolean exact = false) {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(name).setExact(exact))
    }

    Locator menuLink(String label) {
        page.locator('nav.menu').getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(label).setExact(true))
    }

    Locator field(String label) {
        page.getByLabel(label, new Page.GetByLabelOptions().setExact(true))
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

    static double performanceFactor() {
        (System.getProperty('performance.factor') ?: '1') as double
    }
}
