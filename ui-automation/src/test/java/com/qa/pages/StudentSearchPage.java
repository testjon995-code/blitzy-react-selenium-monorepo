package com.qa.pages;

import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Page Object for the Student Search region of the application's single screen.
 *
 * <p>It owns the four automation attributes the region is addressed by and every wait
 * condition used to observe it, and exposes them as intent-level operations so a scenario never
 * names an element, a selector or a condition. Binding by attribute is a correctness requirement
 * rather than a preference: the starter page around this region contains other lists, list items,
 * second-level headings, paragraphs and a button, so a tag, class, id or positional locator
 * could resolve to starter markup instead.</p>
 *
 * <p>No element reference is kept. The frontend rebuilds the results projection on every
 * keystroke, so each operation re-resolves what it needs at the moment it runs, and the class
 * holds no mutable static state.</p>
 *
 * <p>The browser session, the timeout budget and the origin are supplied by the shared test
 * base; expectations belong to the calling test. This class creates nothing, reads no
 * configuration, navigates nowhere and asserts nothing.</p>
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

    private static final By SEARCH_INPUT = By.cssSelector("[data-testid='student-search-input']");

    private static final By STUDENT_LIST = By.cssSelector("[data-testid='student-list']");

    private static final By STUDENT_ITEM = By.cssSelector("[data-testid='student-item']");

    private static final By NO_STUDENTS_FOUND = By.cssSelector("[data-testid='no-students-found']");

    /**
     * The exact wording the frontend renders when a term matches no student.
     *
     * <p>Part of the same cross-project contract as the four hooks above, and duplicated by
     * design rather than by accident: the frontend renders this text, this constant is what the
     * empty-state wait below matches it against, and the T-3 scenario in
     * {@code StudentSearchTest} asserts it as its own expectation. A wording change therefore
     * means editing all three in the same commit.</p>
     */
    private static final String NO_STUDENTS_FOUND_TEXT = "No students found";

    private final WebDriver driver;

    private final WebDriverWait wait;

    /**
     * Binds this page to an already open browser session and an already configured wait.
     *
     * <p>Injection only: nothing is navigated, waited for or looked up here, so
     * {@link #awaitLoaded()} is what establishes that the region is ready. Both arguments are
     * stored as given, so a {@code null} is not rejected here and instead fails the first
     * operation that uses it.</p>
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
     * <p>Those two conditions are what readiness means here. The results list is present in
     * every settled state, so waiting on it can never be satisfied by a transient one, and the
     * search field has to be interactable rather than merely present before it can receive
     * keystrokes.</p>
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
     * <p>The field is cleared and then typed into, so the value left behind is {@code term}
     * alone rather than a concatenation with whatever an earlier step put there. An empty term is
     * rejected because it would not express a search: clearing raises the event the feature
     * filters on only when it actually changes the value, so on a field that is already empty the
     * call would drive no re-render at all while reading as though it had. A scenario wanting the
     * whole roster back can type a term that normalizes away - a single space will do, because
     * the feature trims before it matches.</p>
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
                    "search term must carry at least one character, because an empty term does "
                            + "not express a search: on a field that already holds nothing, "
                            + "clearing it changes no value and so raises no event, leaving "
                            + "nothing for the application to re-render; type a term that "
                            + "normalizes away, such as a single space, to restore the full "
                            + "roster");
        }

        // Clickability is resolved on each call, which is what guarantees the field is ready to
        // receive the keystrokes below rather than merely present.
        WebElement field = wait.until(ExpectedConditions.elementToBeClickable(SEARCH_INPUT));
        field.clear();
        field.sendKeys(term);
        return this;
    }

    /**
     * Waits until exactly {@code expected} student rows are rendered.
     *
     * <p>Expressed as a count because the frontend removes an entry that stops matching instead
     * of hiding it, so the number of elements carrying the row hook is the whole truth about how
     * many students are showing. Zero is an ordinary expectation: it is reached by the rows being
     * gone rather than by the surrounding list disappearing.</p>
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
     * <p>Document order is roster order, and nothing is sorted, filtered or de-duplicated on the
     * way out, because any of those would hide the kind of regression an ordering expectation
     * exists to catch. Each name is trimmed. There is no wait of its own here: pair it with
     * {@link #awaitStudentCount(int)} so the projection has settled first.</p>
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
     * reworded message fails instead of passing on a partial match. The awaited wording is the
     * constant declared above, which the T-3 scenario also states as its own expectation, so the
     * two have to agree. What comes back is the text the wait itself read out of the document -
     * neither an echo of the expectation nor a second lookup - so a scenario asserting on it is
     * asserting on the very value that satisfied the condition. No trimming is applied here
     * because {@code getText()} already reports rendered text with its surrounding whitespace
     * removed.</p>
     *
     * @return the empty state's rendered text, as the browser reported it
     */
    public String awaitEmptyStateText() {
        return wait.until(new ExpectedCondition<String>() {

            @Override
            public String apply(WebDriver browser) {
                try {
                    List<WebElement> rendered = browser.findElements(NO_STUDENTS_FOUND);
                    if (rendered.size() != 1) {
                        return null;
                    }

                    String text = rendered.get(0).getText();
                    return NO_STUDENTS_FOUND_TEXT.equals(text) ? text : null;
                } catch (StaleElementReferenceException rebuilt) {
                    // The projection was replaced between the lookup and the read, so there is
                    // nothing to conclude from this attempt; returning null polls again.
                    return null;
                }
            }

            @Override
            public String toString() {
                return "empty state to read exactly \"" + NO_STUDENTS_FOUND_TEXT + "\"";
            }
        });
    }

    /**
     * Waits until exactly {@code expected} empty-state elements are rendered.
     *
     * <p>This is how a scenario states both halves of the negative path: {@code 0} while students
     * are showing, {@code 1} once a term matches nobody. Expressing absence as a count of zero
     * matches how the frontend behaves - the element is added and removed, never hidden - and
     * keeps a stale leftover from passing for absence.</p>
     *
     * @param expected the number of empty-state elements the caller expects; any non-negative
     *        value is accepted, but the frontend renders at most one, so an expectation above one
     *        can only ever exhaust the wait
     * @return this page, so a scenario can chain straight into a read
     * @throws IllegalArgumentException if {@code expected} is negative
     */
    public StudentSearchPage awaitEmptyStateCount(int expected) {
        requireNonNegative(expected, "expected empty-state count");
        wait.until(ExpectedConditions.numberOfElementsToBe(NO_STUDENTS_FOUND, expected));
        return this;
    }

    /**
     * Counts the empty-state elements currently in the document.
     *
     * <p>An immediate read with no wait of its own, so pair it with
     * {@link #awaitEmptyStateCount(int)} after a transition to be sure the number is settled.
     * Absence is reported as {@code 0} from an ordinary lookup that finds nothing, rather than by
     * catching a missing-element failure.</p>
     *
     * @return the number of empty-state elements found at the moment of the call
     */
    public int emptyStateCount() {
        return driver.findElements(NO_STUDENTS_FOUND).size();
    }

    private static void requireNonNegative(int value, String description) {
        if (value < 0) {
            throw new IllegalArgumentException(description + " must not be negative, but was " + value);
        }
    }
}
