package com.qa.tests;

import com.qa.base.BaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Local exact-title smoke test for the configured application URL.
 */
public class GoogleTest extends BaseTest {

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
