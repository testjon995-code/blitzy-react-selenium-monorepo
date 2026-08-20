/**
 * Student Search: term normalization, roster filtering and result rendering.
 *
 * `main.ts` owns the field, the results host and the persistent list. This module
 * replaces only the list's children and the empty-state sibling, which is what
 * keeps the field's value, focus and caret intact while the user types.
 *
 * It is the sole producer of the two dynamic automation hooks: `student-item`, one
 * per match in roster order, and `no-students-found`, at most one and a sibling of
 * the list rather than a child. A name that stops matching is removed from the
 * document rather than masked by a style rule, so a row count is the true match
 * count. Nothing couples this module to the sibling Selenium project at build time,
 * so renaming a hook or rewording the empty state means editing
 * `StudentSearchPage.java` and `StudentSearchTest.java` under
 * `ui-automation/src/test/java/com/qa` in the same commit.
 *
 * Rendered dynamic values are written with `textContent`, never as markup. The
 * search term is compared against roster names and is never projected into the
 * document at all.
 */

import { students } from './students.ts'
import type { Student } from './students.ts'

export function setupStudentSearch(
  input: HTMLInputElement,
  list: HTMLUListElement,
  resultsHost: HTMLElement
) {
  const findMatches = (term: string): readonly Student[] => {
    const normalized = term.trim().toLowerCase()
    if (normalized === '') {
      // The roster itself, by reference rather than as a copy. Safe because the array
      // and its entries are declared readonly at their source, so no caller of this
      // module can reach through the returned value and change the data.
      return students
    }
    return students.filter((student) => student.name.toLowerCase().includes(normalized))
  }

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

    // Zero matches unambiguously means the no-match state: the roster is never empty
    // and a term that normalizes to nothing returns all of it.
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
