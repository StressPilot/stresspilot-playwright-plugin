package dev.zeann3th.stresspilot.plugins.playwright;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.graalvm.polyglot.HostAccess;

import java.util.Set;

/**
 * An explicit, allowlisted wrapper around a Playwright Page.
 * Only the methods declared here are callable from sandboxed JS.
 * The real Page object is NEVER injected directly — only this proxy is.
 */
public class SafePageProxy {

    private final Page page;

    // Hard cap on how many chars of text we'll return to the script.
    // Prevents exfiltration of massive page dumps that could spike memory.
    private static final int MAX_TEXT_LENGTH = 50_000;

    // Tracks how many navigations this script has done.
    // Protects against redirect-chasing loops or abuse.
    private int navigationCount = 0;
    private static final int MAX_NAVIGATIONS = 20;

    public SafePageProxy(Page page) {
        this.page = page;
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    // 'goto' is a Java keyword, so we name the method gotoUrl but export it as "goto" for JS.
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

    // -------------------------------------------------------------------------
    // Reading page content
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public String textContent(String selector) {
        validateSelector(selector);
        String text = page.locator(selector).first().textContent();
        return truncate(text);
    }

    @HostAccess.Export
    public String innerText(String selector) {
        validateSelector(selector);
        String text = page.locator(selector).first().innerText();
        return truncate(text);
    }

    @HostAccess.Export
    public String content() {
        String text = page.content(); // Returns full HTML
        return truncate(text);
    }

    @HostAccess.Export
    public String getAttribute(String selector, String attribute) {
        validateSelector(selector);
        validateAttributeName(attribute);
        return page.locator(selector).first().getAttribute(attribute);
    }

    @HostAccess.Export
    public boolean isVisible(String selector) {
        validateSelector(selector);
        return page.locator(selector).first().isVisible();
    }

    // Custom convenience method (not strict Playwright, but highly useful for QA)
    @HostAccess.Export
    public boolean exists(String selector) {
        validateSelector(selector);
        return page.locator(selector).count() > 0;
    }

    // -------------------------------------------------------------------------
    // Interacting with forms / elements
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public void click(String selector) {
        validateSelector(selector);
        page.locator(selector).first().click();
    }

    @HostAccess.Export
    public void dblclick(String selector) {
        validateSelector(selector);
        page.locator(selector).first().dblclick();
    }

    @HostAccess.Export
    public void hover(String selector) {
        validateSelector(selector);
        page.locator(selector).first().hover();
    }

    @HostAccess.Export
    public void fill(String selector, String value) {
        validateSelector(selector);
        validateInputValue(value);
        page.locator(selector).first().fill(value);
    }

    @HostAccess.Export
    public void selectOption(String selector, String value) {
        validateSelector(selector);
        page.locator(selector).first().selectOption(value);
    }

    @HostAccess.Export
    public void check(String selector) {
        validateSelector(selector);
        page.locator(selector).first().check();
    }

    @HostAccess.Export
    public void uncheck(String selector) {
        validateSelector(selector);
        page.locator(selector).first().uncheck();
    }

    @HostAccess.Export
    public void press(String selector, String key) {
        validateSelector(selector);
        validateKeyName(key);
        page.locator(selector).first().press(key);
    }

    // -------------------------------------------------------------------------
    // Waiting
    // -------------------------------------------------------------------------

    /**
     * Wait for a selector to appear. Max 15 seconds.
     */
    @HostAccess.Export
    public void waitForSelector(String selector) {
        validateSelector(selector);
        page.locator(selector).first().waitFor(
                new Locator.WaitForOptions().setTimeout(15_000)
        );
    }

    /**
     * Playwright native wait. Max 5 seconds to prevent thread locking.
     */
    @HostAccess.Export
    public void waitForTimeout(int milliseconds) {
        if (milliseconds < 0 || milliseconds > 5_000) {
            throw new SecurityException("waitForTimeout must be between 0 and 5000ms");
        }
        page.waitForTimeout(milliseconds);
    }

    // -------------------------------------------------------------------------
    // Screenshots
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public String screenshot() {
        byte[] buffer = page.screenshot(new Page.ScreenshotOptions()
                .setType(com.microsoft.playwright.options.ScreenshotType.JPEG)
                .setQuality(80)
                .setFullPage(false));

        return java.util.Base64.getEncoder().encodeToString(buffer);
    }

    // -------------------------------------------------------------------------
    // Validation helpers — these NEVER reach the JS sandbox
    // -------------------------------------------------------------------------

    private void validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new SecurityException("URL must not be blank");
        }
        String lower = url.toLowerCase().trim();
        if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("file:")) {
            throw new SecurityException("URL scheme not allowed: " + url);
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new SecurityException("Only http/https URLs are allowed");
        }
    }

    private void validateSelector(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new SecurityException("Selector must not be blank");
        }
        if (selector.length() > 500) {
            throw new SecurityException("Selector too long");
        }
    }

    private void validateAttributeName(String attribute) {
        if (!attribute.matches("[a-zA-Z][a-zA-Z0-9\\-_]*")) {
            throw new SecurityException("Invalid attribute name: " + attribute);
        }
    }

    private void validateInputValue(String value) {
        if (value != null && value.length() > 10_000) {
            throw new SecurityException("Input value too long");
        }
    }

    private void validateKeyName(String key) {
        Set<String> allowed = Set.of(
                "Enter", "Tab", "Escape", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight",
                "Backspace", "Delete", "Home", "End", "PageUp", "PageDown", "Space"
        );
        if (!allowed.contains(key)) {
            throw new SecurityException("Key not allowed: " + key);
        }
    }

    private String truncate(String text) {
        if (text == null) return null;
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}