package com.qa.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for the Student Search region of the application's single screen.
 *
 * <p>This class is the suite's only description of that region. It owns every selector the
 * region is addressed by and every wait condition used to observe it, and it exposes those
 * capabilities as intent-level operations - load the region, type a term, wait for a settled
 * result set, read the rendered names, inspect the empty state. Test classes describe
 * scenarios in those terms and never mention an element, a selector or a wait condition
 * themselves.</p>
 *
 * <h2>Binding surface</h2>
 *
 * <p>The region is addressed exclusively through the four automation attributes the frontend
 * publishes for it. That is a correctness requirement rather than a preference: the starter
 * page this region was added to already contains several unordered lists, list items,
 * second-level headings, paragraphs and a button, so a locator written against a tag name, a
 * class, an id or a document position could resolve to starter markup instead of Student
 * Search markup. Presentation classes are free to change; the four attribute values are not.
 * Nothing here binds to a tag name, a class, an id, an ancestor, a nesting relationship or a
 * position.</p>
 *
 * <h2>Cardinality of the four hooks</h2>
 *
 * <table border="1">
 *   <caption>Element counts the frontend guarantees per settled state</caption>
 *   <tr><th>Hook</th><th>Count</th><th>Produced by</th></tr>
 *   <tr><td>{@code student-search-input}</td><td>exactly 1, always</td><td>static markup</td></tr>
 *   <tr><td>{@code student-list}</td><td>exactly 1, always</td><td>static markup</td></tr>
 *   <tr><td>{@code student-item}</td><td>0..n, one per match</td><td>rendered per keystroke</td></tr>
 *   <tr><td>{@code no-students-found}</td><td>0 or 1</td><td>rendered per keystroke</td></tr>
 * </table>
 *
 * <h2>Three structural facts the waits are built on</h2>
 *
 * <ol>
 *   <li>The results list stays in the document in <em>every</em> settled state, including the
 *       one holding no rows, so it is the correct signal that the region has finished loading
 *       and an empty result set is never signalled by the list disappearing.</li>
 *   <li>The empty-state element is a <em>sibling</em> of the results list rather than a child
 *       of it. Row count and empty-state presence are therefore two independent locator
 *       queries, never a nesting, ancestry or position assertion.</li>
 *   <li>An entry that does not match is <em>removed from the document</em>, never retained and
 *       masked by a style rule. Element count is consequently the single source of truth for
 *       "how many students are showing", which is why the count conditions below are used in
 *       preference to any visibility-based condition - including for the case of no empty-state
 *       element at all.</li>
 * </ol>
 *
 * <h2>Freshness</h2>
 *
 * <p>No element reference is ever stored. The frontend rebuilds the whole results projection on
 * every keystroke, so a retained row would be detached from the document by the next one; every
 * operation below re-resolves what it needs from a locator at the moment it runs. The class
 * also holds no mutable static state, so an instance constructed against a brand new browser
 * session is always correct and instances never interfere with one another.</p>
 *
 * <h2>What this class deliberately does not do</h2>
 *
 * <p>It does not create or quit a browser, does not build a wait, does not read a system
 * property or any other configuration, does not navigate, and does not assert. The browser
 * lifecycle, the timeout budget and the origin belong to the shared test base that hands this
 * class a driver and a wait; expectations belong to the test class that calls it. That
 * separation is what keeps a scenario readable and keeps this file the only place a change to
 * the region's markup has to be absorbed.</p>
 *
 * @see #awaitLoaded()
 * @see #search(String)
 */
public class StudentSearchPage {

    /*
     * The four hook values below are a cross-project contract with the frontend:
     * `student-search-input` and `student-list` are written as static markup in
     * frontend/src/main.ts, and `student-item` and `no-students-found` are written dynamically
     * by frontend/src/studentSearch.ts. Nothing at build time couples the two projects, and
     * only a running Selenium suite detects drift between them, so renaming a hook means
     * editing the frontend and this file in the same commit. They are the only four selector
     * definitions in this Maven project, and they are declared exactly once, here.
     */

    /** The single search field. Typed into; never recreated by the frontend. */
    private static final By SEARCH_INPUT = By.cssSelector("[data-testid='student-search-input']");

    /** The persistent results list. Present in every settled state, empty or not. */
    private static final By STUDENT_LIST = By.cssSelector("[data-testid='student-list']");

    /** One rendered result row per matching student, in roster order. */
    private static final By STUDENT_ITEM = By.cssSelector("[data-testid='student-item']");

    /** The empty-state element, rendered only while nothing matches. */
    private static final By NO_STUDENTS_FOUND = By.cssSelector("[data-testid='no-students-found']");

    /**
     * The exact wording the frontend renders when a term matches no student.
     *
     * <p>Owned here rather than by a test class so the expectation exists once in the Java
     * project, mirroring the single definition on the frontend side. It is part of the same
     * cross-project contract as the four hooks above: changing the rendered wording means
     * changing this constant in the same commit.</p>
     */
    private static final String NO_STUDENTS_FOUND_TEXT = "No students found";

    /**
     * The browser session this page reads, supplied by the caller.
     *
     * <p>Used only to resolve the locators above. Typed as the interface so this page depends
     * on the WebDriver contract rather than on a particular browser, and never reassigned, so
     * one instance always speaks to one session.</p>
     */
    private final WebDriver driver;

    /**
     * The explicit wait this page synchronises on, supplied by the caller.
     *
     * <p>Every state transition observed below goes through it, and it is the only waiting
     * mechanism used: there is no global driver timeout, no fixed pause and no retry loop
     * anywhere in this class. Its timeout budget is the caller's to choose.</p>
     */
    private final WebDriverWait wait;

    /**
     * Binds this page to an already open browser session and an already configured wait.
     *
     * <p>Pure injection: nothing is navigated, nothing is waited for and no element is looked
     * up here, so constructing the page is free and can be done before the region exists.
     * {@link #awaitLoaded()} is the operation that establishes the region is ready.</p>
     *
     * <p>Both arguments are required and are expected to be the live session and wait owned by
     * the shared test base, which creates them per test method before any test body runs. They
     * are stored as given; supplying {@code null} for either leaves the page unusable and the
     * first operation called on it will fail.</p>
     *
     * @param driver the open browser session to read the region from
     * @param wait the explicit wait, already bound to {@code driver} and to a timeout budget
     */
    public StudentSearchPage(WebDriver driver, WebDriverWait wait) {
        this.driver = driver;
        this.wait = wait;
    }

    /**
     * Waits until the Student Search region is loaded and ready to be typed into.
     *
     * <p>Two conditions have to hold before a scenario can trust what it reads. The results
     * list must be in the document, which is the region's load signal precisely because the
     * frontend keeps that list present in every settled state - it never vanishes, not even
     * when nothing matches, so waiting on it can never be satisfied by a transient state.
     * The search field must additionally be interactable, which is the meaningful readiness
     * condition for an element about to receive keystrokes; merely existing in the document
     * would not guarantee that.</p>
     *
     * <p>Call this once at the start of a scenario. It is state-based throughout, so it returns
     * as soon as the region has settled rather than after a fixed delay, and it fails with a
     * timeout carrying the unmet condition if the region never appears.</p>
     *
     * @return this page, so a scenario can chain straight into an operation
     */
    public StudentSearchPage awaitLoaded() {
        wait.until(ExpectedConditions.presenceOfElementLocated(STUDENT_LIST));
        wait.until(ExpectedConditions.elementToBeClickable(SEARCH_INPUT));
        return this;
    }

    /**
     * Replaces the search term with {@code term}, exactly as a user would type it.
     *
     * <p>The field is cleared before the term is typed, so the value left behind is {@code term}
     * alone and never a concatenation with whatever an earlier step put there.</p>
     *
     * <p>Clearing on its own is deliberately not treated as a search. Measured against this
     * application, the driver's clear operation empties the field's value without raising the
     * change notification the feature filters on, so the projection would go on showing the
     * previous term's result while the field looked empty. The typed term is what the
     * application actually observes: every character notifies the feature, which re-reads the
     * field's complete value. A term therefore has to carry at least one character for the
     * result set to move, and an empty one is rejected outright rather than quietly leaving a
     * stale projection behind. A scenario wanting the whole roster back after a search can type
     * a term that normalizes away - a single space will do, because the feature trims before it
     * matches - and a scenario wanting the untouched initial state simply gets one, since every
     * test runs against a freshly opened session.</p>
     *
     * <p>Only real keystrokes are used. No key is pressed to "submit": the region contains no
     * form and no submit control, so filtering happens purely as the value changes, and sending
     * a return key would couple a scenario to semantics the application does not have. The
     * value is likewise never injected by script, because that would bypass the very
     * notification the feature listens for.</p>
     *
     * <p>The rendered result set is settled by the time this returns as far as the browser is
     * concerned - the feature filters synchronously while each keystroke is delivered, with no
     * debounce, timer or animation staging, and the final keystroke re-reads the complete value
     * - but a scenario should still express the outcome it expects through
     * {@link #awaitStudentCount(int)} or {@link #awaitEmptyStateCount(int)}, so that what it is
     * waiting for is stated rather than assumed.</p>
     *
     * @param term the term to type; must carry at least one character
     * @return this page, so a scenario can chain straight into a wait
     * @throws IllegalArgumentException if {@code term} is {@code null} or empty
     */
    public StudentSearchPage search(String term) {
        if (term == null) {
            throw new IllegalArgumentException("search term must not be null");
        }
        if (term.isEmpty()) {
            throw new IllegalArgumentException(
                    "search term must carry at least one character, because clearing the field "
                            + "alone does not notify the application and would leave the previous "
                            + "result set on screen; type a term that normalizes away, such as a "
                            + "single space, to restore the full roster");
        }

        // Re-resolved on every call rather than cached: correct even after a re-render, and the
        // clickable condition is what guarantees the field can actually receive the keystrokes.
        WebElement field = wait.until(ExpectedConditions.elementToBeClickable(SEARCH_INPUT));
        field.clear();
        field.sendKeys(term);
        return this;
    }

    /**
     * Waits until exactly {@code expected} student rows are rendered.
     *
     * <p>This is the operation that turns "I typed something" into "the result set has settled",
     * and it is expressed as a count because the frontend removes an entry that stops matching
     * instead of hiding it. The number of elements carrying the row hook is therefore the whole
     * truth about how many students are showing, with no need to ask any of them whether it is
     * displayed.</p>
     *
     * <p>Zero is a perfectly ordinary expectation here: it is the settled state of a term that
     * matches nobody, and it is reached by the rows being gone rather than by the surrounding
     * list disappearing.</p>
     *
     * @param expected the number of rows the caller expects; zero or more
     * @return this page, so a scenario can chain straight into a read
     * @throws IllegalArgumentException if {@code expected} is negative
     */
    public StudentSearchPage awaitStudentCount(int expected) {
        requireNonNegative(expected, "expected student count");
        wait.until(ExpectedConditions.numberOfElementsToBe(STUDENT_ITEM, expected));
        return this;
    }

    /**
     * Reads the rendered student names, in the order the document holds them.
     *
     * <p>Document order is roster order - the frontend appends one row per match while walking
     * the roster and never sorts, reverses or re-ranks - so the returned sequence is directly
     * comparable to an expected ordered list. Nothing is sorted, filtered or de-duplicated on
     * the way out, because doing any of those would quietly hide exactly the kind of regression
     * an ordering expectation exists to catch.</p>
     *
     * <p>Each name is trimmed, which keeps a scenario's expectations free of incidental
     * whitespace from the surrounding markup. Rows are resolved fresh on every call, so the
     * result reflects the current projection rather than an earlier one.</p>
     *
     * <p>This is a plain read with no wait of its own: pair it with
     * {@link #awaitStudentCount(int)} so the projection has demonstrably settled before it is
     * inspected.</p>
     *
     * @return the rendered names in document order, unmodifiable and possibly empty
     */
    public List<String> studentNames() {
        return driver.findElements(STUDENT_ITEM).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    /**
     * Waits until the empty state carries its exact expected wording, then returns what it shows.
     *
     * <p>The comparison is exact rather than a substring test, so a truncated, extended or
     * reworded message fails instead of passing on a partial match. The expected wording lives
     * in this class, which keeps it out of the scenarios and leaves one place to change when the
     * frontend's wording changes.</p>
     *
     * <p>The value returned is read back from the document rather than echoed from the
     * expectation, so a scenario asserting on it is asserting on something actually observed in
     * the browser. That read is safe to make immediately: the wait has just proven the element
     * is present with settled text, and the region does not change again on its own.</p>
     *
     * @return the empty state's rendered text, trimmed
     */
    public String awaitEmptyStateText() {
        wait.until(ExpectedConditions.textToBe(NO_STUDENTS_FOUND, NO_STUDENTS_FOUND_TEXT));
        return driver.findElement(NO_STUDENTS_FOUND).getText().trim();
    }

    /**
     * Waits until exactly {@code expected} empty-state elements are rendered.
     *
     * <p>The hook's cardinality is 0 or 1, so this is how a scenario states both halves of the
     * negative path: {@code 0} while students are showing, {@code 1} once a term matches
     * nobody. Expressing absence as a count of zero, rather than as an invisibility condition,
     * matches how the frontend behaves - the element is added and removed, never hidden - and
     * keeps a stale leftover from passing for absence.</p>
     *
     * @param expected the number of empty-state elements the caller expects; zero or one
     * @return this page, so a scenario can chain straight into a read
     * @throws IllegalArgumentException if {@code expected} is negative
     */
    public StudentSearchPage awaitEmptyStateCount(int expected) {
        requireNonNegative(expected, "expected empty-state count");
        wait.until(ExpectedConditions.numberOfElementsToBe(NO_STUDENTS_FOUND, expected));
        return this;
    }

    /**
     * Counts the empty-state elements currently rendered.
     *
     * <p>A plain read of the same 0-or-1 cardinality {@link #awaitEmptyStateCount(int)} waits
     * on, for a scenario that prefers to state the negative path as an explicit expectation on
     * a measured number. Absence is reported as {@code 0} from an ordinary lookup that finds
     * nothing, so no missing-element failure is ever raised and then swallowed to stand in for
     * a boolean.</p>
     *
     * <p>Pair it with a wait when the projection has just changed, so the number read is a
     * settled one.</p>
     *
     * @return the number of empty-state elements in the document, {@code 0} or {@code 1}
     */
    public int emptyStateCount() {
        return driver.findElements(NO_STUDENTS_FOUND).size();
    }

    /**
     * Rejects a negative element-count expectation before any waiting begins.
     *
     * <p>No element count can ever be negative, so such an expectation is unsatisfiable. Failing
     * immediately, and naming the argument, turns a caller's slip into an instant and explicit
     * error instead of spending the whole timeout budget only to report that a condition never
     * came true.</p>
     *
     * @param value the count supplied by the caller
     * @param description the argument's name, for the failure message
     * @throws IllegalArgumentException if {@code value} is negative
     */
    private static void requireNonNegative(int value, String description) {
        if (value < 0) {
            throw new IllegalArgumentException(description + " must not be negative, but was " + value);
        }
    }
}
