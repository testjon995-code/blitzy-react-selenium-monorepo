package com.qa.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Single source of the runtime settings used by this Selenium suite.
 *
 * <p>Each setting is read from the JVM system property of the same name, so a Maven
 * invocation such as {@code mvn clean test -DbaseUrl=http://localhost:5173} overrides a
 * default without a source or POM edit:</p>
 *
 * <ul>
 * <li>{@code baseUrl} - origin of the application under test, default
 * {@code http://localhost:5173}, the port the frontend dev server pins with strictPort.</li>
 * <li>{@code headless} - whether Chrome runs without a visible window, default {@code true}.</li>
 * <li>{@code timeoutSeconds} - budget for each explicit wait, default {@code 10}.</li>
 * </ul>
 *
 * <p>A property that is absent, empty or whitespace only falls back to the default declared
 * here. A property that is supplied but malformed raises {@link IllegalArgumentException}
 * naming the key, the offending value and the expected form, so the run fails while
 * configuration is read and before a browser is launched or navigated.</p>
 *
 * <p>Values are resolved on every call rather than cached, which keeps a rejection
 * attributable to the caller that asked for it instead of surfacing as a class
 * initialization error.</p>
 */
public final class TestConfig {

    /** System property that overrides the origin of the application under test. */
    private static final String BASE_URL_PROPERTY = "baseUrl";

    /** System property that overrides headless browser execution. */
    private static final String HEADLESS_PROPERTY = "headless";

    /** System property that overrides the explicit wait budget, in seconds. */
    private static final String TIMEOUT_SECONDS_PROPERTY = "timeoutSeconds";

    /** Origin served by the frontend dev server, which pins port 5173 with strictPort. */
    private static final String DEFAULT_BASE_URL = "http://localhost:5173";

    /** Headless is the committed default so the suite needs no display to run. */
    private static final boolean DEFAULT_HEADLESS = true;

    /** Wait budget that comfortably covers local, synchronous DOM updates. */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /** This class only exposes static accessors, holds no state and is never instantiated. */
    private TestConfig() {
    }

    /**
     * Resolves the origin the suite navigates to.
     *
     * <p>Trimming is the only normalization applied: a trailing slash, the host and the
     * case of the supplied value are all preserved, so the value a caller passes is the
     * value the browser receives.</p>
     *
     * @return the validated absolute {@code http} or {@code https} origin, or
     *         {@code http://localhost:5173} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value is not a parseable, absolute
     *         {@code http} or {@code https} URL carrying a host
     */
    public static String baseUrl() {
        String value = trimmedProperty(BASE_URL_PROPERTY);
        if (value == null) {
            return DEFAULT_BASE_URL;
        }

        String expectation = "expected an absolute http or https URL, for example -D"
                + BASE_URL_PROPERTY + "=" + DEFAULT_BASE_URL;

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException cause) {
            throw new IllegalArgumentException("Invalid system property " + BASE_URL_PROPERTY
                    + "=\"" + value + "\": " + expectation, cause);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean supportedScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!uri.isAbsolute() || !supportedScheme || host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid system property " + BASE_URL_PROPERTY
                    + "=\"" + value + "\": " + expectation);
        }

        return value;
    }

    /**
     * Resolves whether the browser runs headless.
     *
     * <p>Only {@code true} and {@code false} are accepted, ignoring case. Lenient boolean
     * parsing is deliberately avoided because it would silently turn a typo such as
     * {@code yes} into {@code false} and quietly change how the browser is launched.</p>
     *
     * @return the requested mode, or {@code true} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value is neither {@code true}
     *         nor {@code false}
     */
    public static boolean headless() {
        String value = trimmedProperty(HEADLESS_PROPERTY);
        if (value == null) {
            return DEFAULT_HEADLESS;
        }

        if ("true".equalsIgnoreCase(value)) {
            return true;
        }

        if ("false".equalsIgnoreCase(value)) {
            return false;
        }

        throw new IllegalArgumentException("Invalid system property " + HEADLESS_PROPERTY
                + "=\"" + value + "\": expected exactly true or false, for example -D"
                + HEADLESS_PROPERTY + "=false");
    }

    /**
     * Resolves the number of seconds an explicit wait may spend on a DOM state.
     *
     * @return the requested budget, or {@code 10} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value is not an {@code int} or is
     *         not strictly greater than zero
     */
    public static int timeoutSeconds() {
        String value = trimmedProperty(TIMEOUT_SECONDS_PROPERTY);
        if (value == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }

        String expectation = "expected a positive integer number of seconds, for example -D"
                + TIMEOUT_SECONDS_PROPERTY + "=20";

        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException cause) {
            throw new IllegalArgumentException("Invalid system property "
                    + TIMEOUT_SECONDS_PROPERTY + "=\"" + value + "\": " + expectation, cause);
        }

        if (seconds <= 0) {
            throw new IllegalArgumentException("Invalid system property "
                    + TIMEOUT_SECONDS_PROPERTY + "=\"" + value + "\": " + expectation);
        }

        return seconds;
    }

    /**
     * Reads one system property and reports whether it was supplied at all.
     *
     * @param key the system property name, which is also the Maven {@code -D} key
     * @return the trimmed value, or {@code null} when the property is unset, empty or
     *         whitespace only, in which case the caller applies its default
     */
    private static String trimmedProperty(String key) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return raw.trim();
    }
}
