package com.qa.base;

import com.qa.config.TestConfig;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Shared per-test Chrome lifecycle for this Selenium suite.
 *
 * <p>Test classes extend this base instead of owning a browser, so setup, navigation, wait
 * construction and teardown are declared exactly once. Each test method receives a brand new
 * Chrome session that has already been navigated to the configured origin, and that session
 * is quit when the method finishes, whatever its outcome. Sessions are never shared or
 * reused: the driver is an instance field created per test, never a static or thread-local
 * one, so a test can neither observe nor corrupt another test's browser state.</p>
 *
 * <p>No setting is resolved here. {@link TestConfig} owns the {@code baseUrl},
 * {@code headless} and {@code timeoutSeconds} values together with their defaults, and it is
 * consulted before any Selenium object is built so a malformed override is rejected while
 * configuration is being read rather than after a browser has been launched.</p>
 *
 * <p>Exactly two Chrome arguments are committed: the current headless mode, applied only when
 * headless execution is requested, and a fixed 1280x900 window so the viewport is identical
 * headless or headed. Nothing here weakens the browser's isolation and there is deliberately
 * no generic argument passthrough, which is why the suite is run by an unprivileged
 * operating-system user rather than by root.</p>
 *
 * <p>Subclasses inherit {@link #driver} and {@link #wait} and drive the page objects, which
 * own every locator and every wait condition. This class itself resolves no locator, reads no
 * page state and asserts nothing.</p>
 */
public abstract class BaseTest {

    /**
     * Browser session for the test method currently executing.
     *
     * <p>Typed as the {@link WebDriver} interface so subclasses and page objects depend on the
     * contract rather than on Chrome, and assigned a fresh {@link ChromeDriver} by
     * {@link #setUp()}. It holds {@code null} only before setup runs and after teardown.</p>
     */
    protected WebDriver driver;

    /**
     * Explicit wait bound to {@link #driver} and to the configured timeout budget.
     *
     * <p>Page objects use it to wait for a settled DOM state. It is this suite's only waiting
     * mechanism: no global driver timeout is configured and no fixed pause is used
     * anywhere.</p>
     */
    protected WebDriverWait wait;

    /**
     * Resolves configuration, launches Chrome, builds the wait and opens the application.
     *
     * <p>The order of the steps is part of the contract rather than a matter of style.
     * Configuration is read first, so an invalid {@code -DbaseUrl}, {@code -Dheadless} or
     * {@code -DtimeoutSeconds} raises {@link IllegalArgumentException} from {@link TestConfig}
     * before any browser process exists; navigation comes last, once both the driver and the
     * wait are available to the test that inherits them.</p>
     *
     * <p>The chromedriver binary is resolved by Selenium Manager, which ships with the
     * declared Selenium distribution, so no driver path, service or manager library is
     * configured here.</p>
     *
     * <p>Declared {@code protected} rather than package private because the test classes that
     * inherit this lifecycle method live in a different package.</p>
     */
    @BeforeEach
    protected void setUp() {
        // Read every setting up front: a rejected value must fail before Chrome is launched.
        String baseUrl = TestConfig.baseUrl();
        boolean headless = TestConfig.headless();
        int timeoutSeconds = TestConfig.timeoutSeconds();

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }

        // Unconditional, and after the headless block, so the size applies in both modes.
        options.addArguments("--window-size=1280,900");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

        // Navigate last: the test body inherits an already loaded page.
        driver.get(baseUrl);
    }

    /**
     * Quits the browser session created for the test method that has just finished.
     *
     * <p>JUnit runs this even when the test failed or when {@link #setUp()} threw partway, so
     * the null check is what makes teardown unconditional: when configuration was rejected the
     * driver was never assigned and there is nothing to quit. Clearing the field afterwards
     * makes a repeated invocation harmless. A failure to quit is intentionally left to
     * propagate, because a browser that cannot be closed is a leak the run should report
     * rather than hide.</p>
     *
     * <p>Declared {@code protected} for the same cross-package inheritance reason as
     * {@link #setUp()}.</p>
     */
    @AfterEach
    protected void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
