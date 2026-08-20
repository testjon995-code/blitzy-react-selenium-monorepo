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
 * <p>Every test method gets a session of its own, created and quit around it. The driver is
 * an instance field rather than a static or thread-local one, so no test can observe or
 * corrupt another's browser state.</p>
 *
 * <p>{@link TestConfig} is consulted before any Selenium object is built, so a malformed
 * override is rejected while configuration is read rather than after a browser has been
 * launched. The only committed Chrome options are the headless mode and a fixed window size:
 * nothing here weakens the browser's isolation and there is deliberately no generic argument
 * passthrough, which is why the suite is run by an unprivileged operating-system user rather
 * than by root.</p>
 *
 * <p>Locators, wait conditions and expectations belong to the page objects and the test
 * classes. This class resolves no locator, reads no page state and asserts nothing.</p>
 */
public abstract class BaseTest {

    protected WebDriver driver;

    /**
     * Page objects share this explicit wait. It is the suite's only waiting mechanism: no
     * implicit driver timeout and no fixed-duration pause is configured anywhere.
     */
    protected WebDriverWait wait;

    /**
     * Resolves configuration, launches Chrome, builds the wait and opens the application.
     *
     * <p>The order is part of the contract rather than a matter of style. Configuration is
     * read before any browser process exists, so an invalid {@code -DbaseUrl},
     * {@code -Dheadless} or {@code -DtimeoutSeconds} raises {@link IllegalArgumentException}
     * from {@link TestConfig} instead of leaving a Chrome process behind; navigation comes
     * last, so an inheriting test body always starts on a loaded page.</p>
     *
     * <p>The chromedriver binary is resolved by Selenium Manager, which ships with the
     * declared Selenium distribution, so no driver path, service or manager library is
     * configured here.</p>
     */
    @BeforeEach
    protected void setUp() {
        String baseUrl = TestConfig.baseUrl();
        boolean headless = TestConfig.headless();
        int timeoutSeconds = TestConfig.timeoutSeconds();

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }

        options.addArguments("--window-size=1280,900");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

        driver.get(baseUrl);
    }

    /**
     * Quits the browser session created for the test method that has just finished.
     *
     * <p>The null guard is what makes teardown safe after a partial setup: when configuration
     * was rejected the driver was never assigned. A failure to quit is intentionally left to
     * propagate, because a browser that cannot be closed is a leak the run should report
     * rather than hide.</p>
     */
    @AfterEach
    protected void tearDown() {
        if (driver != null) {
            driver.quit();
            driver = null;
        }
    }
}
