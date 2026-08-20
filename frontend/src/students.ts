// These names and their order are asserted verbatim by the sibling Selenium suite
// in ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java. Nothing
// couples the two projects at build time, so change both sides in the same commit.

export type Student = {
  id: number
  name: string
}

// Declared readonly so this single source of truth cannot be mutated by a consumer:
// the array is handed out by reference when no term is being filtered on.
export const students: readonly Student[] = [
  { id: 1, name: 'John Doe' },
  { id: 2, name: 'Alice Smith' },
  { id: 3, name: 'Robert Brown' }
]
