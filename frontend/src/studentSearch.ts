/**
 * Student Search: term normalization, roster filtering, result rendering and
 * input handling for the Student Search region of the single starter screen.
 *
 * Responsibilities
 * - Normalize the raw search term and select the roster entries it matches.
 * - Project those matches into the results markup the caller already owns.
 * - Keep that projection in step with the field on every keystroke.
 *
 * This module carries no data of its own. Every name it renders comes from
 * `students.ts`, the single source of truth for the roster, so adding, removing
 * or renaming an entry there needs no edit here.
 *
 * ## Ownership boundary
 *
 * The caller (`main.ts`) owns the static markup. The labelled text field, the
 * results host and the persistent results list all exist in the page before
 * this module runs, and this module never creates, replaces or re-parents any
 * of the three. It rebuilds only the contents of the results projection, which
 * is precisely what keeps the field's value, focus and caret position intact
 * while the user types. Nothing outside the three elements handed in is
 * touched, so the module has no side effect beyond the DOM it is given.
 *
 * ## Cross-project DOM contract
 *
 * This module is the sole producer of the two dynamic automation hooks. The
 * sibling Maven/Selenium project binds to these attributes only, never to tag
 * names, classes, ids, nesting or position, so presentation may change freely
 * without breaking a test.
 *
 * - `student-item` — one element per rendered match, in roster declaration
 *   order, cardinality 0..n. Automation counts these to read the true match
 *   count, so an entry that does not match is removed from the document
 *   outright rather than being retained and masked by a style rule.
 * - `no-students-found` — cardinality 0 or 1. Present only when a non-empty
 *   term matches nothing, and always a sibling of the results list rather than
 *   a child of it.
 *
 * The results list itself stays in the document in every settled state,
 * including when it holds no rows at all, because automation waits on it to
 * confirm the region has finished loading.
 *
 * Only a running Selenium suite detects drift between the two projects.
 * Renaming a hook or reworking the empty-state wording therefore means editing
 * `ui-automation/src/test/java/com/qa/pages/StudentSearchPage.java` and
 * `ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java` in the same
 * commit.
 *
 * ## Settled states
 *
 * - Empty or whitespace-only field: every roster entry renders, in declaration
 *   order, and no empty-state element exists.
 * - Term matching one entry: exactly one row renders and no empty-state
 *   element exists.
 * - Term matching nothing: no rows render and exactly one empty-state element
 *   exists as the list's sibling.
 *
 * ## Determinism
 *
 * Every update runs synchronously inside the `input` event. There is no
 * debounce, no timer, no animation-frame staging, no promise and no transition,
 * so an automated client that types and immediately reads the document always
 * observes a settled result set rather than an intermediate one.
 *
 * ## Safe rendering
 *
 * Dynamic nodes are built exclusively with `document.createElement`,
 * `setAttribute` and `textContent`. Roster values and the user's term are
 * therefore written as text nodes and never parsed as markup, which removes
 * any injection path through the search field. The sibling counter module
 * assigns a markup string to build its own fixed label, but that is a static
 * template of its own making and is deliberately not a precedent for the
 * data-derived and user-controlled values handled here.
 */

import { students } from './students.ts'
import type { Student } from './students.ts'

/**
 * Wire the Student Search region and render its initial state.
 *
 * Mirrors the setup-function idiom already established by `counter.ts`: take
 * the elements to drive, define a nested renderer that closes over them,
 * register exactly one listener, then invoke the renderer once so the region is
 * populated before the user interacts with it. That final call is what makes
 * the on-load state deterministic rather than empty.
 *
 * The parameter order is part of the contract with the call site in `main.ts`.
 *
 * @param input The search field. Read for its current term and observed for
 *   the `input` event. Never recreated, and its value is never written here,
 *   so typing preserves focus and caret position.
 * @param list The persistent results list. Only its children are replaced.
 *   The element itself is never removed, replaced or recreated.
 * @param resultsHost The container holding the results list. Receives the
 *   empty-state element as a sibling of that list when nothing matches.
 */
export function setupStudentSearch(
  input: HTMLInputElement,
  list: HTMLUListElement,
  resultsHost: HTMLElement
) {
  /**
   * Select the roster entries matching a term, preserving declaration order.
   *
   * Normalization trims surrounding whitespace and folds case, so a padded or
   * differently cased term behaves exactly like its tidy equivalent. Matching
   * is a plain case-insensitive substring test against the `name` field only.
   * No other field participates, and the term is never compiled into a pattern,
   * so a value carrying regular-expression metacharacters is matched literally
   * instead of altering the search.
   *
   * A term that normalizes to nothing restores the full roster. `filter`
   * derives a new array and leaves the imported roster untouched, so ordering
   * is inherited from the declaration and is never sorted, reversed or
   * re-ranked. The full-roster branch returns the imported array directly.
   * That is safe because this helper is closure-local rather than exported and
   * its only consumer reads the result without mutating it, so the shared
   * reference cannot escape or be modified.
   *
   * @param term The raw field value.
   * @returns The matching entries, in roster declaration order.
   */
  const findMatches = (term: string): Student[] => {
    const normalized = term.trim().toLowerCase()
    if (normalized === '') {
      return students
    }
    return students.filter((student) => student.name.toLowerCase().includes(normalized))
  }

  /**
   * Rebuild the results projection for a term.
   *
   * Each pass clears the list's children, drops any empty-state element left
   * by an earlier pass, then appends either one row per match or a single
   * empty-state element. Exactly one of those two outcomes is ever present,
   * and neither can survive into a later state as a stale leftover. Removing
   * the previous empty-state element first is what keeps a no-match term
   * followed by a matching term from showing both at once.
   *
   * Testing only for an absence of matches is sufficient as well as correct: a
   * term that normalizes to nothing returns the whole roster, and the roster is
   * never empty, so reaching zero matches already implies the user supplied a
   * non-empty term. No further guard is needed, and adding an unused one would
   * fail the compiler's unused-declaration checks.
   *
   * @param term The raw field value to project.
   */
  const render = (term: string) => {
    const matches = findMatches(term)

    list.replaceChildren()

    // The selector below and the attribute written further down name the same
    // hook and must be kept in agreement, otherwise empty-state elements would
    // accumulate across renders and break the expected cardinality.
    const staleEmptyState = resultsHost.querySelector('[data-testid="no-students-found"]')
    if (staleEmptyState !== null) {
      staleEmptyState.remove()
    }

    if (matches.length === 0) {
      const emptyState = document.createElement('p')
      emptyState.setAttribute('data-testid', 'no-students-found')
      emptyState.textContent = 'No students found'
      resultsHost.append(emptyState)
      return
    }

    for (const student of matches) {
      const item = document.createElement('li')
      item.setAttribute('data-testid', 'student-item')
      item.textContent = student.name
      list.append(item)
    }
  }

  input.addEventListener('input', () => render(input.value))
  render(input.value)
}
