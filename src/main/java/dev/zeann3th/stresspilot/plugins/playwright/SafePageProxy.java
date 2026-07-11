package dev.zeann3th.stresspilot.plugins.playwright;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.graalvm.polyglot.HostAccess;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ScreenshotType;

/**
 * An explicit, allowlisted wrapper around a Playwright Page.
 * Only the methods declared here are callable from sandboxed JS.
 */
public class SafePageProxy {

    private final Page page;

    private static final DateTimeFormatter SCREENSHOT_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss-SSS");

    public SafePageProxy(Page page) {
        this.page = page;
    }

    @HostAccess.Export
    public void gotoUrl(String url) {
        page.navigate(url);
    }

    @HostAccess.Export
    public String url() {
        return page.url();
    }

    @HostAccess.Export
    public String title() {
        return page.title();
    }

    @HostAccess.Export
    public void reload() {
        page.reload();
    }

    @HostAccess.Export
    public void goBack() {
        page.goBack();
    }

    @HostAccess.Export
    public void goForward() {
        page.goForward();
    }

    // -------------------------------------------------------------------------
    // Locators
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public SafeLocatorProxy locator(String selector) {
        validateSelector(selector);
        return new SafeLocatorProxy(page.locator(selector));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByRole(String role, String name) {
        AriaRole ariaRole = AriaRole.valueOf(role.toUpperCase());
        return new SafeLocatorProxy(page.getByRole(ariaRole, new Page.GetByRoleOptions().setName(name)));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByText(String text) {
        return new SafeLocatorProxy(page.getByText(text));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByLabel(String text) {
        return new SafeLocatorProxy(page.getByLabel(text));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByPlaceholder(String text) {
        return new SafeLocatorProxy(page.getByPlaceholder(text));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByAltText(String text) {
        return new SafeLocatorProxy(page.getByAltText(text));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByTitle(String text) {
        return new SafeLocatorProxy(page.getByTitle(text));
    }

    @HostAccess.Export
    public SafeLocatorProxy getByTestId(String testId) {
        return new SafeLocatorProxy(page.getByTestId(testId));
    }

    // -------------------------------------------------------------------------
    // Legacy / Convenience methods (directly on page)
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public String textContent(String selector) {
        return locator(selector).textContent();
    }

    @HostAccess.Export
    public String innerText(String selector) {
        return locator(selector).innerText();
    }

    @HostAccess.Export
    public String content() {
        return page.content();
    }

    @HostAccess.Export
    public String getAttribute(String selector, String attribute) {
        return locator(selector).getAttribute(attribute);
    }

    @HostAccess.Export
    public boolean isVisible(String selector) {
        return locator(selector).isVisible();
    }

    @HostAccess.Export
    public boolean exists(String selector) {
        validateSelector(selector);
        return page.locator(selector).count() > 0;
    }

    @HostAccess.Export
    public void click(String selector) {
        locator(selector).click();
    }

    @HostAccess.Export
    public void fill(String selector, String value) {
        locator(selector).fill(value);
    }

    @HostAccess.Export
    public void press(String selector, String key) {
        locator(selector).press(key);
    }

    @HostAccess.Export
    public void hover(String selector) {
        locator(selector).hover();
    }

    @HostAccess.Export
    public void dblclick(String selector) {
        locator(selector).dblclick();
    }

    @HostAccess.Export
    public void check(String selector) {
        locator(selector).check();
    }

    @HostAccess.Export
    public void uncheck(String selector) {
        locator(selector).uncheck();
    }

    @HostAccess.Export
    public void selectOption(String selector, String value) {
        locator(selector).selectOption(value);
    }

    // -------------------------------------------------------------------------
    // Waiting
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public void waitForSelector(String selector) {
        locator(selector).waitFor();
    }

    @HostAccess.Export
    public void waitForTimeout(int milliseconds) {
        page.waitForTimeout(milliseconds);
    }

    @HostAccess.Export
    public void waitForLoadState(String state) {
        // state can be "load", "domcontentloaded", "networkidle"
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.valueOf(state.toUpperCase()));
    }

    @HostAccess.Export
    public String screenshot(boolean fullPage, String type, int quality, String name) {
        return saveScreenshot(page, fullPage, type, quality, name);
    }

    static String saveScreenshot(Page page, boolean fullPage, String type, int quality, String name) {
        ScreenshotType screenshotType = "png".equalsIgnoreCase(type) ? ScreenshotType.PNG : ScreenshotType.JPEG;
        Path output = nextScreenshotPath(screenshotType, name);
        Page.ScreenshotOptions options = new Page.ScreenshotOptions()
                .setFullPage(fullPage)
                .setType(screenshotType)
                .setPath(output);
        if (screenshotType == ScreenshotType.JPEG) {
            options.setQuality(Math.max(0, Math.min(100, quality)));
        }
        page.screenshot(options);
        return output.toString();
    }

    private static Path nextScreenshotPath(ScreenshotType type, String name) {
        Path directory = Path.of(System.getProperty("user.home"), "Pictures", "StressPilot");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create screenshot directory " + directory, e);
        }

        String label = name == null || name.isBlank() ? "screenshot" : name;
        label = label.replaceAll("[^a-zA-Z0-9._-]", "_").replaceAll("^\\.+", "");
        if (label.isBlank()) label = "screenshot";
        String extension = type == ScreenshotType.PNG ? "png" : "jpg";
        String timestamp = SCREENSHOT_TIMESTAMP.format(LocalDateTime.now());
        String unique = UUID.randomUUID().toString().substring(0, 8).toLowerCase(Locale.ROOT);
        return directory.resolve(label + "_" + timestamp + "_" + unique + "." + extension).toAbsolutePath();
    }

    // -------------------------------------------------------------------------
    // Internal Validation
    // -------------------------------------------------------------------------

    private void validateSelector(String selector) {
        if (selector == null || selector.isBlank()) throw new SecurityException("Selector blank");
    }

    /**
     * Inner class to proxy Locator methods.
     */
    public static class SafeLocatorProxy {
        private final Locator locator;

        public SafeLocatorProxy(Locator locator) {
            this.locator = locator;
        }

        @HostAccess.Export
        public void click() {
            locator.first().click();
        }

        @HostAccess.Export
        public void dblclick() {
            locator.first().dblclick();
        }

        @HostAccess.Export
        public void hover() {
            locator.first().hover();
        }

        @HostAccess.Export
        public void fill(String value) {
            locator.first().fill(value);
        }

        @HostAccess.Export
        public void press(String key) {
            locator.first().press(key);
        }

        @HostAccess.Export
        public void check() {
            locator.first().check();
        }

        @HostAccess.Export
        public void uncheck() {
            locator.first().uncheck();
        }

        @HostAccess.Export
        public void selectOption(String value) {
            locator.first().selectOption(value);
        }

        @HostAccess.Export
        public boolean isVisible() {
            return locator.first().isVisible();
        }

        @HostAccess.Export
        public boolean isEnabled() {
            return locator.first().isEnabled();
        }

        @HostAccess.Export
        public boolean isChecked() {
            return locator.first().isChecked();
        }

        @HostAccess.Export
        public String inputValue() {
            return locator.first().inputValue();
        }

        @HostAccess.Export
        public String textContent() {
            return locator.first().textContent();
        }

        @HostAccess.Export
        public String innerText() {
            return locator.first().innerText();
        }

        @HostAccess.Export
        public String getAttribute(String name) {
            return locator.first().getAttribute(name);
        }

        @HostAccess.Export
        public void waitFor() {
            locator.first().waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        }

        @HostAccess.Export
        public int count() {
            return locator.count();
        }

        @HostAccess.Export
        public SafeLocatorProxy first() {
            return new SafeLocatorProxy(locator.first());
        }

        @HostAccess.Export
        public SafeLocatorProxy last() {
            return new SafeLocatorProxy(locator.last());
        }

        @HostAccess.Export
        public SafeLocatorProxy nth(int index) {
            return new SafeLocatorProxy(locator.nth(index));
        }

        @HostAccess.Export
        public SafeLocatorProxy locator(String selector) {
            if (selector == null || selector.isBlank()) throw new SecurityException("Selector blank");
            return new SafeLocatorProxy(locator.locator(selector));
        }

        @HostAccess.Export
        public SafeLocatorProxy getByText(String text) {
            return new SafeLocatorProxy(locator.getByText(text));
        }

        @HostAccess.Export
        public SafeLocatorProxy getByLabel(String text) {
            return new SafeLocatorProxy(locator.getByLabel(text));
        }

        @HostAccess.Export
        public SafeLocatorProxy getByPlaceholder(String text) {
            return new SafeLocatorProxy(locator.getByPlaceholder(text));
        }

        @HostAccess.Export
        public SafeLocatorProxy getByTestId(String testId) {
            return new SafeLocatorProxy(locator.getByTestId(testId));
        }

    }
}
