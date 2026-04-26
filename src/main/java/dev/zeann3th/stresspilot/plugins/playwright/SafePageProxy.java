package dev.zeann3th.stresspilot.plugins.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.graalvm.polyglot.HostAccess;

import java.util.List;
import java.util.Set;

/**
 * An explicit, allowlisted wrapper around a Playwright Page.
 * Only the methods declared here are callable from sandboxed JS.
 */
public class SafePageProxy {

    private final Page page;

    private static final int MAX_TEXT_LENGTH = 50_000;
    private int navigationCount = 0;
    private static final int MAX_NAVIGATIONS = 20;

    public SafePageProxy(Page page) {
        this.page = page;
    }

    @HostAccess.Export
    public void gotoUrl(String url) {
        if (navigationCount >= MAX_NAVIGATIONS) {
            throw new SecurityException("Navigation limit reached (" + MAX_NAVIGATIONS + ")");
        }
        validateUrl(url);
        navigationCount++;
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
        return truncate(page.content());
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

    // -------------------------------------------------------------------------
    // Waiting
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public void waitForSelector(String selector) {
        locator(selector).waitFor();
    }

    @HostAccess.Export
    public void waitForTimeout(int milliseconds) {
        if (milliseconds < 0 || milliseconds > 10_000) {
            throw new SecurityException("waitForTimeout must be between 0 and 10000ms");
        }
        page.waitForTimeout(milliseconds);
    }

    @HostAccess.Export
    public void waitForLoadState(String state) {
        // state can be "load", "domcontentloaded", "networkidle"
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.valueOf(state.toUpperCase()));
    }

    @HostAccess.Export
    public String screenshot() {
        byte[] buffer = page.screenshot(new Page.ScreenshotOptions()
                .setType(com.microsoft.playwright.options.ScreenshotType.JPEG)
                .setQuality(70));
        return java.util.Base64.getEncoder().encodeToString(buffer);
    }

    // -------------------------------------------------------------------------
    // Internal Validation
    // -------------------------------------------------------------------------

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) throw new SecurityException("URL blank");
        String lower = url.toLowerCase().trim();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) {
            throw new SecurityException("URL scheme not allowed");
        }
    }

    private void validateSelector(String selector) {
        if (selector == null || selector.isBlank()) throw new SecurityException("Selector blank");
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
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
            if (value != null && value.length() > 10_000) throw new SecurityException("Input too long");
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
        public String textContent() {
            return truncate(locator.first().textContent());
        }

        @HostAccess.Export
        public String innerText() {
            return truncate(locator.first().innerText());
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

        private String truncate(String text) {
            if (text == null) return null;
            return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        }
    }
}
