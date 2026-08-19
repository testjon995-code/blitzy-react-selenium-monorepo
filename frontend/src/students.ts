/**
 * Single source of truth for the Student Search roster.
 *
 * This module is pure data. It imports nothing, touches no DOM, reads no
 * storage, performs no network access and derives nothing from the clock, so it
 * yields identical values on every load. `studentSearch.ts` is its only
 * consumer, and no other frontend module may hard-code a student name.
 *
 * The roster is also one half of a cross-project contract: the sibling
 * Maven/Selenium project asserts these exact strings, in this exact order, in
 * `ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java`.
 *
 * - T-1 expects all three names rendered in the declaration order below.
 * - T-2 searches `Alice` and expects `Alice Smith` as the sole match, so no
 *   other name may contain `alice` case-insensitively.
 * - T-3 searches `zzzzzz` and expects the empty state, so no name may contain
 *   that sentinel.
 *
 * Only a running Selenium suite detects drift between the two projects. Any
 * edit to a name, to the order, or to the length of this list must therefore be
 * made together with the matching Java assertions, in the same commit.
 */

/**
 * One roster entry.
 *
 * Declared as a type alias rather than an interface on purpose: a type alias
 * cannot be re-opened by declaration merging, so the shape stays exactly these
 * two members. `id` gives the caller a stable identity for an entry, and `name`
 * is the only field Student Search filters on.
 */
export type Student = {
  id: number
  name: string
}

/**
 * The complete roster, written out as literals in the order it must render.
 *
 * Order is contract rather than preference: the search projection preserves
 * this declaration order and never sorts, shuffles or reverses. Treat the array
 * and its entries as read-only: derive a filtered projection instead of
 * mutating in place. The explicit `Student[]` annotation makes a shape mistake
 * fail at the `tsc` stage of `npm run build` instead of in the browser.
 */
export const students: Student[] = [
  { id: 1, name: 'John Doe' },
  { id: 2, name: 'Alice Smith' },
  { id: 3, name: 'Robert Brown' }
]
