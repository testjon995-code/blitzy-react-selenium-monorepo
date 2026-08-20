package com.qa.tests;

import com.qa.base.BaseTest;
import com.qa.pages.StudentSearchPage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The three Student Search acceptance scenarios, one test method each.
 *
 * <p>T-1 loads the region and expects the whole roster in declaration order; T-2 searches for
 * {@code Alice} and expects that one match alone; T-3 searches for a term no student matches and
 * expects no rows plus the exact empty-state wording.</p>
 *
 * <p>Every scenario states its expectations as values - names, counts and wording - and reaches
 * the page through {@link StudentSearchPage}, so no locator, hook string or wait condition appears
 * here. The browser session comes from {@link BaseTest}, one per test method.</p>
 */
public class StudentSearchTest extends BaseTest {

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

    @Test
    void searchingForATermNoStudentMatchesRendersTheEmptyState() {
        // 'zzzzzz' is a sentinel: it cannot match the current roster. Update this term in the
        // same change if future roster data ever contains it.
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
