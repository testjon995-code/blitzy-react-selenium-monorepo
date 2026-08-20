package com.qa.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Single source of this suite's three runtime settings - {@code baseUrl}, {@code headless} and
 * {@code timeoutSeconds} - each read from the JVM system property of the same name, so a Maven
 * {@code -D} override needs no source or POM edit.
 *
 * <p>An absent, empty or whitespace-only property falls back to the default declared here. A
 * supplied value is validated after {@code trim()}, so whitespace around it never decides whether
 * it is accepted; a malformed one raises {@link IllegalArgumentException} naming the key, the
 * value as supplied and the expected form, so a run fails while configuration is read rather than
 * after a browser has been launched.</p>
 *
 * <p>A rejection renders the offending value escaped and quoted rather than verbatim, because it
 * is untrusted text on its way into a terminal or a CI log: a control character shown as an
 * escape cannot forge a log line or drive a terminal, an escaped quote cannot be read as the
 * message framing it, and a rejected {@code baseUrl} has anything that could be user information
 * replaced by {@code ***}. For the same reason no parser exception is ever attached as a cause -
 * both {@link URISyntaxException} and {@link NumberFormatException} repeat the offending value
 * unescaped in their own message.</p>
 *
 * <p>Two restrictions are narrower than the phrase "an absolute HTTP(S) override", both
 * deliberately: a URL embedding user information is refused rather than forwarded to the browser,
 * and a value carrying a control or other non-printable character inside its trimmed text is
 * refused before a parser sees it. Neither can turn away a well-formed local origin, because an
 * RFC 3986 URI is ASCII and holds no control character - nor can they turn away {@code true},
 * {@code false} or a whole number.</p>
 *
 * <p>Values are resolved on every call rather than cached, which keeps a rejection attributable
 * to the caller that asked for it instead of surfacing as a class initialization error.</p>
 */
public final class TestConfig {

    private static final String BASE_URL_PROPERTY = "baseUrl";

    private static final String HEADLESS_PROPERTY = "headless";

    private static final String TIMEOUT_SECONDS_PROPERTY = "timeoutSeconds";

    private static final String DEFAULT_BASE_URL = "http://localhost:5173";

    /** Headless is the committed default so the suite needs no display to run. */
    private static final boolean DEFAULT_HEADLESS = true;

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /** A wait has to be able to expire, so the smallest budget worth accepting is one second. */
    private static final int MIN_TIMEOUT_SECONDS = 1;

    private TestConfig() {
    }

    /**
     * Resolves the URL the suite navigates to.
     *
     * <p>Trimming is the only normalization applied: scheme case, host, port, trailing slash and
     * any path or query the supplied value carries all reach the browser as supplied.</p>
     *
     * <p>User information - the {@code user:password@} prefix of an authority - is refused instead
     * of forwarded, even though a URL carrying it parses perfectly well. The application under
     * test is an unauthenticated local dev server, so a credential here can only be a mistake,
     * and refusing it keeps that mistake out of a browser session, a driver log and anything the
     * origin is later reported to. The refusal covers user information and nothing else: a secret
     * placed in the path, query or fragment is structurally valid, so it is accepted, navigated
     * to, and shown in full if some other part of the value is rejected. No secret belongs in
     * this property.</p>
     *
     * @return the validated absolute {@code http} or {@code https} URL, trimmed, or
     *         {@code http://localhost:5173} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value contains a control or other
     *         non-printable character, does not parse as a URI, is not absolute, does not use the
     *         {@code http} or {@code https} scheme, carries no host, or embeds user information
     */
    public static String baseUrl() {
        String expectation = "expected an absolute http or https URL with no embedded"
                + " credentials, for example -D" + BASE_URL_PROPERTY + "=" + DEFAULT_BASE_URL;

        String raw = suppliedProperty(BASE_URL_PROPERTY);
        if (raw == null) {
            return DEFAULT_BASE_URL;
        }

        // Reported by every rejection below, so no path can name this value with a credential
        // still legible in it.
        String supplied = withoutCredentials(raw);
        String value = raw.trim();
        requirePrintable(BASE_URL_PROPERTY, value, supplied, expectation);

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException cause) {
            // The exception itself is deliberately not attached: its message repeats the value
            // unescaped. Its reason and index are written by the parser, carry none of the input,
            // and are the part actually worth reading.
            String position = cause.getIndex() < 0 ? "" : " at index " + cause.getIndex();
            throw rejected(BASE_URL_PROPERTY, supplied,
                    escaped(cause.getReason()) + position + "; " + expectation);
        }

        if (uri.getRawUserInfo() != null) {
            // Checked before the scheme and host so a credential-bearing value is never reported
            // by a rejection that was written for a different defect.
            throw rejected(BASE_URL_PROPERTY, supplied, "the authority embeds user information,"
                    + " shown above as ***; " + expectation);
        }

        // Locale.ROOT keeps this an ASCII fold. Unicode-aware case folding treats U+017F as an
        // s, which would read a scheme this class does not accept as one that it does.
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        boolean supportedScheme = scheme.equals("http") || scheme.equals("https");
        if (!uri.isAbsolute() || !supportedScheme || host == null || host.isBlank()) {
            throw rejected(BASE_URL_PROPERTY, supplied, expectation);
        }

        return value;
    }

    /**
     * Resolves whether the browser runs headless.
     *
     * <p>Only the exact words {@code true} and {@code false} are accepted. Lenient parsing is
     * avoided because it would silently turn a typo such as {@code yes} into {@code false} and
     * quietly change how the browser is launched, and case folding is avoided for the same
     * reason: {@code TRUE} is a value somebody meant to write another way, and a Unicode
     * look-alike such as {@code falſe} - with U+017F where the {@code s} belongs - is a value
     * nobody meant at all.</p>
     *
     * @return the requested mode, or {@code true} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value contains a control or other
     *         non-printable character, or is neither {@code true} nor {@code false}
     */
    public static boolean headless() {
        String expectation = "expected exactly true or false, in lower case, for example -D"
                + HEADLESS_PROPERTY + "=false";

        String raw = suppliedProperty(HEADLESS_PROPERTY);
        if (raw == null) {
            return DEFAULT_HEADLESS;
        }

        String value = raw.trim();
        requirePrintable(HEADLESS_PROPERTY, value, raw, expectation);

        if (value.equals("true")) {
            return true;
        }

        if (value.equals("false")) {
            return false;
        }

        throw rejected(HEADLESS_PROPERTY, raw, expectation);
    }

    /**
     * Resolves the number of seconds an explicit wait may spend on a DOM state.
     *
     * <p>Any positive whole number is accepted, so how long a run may spend on a DOM state that
     * never settles is the caller's decision rather than this class's. Only three shapes are
     * refused: zero and negative numbers, because a wait has to be able to run; digits an
     * {@code int} cannot hold, because the budget has to be representable; and anything not
     * written in ASCII digits, because the budget has to be the number a reader sees.</p>
     *
     * @return the requested budget, or {@code 10} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value contains a control or other
     *         non-printable character, is not written in ASCII digits, is below one second, or is
     *         larger than an {@code int} can represent
     */
    public static int timeoutSeconds() {
        String expectation = "expected a whole number of seconds of " + MIN_TIMEOUT_SECONDS
                + " or more, written in ASCII digits, for example -D" + TIMEOUT_SECONDS_PROPERTY
                + "=20";

        String raw = suppliedProperty(TIMEOUT_SECONDS_PROPERTY);
        if (raw == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }

        String value = raw.trim();
        requirePrintable(TIMEOUT_SECONDS_PROPERTY, value, raw, expectation);

        if (!isAsciiInteger(value)) {
            // Checked before parsing because Integer.parseInt accepts any Unicode decimal digit,
            // so a value such as a 1 followed by U+FF10 FULLWIDTH DIGIT ZERO would quietly parse
            // as 10.
            throw rejected(TIMEOUT_SECONDS_PROPERTY, raw, expectation);
        }

        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            // The text is ASCII-numeric here, so the only way to reach this is a value too large
            // for an int. Not attached as a cause: its message repeats the value unescaped.
            throw rejected(TIMEOUT_SECONDS_PROPERTY, raw, "a budget above " + Integer.MAX_VALUE
                    + " seconds cannot be represented; " + expectation);
        }

        if (seconds < MIN_TIMEOUT_SECONDS) {
            throw rejected(TIMEOUT_SECONDS_PROPERTY, raw, "a wait has to be able to run, so a"
                    + " budget below " + MIN_TIMEOUT_SECONDS + " second is refused; "
                    + expectation);
        }

        return seconds;
    }

    /**
     * Reads one system property and reports whether it was supplied at all.
     *
     * <p>Nonblank text is returned exactly as the JVM holds it. Callers validate a trimmed copy
     * but name this untouched text when they reject a value - in the escaped rendering
     * {@link #rejected(String, String, String)} produces - so a failure names the value as it was
     * supplied rather than the trimmed form derived from it.</p>
     *
     * @param key the system property name, which is also the Maven {@code -D} key
     * @return the value as supplied, or {@code null} when the property is unset, empty or
     *         whitespace only, in which case the caller applies its default
     */
    private static String suppliedProperty(String key) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        return raw;
    }

    /**
     * Refuses a value that carries a control or other non-printable character.
     *
     * <p>The scan runs on the trimmed value, so whitespace around a value is what {@code trim()}
     * says it is rather than grounds for refusal, while a control character embedded in the value
     * itself - the one that could forge a log line - is still refused. Refusing it here, before
     * any parser is handed the value, is what keeps this class's rendering guarantee whole: the
     * value never reaches {@link URI} or {@link Integer#parseInt(String)}, so no exception
     * repeating it unescaped is ever constructed.</p>
     *
     * @param key the system property name being validated
     * @param value the trimmed value, scanned character by character
     * @param supplied the value as supplied, which is what a rejection names
     * @param expectation the accepted form of this property, so the rejection stays actionable
     * @throws IllegalArgumentException if any character of {@code value} is not printable
     */
    private static void requirePrintable(String key, String value, String supplied,
            String expectation) {
        for (int index = 0; index < value.length(); index++) {
            if (!printable(value.charAt(index))) {
                throw rejected(key, supplied, "a control or other non-printable character at"
                        + " index " + index + " of the trimmed value cannot appear in this"
                        + " setting; " + expectation);
            }
        }
    }

    /**
     * Reports whether a character is acceptable inside a supplied property value.
     *
     * <p>The categories excluded are the ones that do something rather than show something: the
     * C0 and C1 controls, which include the carriage return and line feed that would forge a log
     * line and the escape that begins a terminal sequence; the format characters, which include
     * the bidirectional overrides that can display text in an order it was not written in; the
     * line and paragraph separators, which some readers treat as line breaks; and unassigned,
     * private-use and lone surrogate code units, which have no dependable rendering at all.</p>
     *
     * @param character the character to classify
     * @return {@code true} when the character can be shown as itself
     */
    private static boolean printable(char character) {
        if (Character.isISOControl(character)) {
            return false;
        }

        return switch (Character.getType(character)) {
            case Character.UNASSIGNED, Character.CONTROL, Character.FORMAT,
                    Character.PRIVATE_USE, Character.SURROGATE,
                    Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> false;
            default -> true;
        };
    }

    /**
     * Reports whether a value is written entirely in ASCII digits, with an optional sign.
     *
     * <p>{@link Integer#parseInt(String)} accepts any character Unicode classifies as a decimal
     * digit, so it reads a value such as {@code 1} followed by U+FF10 as {@code 10}. This check
     * keeps the accepted form to what the documentation promises - a whole number of seconds - so
     * a budget cannot differ from the number a reader sees. It deliberately still allows a
     * leading sign, because rejecting {@code -1} for its value rather than its spelling gives the
     * clearer message.</p>
     *
     * @param value the trimmed value to inspect
     * @return {@code true} when the value is an optional sign followed by ASCII digits only
     */
    private static boolean isAsciiInteger(String value) {
        int index = 0;
        if (!value.isEmpty() && (value.charAt(0) == '+' || value.charAt(0) == '-')) {
            index = 1;
        }

        if (index == value.length()) {
            return false;
        }

        while (index < value.length()) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }

            index++;
        }

        return true;
    }

    /**
     * Builds the exception every rejection in this class throws.
     *
     * <p>Every message has the same three parts, because each answers a question the reader has:
     * which property was rejected, what its value was, and what would have been accepted instead.
     * The value is wrapped in double quotes, which is what makes a space-padded value visible,
     * and escaped, which is what keeps the quotes meaningful and the line inert. No cause is ever
     * attached.</p>
     *
     * @param key the system property name, which is also the Maven {@code -D} key
     * @param supplied the offending value as supplied, already credential-redacted for a property
     *        that can carry one
     * @param detail why the value was rejected and what form is expected
     * @return the exception to throw, with no cause attached
     */
    private static IllegalArgumentException rejected(String key, String supplied, String detail) {
        return new IllegalArgumentException("Invalid system property " + key + "=\""
                + escaped(supplied) + "\": " + detail);
    }

    /**
     * Escapes a value so it can be written to a terminal or a log as inert text.
     *
     * <p>Backslashes and double quotes are escaped, the familiar control characters are replaced
     * by their usual named escapes, and everything else outside printable ASCII becomes a numeric
     * escape in the Java style, which cannot move a cursor, start a control sequence or reverse
     * the reading order of the line. That is stricter than what {@link #printable(char)} refuses,
     * deliberately: a rejected {@code headless=falſe} is refused for not being the word
     * {@code false}, and rendered as itself it would read as {@code false} and leave the reader
     * with a message that appears to contradict itself.</p>
     *
     * @param value the text to escape, which may be untrusted
     * @return the escaped text, containing printable ASCII characters only
     */
    private static String escaped(String value) {
        StringBuilder rendered = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> rendered.append("\\\\");
                case '"' -> rendered.append("\\\"");
                case '\n' -> rendered.append("\\n");
                case '\r' -> rendered.append("\\r");
                case '\t' -> rendered.append("\\t");
                case '\b' -> rendered.append("\\b");
                case '\f' -> rendered.append("\\f");
                default -> {
                    if (character >= ' ' && character <= '~') {
                        rendered.append(character);
                    } else {
                        rendered.append(String.format("\\u%04x", (int) character));
                    }
                }
            }
        }

        return rendered.toString();
    }

    /**
     * Replaces anything that could be user information in a URL with {@code ***}.
     *
     * <p>Applied to every rejected {@code baseUrl}, not only to the rejection that names user
     * information, because a value can be rejected for its scheme, its host or its syntax and
     * still carry a password. It works on the supplied text rather than on a parsed {@link URI},
     * so a value that never parsed is covered too.</p>
     *
     * <p>The rule is deliberately blunt, so that a malformed value cannot evade it: if the text
     * contains an {@code @} at all, everything before the <em>last</em> one is replaced and
     * nothing is kept in front of the replacement. Anchoring on the first {@code @} would expose
     * the tail of a password that itself contains one, and keeping a leading {@code http://} as a
     * marker of where the authority begins would trust a prefix that a malformed value can put
     * credential text in front of.</p>
     *
     * <p>The consequence is over-hiding, and that is the intended trade: {@code ftp://host/a@b} is
     * reported as {@code ***@b}, hiding a scheme and a path that held no secret, because in a
     * value this malformed the {@code @} is indistinguishable from user information. Over-hiding
     * costs a little detail in a message that already names the expected form; under-hiding writes
     * a credential to a log. Note the boundary: only text before an {@code @} is hidden, so a
     * secret in the host, port, path, query or fragment is shown escaped but not redacted - and
     * such a value, being structurally valid, would be navigated to rather than rejected.</p>
     *
     * @param value the supplied value, which may be malformed
     * @return the value with anything that could be user information replaced, or unchanged when
     *         it contains no {@code @}
     */
    private static String withoutCredentials(String value) {
        int userInfoEnd = value.lastIndexOf('@');
        if (userInfoEnd < 0) {
            return value;
        }

        return "***" + value.substring(userInfoEnd);
    }
}
