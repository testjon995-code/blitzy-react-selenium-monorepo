// These names and their order are asserted verbatim by the sibling Selenium suite
// in ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java. Nothing
// couples the two projects at build time, so change both sides in the same commit.

export type Student = {
  readonly id: number
  readonly name: string
}

// Readonly all the way down - the array cannot be reordered or resized, and neither
// field of an entry can be reassigned - because this single source of truth is handed
// out by reference whenever no term is being filtered on.
export const students: readonly Student[] = [
  { id: 1, name: 'John Doe' },
  { id: 2, name: 'Alice Smith' },
  { id: 3, name: 'Robert Brown' }
]
