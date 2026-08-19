package com.qa.tests;

import com.qa.base.BaseTest;
import com.qa.pages.StudentSearchPage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance scenarios for the Student Search region of the application's single screen.
 *
 * <p>Three scenarios cover the region's entire settled-state model, one per state the feature can
 * reach: the roster as the page loads, a term that narrows it to a single entry, and a term that
 * matches nobody. Each one states both halves of its state - how many student rows are rendered,
 * and whether the empty state is there - because either half on its own would pass in a state the
 * feature must never produce.</p>
 *
 * <h2>What this class deliberately does not contain</h2>
 *
 * <p>No selector, no element lookup, no wait condition, no origin, no timeout budget and no
 * browser setup. The session, the wait and the origin all arrive from the shared base this class
 * extends, which opens the application before each scenario and closes it afterwards whatever the
 * outcome; every reading of the page goes through the Student Search page object, which owns the
 * region's selectors and its wait conditions. What remains here is each scenario's intent and the
 * values it expects, which is exactly what should be reviewable in a test.</p>
 *
 * <h2>Waiting</h2>
 *
 * <p>Every scenario states the outcome it expects through the page object's {@code await}
 * operations before it reads anything. Those waits are this suite's only synchronisation: each
 * returns as soon as the region has settled into the expected state, and fails carrying the unmet
 * condition when it never does. No fixed pause is used anywhere, and no value is read from a
 * projection that has not first been shown to have settled.</p>
 *
 * <h2>Isolation</h2>
 *
 * <p>Each scenario runs against a session of its own, so the field always starts empty and the
 * roster always starts whole; no reset or cleanup between methods is needed, and none is
 * performed. Nothing is shared between the methods and none of them depends on another having
 * run, so they may execute in any order or individually.</p>
 *
 * <h2>Cross-project contract</h2>
 *
 * <p>The three names and their order are owned by the frontend roster module, and the empty-state
 * wording by the frontend search module. Nothing couples the two projects at build time, so this
 * suite is the only thing that detects drift between them. That is why every expectation below is
 * an exact comparison rather than a lenient one, and why a rename or a data change on either side
 * has to be made on both sides in the same commit.</p>
 *
 * @see StudentSearchPage
 */
public class StudentSearchTest extends BaseTest {

    /**
     * T-1: the region loads and renders the whole roster in declaration order.
     *
     * <p>The initial render is what is under test, so no term is typed here. Readiness is
     * established first, because a row count carries no meaning until the region is in the
     * document, and both counts are then waited on so the state has settled before either one is
     * read.</p>
     *
     * <p>The names are compared as an ordered sequence. Order is load-bearing: the frontend is
     * required to preserve its roster declaration order and never to sort, reverse or re-rank it,
     * and an order-insensitive comparison here would quietly stop protecting that.</p>
     */
    @Test
    void loadsTheRegionAndRendersTheWholeRosterInDeclarationOrder() {
        StudentSearchPage page = new StudentSearchPage(driver, wait);

        page.awaitLoaded()
                .awaitStudentCount(3)
                .awaitEmptyStateCount(0);

        assertEquals(
                List.of("John Doe", "Alice Smith", "Robert Brown"),
                page.studentNames(),
                "the whole roster should render, in declaration order, before any term is typed");
        assertEquals(
                0,
                page.emptyStateCount(),
                "no empty state should exist while student rows are rendered");
    }

    /**
     * T-2: the term {@code Alice} narrows the results to {@code Alice Smith} alone.
     *
     * <p>Matching is a case-insensitive substring test against the name, so this scenario proves
     * two things at once: the entry that matches is kept, and the two that do not are gone from
     * the document rather than merely styled out of the way. A single-element ordered comparison
     * keeps the expectation the same shape as T-1's.</p>
     */
    @Test
    void searchingForAliceNarrowsTheResultsToAliceSmithAlone() {
        StudentSearchPage page = new StudentSearchPage(driver, wait);

        page.awaitLoaded()
                .search("Alice")
                .awaitStudentCount(1)
                .awaitEmptyStateCount(0);

        assertEquals(
                List.of("Alice Smith"),
                page.studentNames(),
                "'Alice' should match 'Alice Smith' and no other roster entry");
        assertEquals(
                0,
                page.emptyStateCount(),
                "no empty state should exist while a student still matches the term");
    }

    /**
     * T-3: a term no student matches clears the rows and renders the empty state.
     *
     * <p>All three expectations matter. Rows could go away without the message arriving, the
     * message could arrive alongside stale rows, and the right number of elements could carry the
     * wrong wording, so the row count, the empty-state count and the empty-state text are each
     * stated. The first two are independent queries: the empty state is rendered as a sibling of
     * the results list, and that list stays in the document even when it holds nothing, so
     * neither count is ever inferred from the other's nesting or position.</p>
     *
     * <p>The wording is compared exactly, so a reworded, truncated or extended message fails
     * instead of passing on a partial match.</p>
     */
    @Test
    void searchingForATermNoStudentMatchesRendersTheEmptyState() {
        // 'zzzzzz' is a sentinel: it is chosen only because no name in the current roster
        // contains it. Should roster data ever collide with it, change the term and the roster
        // together, in the same commit.
        StudentSearchPage page = new StudentSearchPage(driver, wait);

        page.awaitLoaded()
                .search("zzzzzz")
                .awaitStudentCount(0)
                .awaitEmptyStateCount(1);

        assertEquals(
                List.of(),
                page.studentNames(),
                "no student row should remain for a term no roster entry matches");
        assertEquals(
                1,
                page.emptyStateCount(),
                "exactly one empty state should be rendered for a term no roster entry matches");
        assertEquals(
                "No students found",
                page.awaitEmptyStateText(),
                "the empty state should carry the exact wording the frontend renders");
    }
}
