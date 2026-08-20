package com.qa.config;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Single source of this suite's three runtime settings - {@code baseUrl},
 * {@code headless} and {@code timeoutSeconds} - each read from the JVM system property of
 * the same name, so a Maven {@code -D} override needs no source or POM edit.
 *
 * <p>An absent, empty or whitespace-only property falls back to the default declared here.
 * A supplied but malformed value raises {@link IllegalArgumentException} naming the key,
 * the value and the expected form, so a run fails while configuration is read rather than
 * after a browser has been launched.</p>
 *
 * <p>Validation runs on the trimmed value while a rejection names the value as supplied
 * rather than the trimmed form this class derived, which is what makes an argument that did
 * not arrive as intended diagnosable from the message alone.</p>
 *
 * <p>It names that form as an escaped, printable rendering rather than verbatim, because a
 * rejected value is untrusted text on its way into a terminal or a CI log. A control
 * character is shown as an escape, and a value containing any control or other non-printable
 * character is refused on that ground alone before a parser ever sees it, so a carriage
 * return, an escape sequence or a bidirectional override cannot forge a log line, drive a
 * terminal or reorder the text around it. A quote or backslash is escaped too, so the quoted
 * value cannot be mistaken for the message that frames it. A rejected {@code baseUrl} is
 * shown with anything that could be user information replaced by {@code ***}; the rest of the
 * URL is escaped but not redacted, which is why no secret belongs in that setting. For the
 * same reason no parser exception is attached as a cause: both {@link URISyntaxException} and
 * {@link NumberFormatException} repeat the offending value unescaped in their own message, so
 * only the parser's own input-free reason is carried over.</p>
 *
 * <p>Values are resolved on every call rather than cached, which keeps a rejection
 * attributable to the caller that asked for it instead of surfacing as a class
 * initialization error.</p>
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

    /**
     * Largest wait budget this suite accepts, in seconds. Every larger positive integer is
     * well formed too, so without a ceiling one mistyped digit turns a DOM state that never
     * settles into a worker held for years rather than a test that fails in seconds.
     */
    private static final int MAX_TIMEOUT_SECONDS = 300;

    private TestConfig() {
    }

    /**
     * Resolves the URL the suite navigates to.
     *
     * <p>Trimming is the only normalization applied: scheme case, host, port, trailing
     * slash and any path or query the supplied value carries are all preserved, so the
     * value a caller passes is the value the browser receives.</p>
     *
     * <p>User information - the {@code user:password@} prefix of an authority - is rejected
     * instead of forwarded, even though a URL carrying it parses perfectly well. The browser
     * would be navigated with that credential, and the suite has no use for one: the
     * application under test is an unauthenticated local dev server. Rejecting it here means a
     * credential passed by mistake fails the run at once rather than reaching a browser
     * session, a driver log or whatever the URL is later reported to.</p>
     *
     * <p>That rejection covers user information and nothing else. A password, API token,
     * bearer token or other secret placed in the path, query or fragment is structurally
     * valid, so it is accepted here, navigated to, and shown in full if some other part of the
     * value is rejected. No secret belongs in this property.</p>
     *
     * @return the validated absolute {@code http} or {@code https} URL, trimmed, or
     *         {@code http://localhost:5173} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value contains a control or other
     *         non-printable character, does not parse as a URI, is not absolute, does not use
     *         the {@code http} or {@code https} scheme, carries no host, or embeds user
     *         information
     */
    public static String baseUrl() {
        String expectation = "expected an absolute http or https URL with no embedded"
                + " credentials, for example -D" + BASE_URL_PROPERTY + "=" + DEFAULT_BASE_URL;

        String raw = suppliedProperty(BASE_URL_PROPERTY);
        if (raw == null) {
            return DEFAULT_BASE_URL;
        }

        // Built once and used by every rejection below, so no path can report this value
        // unescaped or with a credential still legible in it.
        String rendering = quoted(withoutCredentials(raw));
        requirePrintable(BASE_URL_PROPERTY, raw, rendering, expectation);

        String value = raw.trim();

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException cause) {
            // The exception itself is deliberately not attached: its message repeats the value
            // unescaped. Its reason and index are written by the parser, carry none of the
            // input, and are the part actually worth reading.
            String position = cause.getIndex() < 0 ? "" : " at index " + cause.getIndex();
            throw rejected(BASE_URL_PROPERTY, rendering,
                    escaped(cause.getReason()) + position + "; " + expectation);
        }

        if (uri.getRawUserInfo() != null) {
            // Checked before the scheme and host so a credential-bearing value is never
            // reported by a rejection that was written for a different defect.
            throw rejected(BASE_URL_PROPERTY, rendering, "the authority embeds user"
                    + " information, shown above as ***; " + expectation);
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean supportedScheme = scheme != null
                && (equalsIgnoringAsciiCase(scheme, "http") || equalsIgnoringAsciiCase(scheme, "https"));
        if (!uri.isAbsolute() || !supportedScheme || host == null || host.isBlank()) {
            throw rejected(BASE_URL_PROPERTY, rendering, expectation);
        }

        return value;
    }

    /**
     * Resolves whether the browser runs headless.
     *
     * <p>Only {@code true} and {@code false} are accepted, ignoring ASCII letter case. Lenient
     * boolean parsing is deliberately avoided because it would silently turn a typo such as
     * {@code yes} into {@code false} and quietly change how the browser is launched.</p>
     *
     * <p>The comparison is deliberately ASCII-only rather than
     * {@link String#equalsIgnoreCase(String)}. That method folds case across the whole of
     * Unicode, where U+017F LATIN SMALL LETTER LONG S folds to {@code s}, so it reports
     * {@code falſe} as equal to {@code false}. A look-alike is exactly the kind of value this
     * strict check exists to refuse: accepting one would launch a visible browser from a value
     * no reader would recognize as {@code false}.</p>
     *
     * @return the requested mode, or {@code true} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value contains a control or other
     *         non-printable character, or is neither {@code true} nor {@code false}
     */
    public static boolean headless() {
        String expectation = "expected the word true or false, in any ASCII letter case, for"
                + " example -D" + HEADLESS_PROPERTY + "=false";

        String raw = suppliedProperty(HEADLESS_PROPERTY);
        if (raw == null) {
            return DEFAULT_HEADLESS;
        }

        String rendering = quoted(raw);
        requirePrintable(HEADLESS_PROPERTY, raw, rendering, expectation);

        String value = raw.trim();
        if (equalsIgnoringAsciiCase(value, "true")) {
            return true;
        }

        if (equalsIgnoringAsciiCase(value, "false")) {
            return false;
        }

        throw rejected(HEADLESS_PROPERTY, rendering, expectation);
    }

    /**
     * Resolves the number of seconds an explicit wait may spend on a DOM state.
     *
     * <p>The budget is bounded at both ends, before the value can become a browser wait: a
     * wait has to be able to run, and it has to be able to stop, so a DOM state that never
     * settles surfaces as a test failing in seconds rather than as a worker blocked on it.</p>
     *
     * @return the requested budget, or {@code 10} when the property is absent or blank
     * @throws IllegalArgumentException if the supplied value contains a control or other
     *         non-printable character, is not an {@code int}, or falls outside 1 to 300
     *         seconds inclusive
     */
    public static int timeoutSeconds() {
        String expectation = "expected a whole number of seconds from " + MIN_TIMEOUT_SECONDS
                + " to " + MAX_TIMEOUT_SECONDS + ", for example -D" + TIMEOUT_SECONDS_PROPERTY
                + "=20";

        String raw = suppliedProperty(TIMEOUT_SECONDS_PROPERTY);
        if (raw == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }

        String rendering = quoted(raw);
        requirePrintable(TIMEOUT_SECONDS_PROPERTY, raw, rendering, expectation);

        String value = raw.trim();
        if (!isAsciiInteger(value)) {
            // Checked before parsing for the same reason headless() avoids equalsIgnoreCase:
            // Integer.parseInt accepts any Unicode decimal digit, so a value such as a 1
            // followed by U+FF10 FULLWIDTH DIGIT ZERO would quietly parse as 10. A budget has
            // to be the number it looks like.
            throw rejected(TIMEOUT_SECONDS_PROPERTY, rendering, expectation);
        }

        int seconds;
        try {
            seconds = Integer.parseInt(value);
        } catch (NumberFormatException cause) {
            // Still guarded: the text is ASCII-numeric here, but it can overflow an int.
            // Not attached as a cause: its message repeats the offending value unescaped, and
            // it says nothing the expectation below does not already say.
            throw rejected(TIMEOUT_SECONDS_PROPERTY, rendering, expectation);
        }

        if (seconds < MIN_TIMEOUT_SECONDS || seconds > MAX_TIMEOUT_SECONDS) {
            throw rejected(TIMEOUT_SECONDS_PROPERTY, rendering, "a budget outside "
                    + MIN_TIMEOUT_SECONDS + " to " + MAX_TIMEOUT_SECONDS + " seconds is refused"
                    + " so an unmet DOM state fails a test instead of holding a worker; "
                    + expectation);
        }

        return seconds;
    }

    /**
     * Reads one system property and reports whether it was supplied at all.
     *
     * <p>Nonblank text is returned exactly as the JVM holds it. Callers validate a trimmed
     * copy but name this untouched text when they reject a value - in the escaped rendering
     * {@link #quoted(String)} produces, never as raw text - so a failure names the value as it
     * was supplied rather than the trimmed form the caller derived from it.</p>
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
     * Refuses a supplied value that carries a control or other non-printable character.
     *
     * <p>None of the three settings can legitimately contain one: not a URL, not the word
     * {@code true} or {@code false}, not a number. Refusing such a value here, before any
     * parser is handed it, is what keeps the guarantee in this class's documentation whole. It
     * means the value never reaches {@link URI} or {@link Integer#parseInt(String)}, so no
     * exception repeating it unescaped is ever constructed, and the only place the text
     * appears is the escaped rendering this method is given.</p>
     *
     * @param key the system property name being validated
     * @param supplied the value as supplied, scanned character by character
     * @param rendering the escaped, quoted rendering of {@code supplied} to report
     * @param expectation the accepted form of this property, so the rejection stays actionable
     * @throws IllegalArgumentException if any character of {@code supplied} is not printable
     */
    private static void requirePrintable(String key, String supplied, String rendering,
            String expectation) {
        for (int index = 0; index < supplied.length(); index++) {
            if (!printable(supplied.charAt(index))) {
                throw rejected(key, rendering, "a control or other non-printable character at"
                        + " index " + index + " cannot appear in this setting; " + expectation);
            }
        }
    }

    /**
     * Builds the exception every rejection in this class throws.
     *
     * <p>Every message has the same three parts, because each answers a question the reader
     * has: which property was rejected, what its value was, and what would have been accepted
     * instead.</p>
     *
     * @param key the system property name, which is also the Maven {@code -D} key
     * @param rendering the escaped, quoted rendering of the offending value
     * @param detail why the value was rejected and what form is expected
     * @return the exception to throw, with no cause attached
     */
    private static IllegalArgumentException rejected(String key, String rendering, String detail) {
        return new IllegalArgumentException("Invalid system property " + key + "=" + rendering
                + ": " + detail);
    }

    /**
     * Renders a value for a diagnostic: escaped, and wrapped in double quotes.
     *
     * <p>The quotes are what make an empty or space-padded value visible; the escaping is what
     * keeps the quotes meaningful, since a quote or backslash inside the value is escaped and
     * so cannot be read as the end of it.</p>
     *
     * @param value the text to render, which may be untrusted
     * @return the quoted, escaped rendering
     */
    private static String quoted(String value) {
        return "\"" + escaped(value) + "\"";
    }

    /**
     * Escapes a value so it can be written to a terminal or a log as inert text.
     *
     * <p>Backslashes and double quotes are escaped so the rendering cannot be confused with
     * the message that frames it, and the familiar control characters are replaced by their
     * usual named escapes. Everything outside printable ASCII becomes a numeric escape in the
     * Java style, which is unambiguous and, unlike the character itself, cannot move a cursor,
     * start a control sequence or reverse the reading order of the line.</p>
     *
     * <p>Note that this is a stricter test than the one {@link #printable(char)} applies to
     * decide what to reject, and deliberately so. A rejected {@code headless=falſe} - with
     * U+017F where the {@code s} belongs - is refused because it is not the word {@code false},
     * but rendered as itself it would read as {@code false} and leave the reader staring at a
     * message that appears to contradict itself. Escaping it puts the reason in plain sight, and
     * it makes every diagnostic pure ASCII, which no terminal or log encoding can garble.</p>
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
     * Reports whether a character is acceptable inside a supplied property value.
     *
     * <p>This is the test {@link #requirePrintable(String, String, String, String)} applies to
     * decide what to refuse outright, which is a wider allowance than
     * {@link #escaped(String)} makes when rendering: a character can be legitimate in a value
     * yet still be worth escaping when it is shown back.</p>
     *
     * <p>The categories excluded here are the ones that do something rather than show
     * something: the C0 and C1 control characters, which include the carriage return and line
     * feed that would forge a new log line and the escape that begins a terminal control
     * sequence; the format characters, which include the bidirectional overrides that can
     * display text in an order it was not written in; the line and paragraph separators, which
     * some readers treat as line breaks; and unassigned, private-use and lone surrogate code
     * units, which have no dependable rendering at all.</p>
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
     * Replaces anything that could be user information in a URL with {@code ***}.
     *
     * <p>Applied to every rejected {@code baseUrl}, not only to the rejection that names user
     * information, because a value can be rejected for its scheme, its host or its syntax and
     * still carry a password. It works on the supplied text rather than on a parsed
     * {@link URI}, so a value that never parsed is covered too.</p>
     *
     * <p>The rule is deliberately blunt so that a malformed value cannot evade it. If the text
     * contains an {@code @} at all, everything before the <em>last</em> one is replaced, and
     * the only thing ever kept in front of the replacement is a prefix that has been
     * <em>proven</em> to be a supported scheme delimiter at the very start of the value:
     * optional leading whitespace followed by {@code http://} or {@code https://}. Nothing
     * else is trusted to mark where an authority begins. A value with no {@code @} carries no
     * user information and is returned unchanged.</p>
     *
     * <p>Both halves of that rule exist because of a way the obvious implementation leaks.
     * Anchoring on the first {@code @} rather than the last would expose the tail of a
     * password that itself contains one. Locating the authority by searching for the first
     * {@code //} anywhere would let a later one stand in for it: in
     * {@code http:/alice:secret//x@host} that separator sits after the credential, so the
     * credential would be kept and printed. Because the kept prefix can now only ever be
     * whitespace and one of two literal schemes, it cannot contain credential text at all,
     * whatever shape the rest of the value has.</p>
     *
     * <p>The consequence is over-hiding, and that is the intended trade: {@code ftp://host/a@b}
     * is reported as {@code ***@b}, hiding a scheme and a path that held no secret, because the
     * {@code @} in the path is indistinguishable from user information in a value this
     * malformed. Over-hiding costs a little detail in a message that is already telling the
     * reader what form was expected; under-hiding writes a credential to a log. Note the
     * boundary, though: only text before an {@code @} is hidden. The host, port, path, query
     * and fragment that follow it are shown - escaped, but not redacted - so a secret placed in
     * one of those is neither protected here nor, since such a value is structurally valid and
     * would be navigated to, protected at all. No secret belongs in this setting.</p>
     *
     * @param value the supplied value, which may be malformed
     * @return the value with anything that could be user information replaced, or unchanged
     *         when it contains no {@code @}
     */
    private static String withoutCredentials(String value) {
        int userInfoEnd = value.lastIndexOf('@');
        if (userInfoEnd < 0) {
            return value;
        }

        // Fail closed: keep the proven scheme prefix only when it really precedes the at-sign.
        int prefixEnd = supportedSchemePrefixLength(value);
        int redactionStart = prefixEnd <= userInfoEnd ? prefixEnd : 0;

        return value.substring(0, redactionStart) + "***" + value.substring(userInfoEnd);
    }

    /**
     * Length of the leading supported-scheme delimiter, or {@code 0} when there is not one.
     *
     * <p>Recognizes only optional leading whitespace followed by {@code http://} or
     * {@code https://}, matched ignoring ASCII letter case, at the very beginning of the value.
     * Those are the only two schemes this class accepts, so anything else - a different scheme,
     * a single slash, a missing scheme, or a {@code ://} that appears later in the text - is
     * reported as absent rather than guessed at.</p>
     *
     * <p>The comparison is ASCII-only for the reason given on {@link #headless()}: Unicode case
     * folding treats U+017F as {@code s}, so a Unicode-aware match would accept {@code httpſ://}
     * as a supported scheme and keep that pseudo-scheme in a diagnostic even though this class
     * rejects the value. Only the two literal ASCII schemes, in any ASCII case, count.</p>
     *
     * <p>That deliberate narrowness is what makes {@link #withoutCredentials(String)} safe: a
     * region measured by this method holds whitespace and one of two literal ASCII strings, so
     * it cannot contain a username, a password or a token no matter how the value was
     * written.</p>
     *
     * @param value the supplied value, which may be malformed
     * @return the number of leading characters safe to keep in a diagnostic, or {@code 0}
     */
    private static int supportedSchemePrefixLength(String value) {
        // Mirrors String.trim(), which is what decided the text that was validated, and keeps
        // the prefix this method measures within ASCII.
        int index = 0;
        while (index < value.length() && value.charAt(index) <= ' ') {
            index++;
        }

        String secureScheme = "https://";
        if (startsWithIgnoringAsciiCase(value, index, secureScheme)) {
            return index + secureScheme.length();
        }

        String scheme = "http://";
        if (startsWithIgnoringAsciiCase(value, index, scheme)) {
            return index + scheme.length();
        }

        return 0;
    }

    /**
     * Reports whether a value is written entirely in ASCII digits, with an optional sign.
     *
     * <p>{@link Integer#parseInt(String)} accepts any character Unicode classifies as a decimal
     * digit, so it reads a value such as {@code 1} followed by U+FF10 as {@code 10}. This check
     * keeps the accepted form to what the documentation promises - a whole number of seconds -
     * so a budget cannot differ from the number a reader sees. It deliberately still allows a
     * leading sign, because rejecting {@code -1} for its range rather than its spelling gives
     * the clearer message.</p>
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
     * Compares two strings for equality, ignoring ASCII letter case and nothing else.
     *
     * <p>Used in place of {@link String#equalsIgnoreCase(String)} wherever this class matches a
     * fixed keyword, because that method folds case across all of Unicode and so treats
     * look-alike letters as equal to ASCII ones. Every keyword this class recognizes -
     * {@code true}, {@code false}, {@code http}, {@code https} - is ASCII, so ASCII case is the
     * only variation that should be tolerated.</p>
     *
     * @param value the text to compare, which may be untrusted
     * @param expected the ASCII keyword to compare it against
     * @return {@code true} when the two differ only in the case of ASCII letters
     */
    private static boolean equalsIgnoringAsciiCase(String value, String expected) {
        return value.length() == expected.length()
                && startsWithIgnoringAsciiCase(value, 0, expected);
    }

    /**
     * Reports whether {@code value} contains {@code expected} at {@code offset}, ignoring ASCII
     * letter case and nothing else.
     *
     * @param value the text to inspect, which may be untrusted
     * @param offset where in {@code value} the comparison starts
     * @param expected the ASCII text to look for
     * @return {@code true} when the region exists and differs only in the case of ASCII letters
     */
    private static boolean startsWithIgnoringAsciiCase(String value, int offset, String expected) {
        if (offset + expected.length() > value.length()) {
            return false;
        }

        for (int index = 0; index < expected.length(); index++) {
            char actual = value.charAt(offset + index);
            char wanted = expected.charAt(index);
            if (actual != wanted && asciiLowerCase(actual) != asciiLowerCase(wanted)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Lowercases one ASCII letter and leaves every other character exactly as it is.
     *
     * <p>Unlike {@link Character#toLowerCase(char)} this maps nothing outside {@code A} to
     * {@code Z}, which is what keeps the comparisons built on it free of Unicode case
     * equivalences.</p>
     *
     * @param character the character to fold
     * @return the ASCII-lowercased character, or the character unchanged
     */
    private static char asciiLowerCase(char character) {
        if (character >= 'A' && character <= 'Z') {
            return (char) (character - 'A' + 'a');
        }

        return character;
    }
}
