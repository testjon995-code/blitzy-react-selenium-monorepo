package com.qa.tests;

import com.qa.base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke coverage that the configured origin is serving this repository's own application.
 *
 * <p>It is the cheapest scenario in the suite and the first one worth reading when the rest of it
 * fails. It asserts the document title of the page the shared base has opened, so a pass means the
 * dev server is up at the configured origin and returned the application's own document, and a
 * failure separates "nothing is being served here" from "the feature under test is broken". No
 * behaviour of the Student Search region is exercised here; that belongs to
 * {@link StudentSearchTest}.</p>
 *
 * <p>The session, the origin and the teardown all arrive from {@link BaseTest}, which opens the
 * application before this test runs and quits the browser afterwards whatever the outcome. This
 * class therefore constructs no driver, names no URL, reads no configuration, resolves no selector
 * and navigates nowhere: it carries one expectation and nothing else, and the suite keeps a single
 * browser lifecycle.</p>
 *
 * <p>The class name is historical: renaming it would record a delete and an add.</p>
 *
 * @see BaseTest
 * @see StudentSearchTest
 */
public class GoogleTest extends BaseTest {

    /**
     * Asserts that the page the shared base opened carries this application's document title.
     *
     * <p>Nothing is navigated here because there is nothing left to navigate: the inherited setup
     * opens the configured origin before this method runs, so the title read below is the title of
     * the application under test. Nothing is waited on either - a title is available as soon as
     * navigation returns, and waiting for an already settled value would only hide an unserved
     * origin behind a timeout.</p>
     *
     * <p>The comparison is exact rather than a substring or case-insensitive match, so a different
     * document served at the same origin fails here instead of passing on a coincidence.</p>
     */
    @Test
    void verifyLocalApplicationTitle() {
        String title = driver.getTitle();

        // The expected value is owned by frontend/index.html; change both in the same commit.
        assertEquals(
                "frontend",
                title,
                "frontend/index.html declares the title the configured origin should serve");
    }
}
