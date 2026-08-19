# Student Search — Vite frontend with Selenium UI automation

This repository holds two projects and one feature that spans both of them. `frontend/` is a
[Vite](https://vite.dev/) application written in **vanilla TypeScript**: it builds its page through
direct DOM manipulation and deliberately uses **no UI framework** — there is no React, no JSX, no
component library and no state-management library, and the project declares no runtime dependency at
all. `ui-automation/` is a Maven project that drives Chrome with
[Selenium](https://www.selenium.dev/) 4 and the JUnit Jupiter programming model to verify what the
frontend actually renders. The feature they share is **Student Search**: a region of the frontend's
single page that filters a small fixed roster as you type, plus three Selenium scenarios that assert
the resulting DOM through four stable `data-testid` hooks. The two projects have no shared build and
no generated glue — the hook strings and the student names are the whole contract between them.

## Repository layout

```text
.
├── README.md                                  this document
├── .gitignore                                 ignores node_modules/, dist/, ui-automation/target/, IDE and OS files
├── frontend/                                  Vite + vanilla-TypeScript application (no UI framework)
│   ├── index.html                             the single document: <title>frontend</title>, the #app mount, the /src/main.ts entry
│   ├── package.json                           the dev / build / preview scripts; TypeScript and Vite are the only dependencies, both dev-only
│   ├── package-lock.json                      the locked toolchain (lockfileVersion 3)
│   ├── tsconfig.json                          type-check settings; only src/ is checked
│   ├── vite.config.ts                         pins the dev server to port 5173 and refuses to fall back
│   ├── public/                                static files served as they are (favicon, icons)
│   └── src/
│       ├── main.ts                            renders the whole page, then wires the counter and Student Search
│       ├── students.ts                        single source of truth for the roster (the Student type and the students array)
│       ├── studentSearch.ts                   term normalization, filtering, initial render and input handling
│       ├── counter.ts                         the starter counter button, unchanged
│       ├── style.css                          the only stylesheet: design tokens, layout, and the Student Search rules
│       └── assets/                            images imported by main.ts
└── ui-automation/                             Maven + Selenium UI test project
    ├── pom.xml                                Java 17, Selenium 4.46.0, JUnit Jupiter 6.1.2, Compiler plugin 3.14.1, Surefire 3.5.3
    └── src/test/java/com/qa/
        ├── config/TestConfig.java             resolves and validates baseUrl, headless and timeoutSeconds
        ├── base/BaseTest.java                 per-test Chrome lifecycle: options, explicit wait, navigation, teardown
        ├── pages/StudentSearchPage.java       the only home of the four Student Search locators
        └── tests/
            ├── StudentSearchTest.java         the three Student Search scenarios (T-1, T-2, T-3)
            └── GoogleTest.java                one local document-title smoke test
```

## Prerequisites

| Tool | Requirement | Verify with |
|---|---|---|
| Node.js | `^20.19.0 \|\| >=22.12.0` — the engine range Vite declares in `frontend/package-lock.json`. The 22.x line, from **22.12.0** upwards, is the branch this repository is set up against | `node --version` |
| npm | Must understand `lockfileVersion` 3; use the npm bundled with that Node installation | `npm --version` |
| JDK | Exactly **Java 17** — `maven.compiler.source` and `maven.compiler.target` are both `17` in `ui-automation/pom.xml` | `java -version`, `javac -version` |
| Maven | No wrapper and no version pin live in the repository. **3.8.7** is the version this project is set up against; the declared plugins need Maven 3.6.3 or newer | `mvn -version` |
| Google Chrome | A locally installed Chrome that Selenium Manager can match a driver to. Chrome is the only supported browser | your installed Chrome version, plus a browser session that starts |

Each tool has to be on `PATH` in the shell that runs the command, and `JAVA_HOME` has to point at the
JDK 17 installation Maven should compile and fork with. Nothing else is required: Maven resolves
Selenium and JUnit from `ui-automation/pom.xml`, and Selenium 4.46.0 already ships the Chrome
bindings, the explicit-wait support classes and **Selenium Manager**, which resolves the matching
ChromeDriver on its own. No separate driver download or driver-manager library is used.

## The Student Search feature

- **Where it lives.** Student Search is an added `<section id="student-search">` on the frontend's
  single page, alongside the existing starter content. It contains an `<h2>Student Search</h2>`
  heading, a visible `<label>` reading `Search students by name` bound to one text input, and a
  results area holding a list element that is present in every state.
- **How filtering works.** Each `input` event filters synchronously: the term is normalized with
  `trim().toLowerCase()` and tested as a case-insensitive substring against the student **name**
  only. Matches keep the roster's declaration order. There is no debounce, timer, animation or
  promise, so the document you read straight after typing is always a settled state rather than an
  intermediate one.
- **Clearing the field.** A term that is empty or whitespace-only restores the full roster.
- **No form.** The input sits outside any `<form>` and there is no submit button, so pressing Enter
  does nothing and the page never reloads.
- **Rows are removed, not hidden.** A student that does not match is taken out of the document.
  Nothing is kept and masked with `display`, `visibility` or `opacity`, so counting the rendered
  rows always yields the true match count.
- **Empty state.** When a non-empty term matches nothing, exactly one paragraph carrying the text
  `No students found` is rendered as a *sibling* of the results list. The list itself stays in the
  document even while it holds no rows, which is what automation waits on to know the region has
  loaded.
- **Nothing else changes.** The starter counter button and the links on the page keep working.

### States and the scenarios that cover them

| State | Value in the search field | `student-item` count | `no-students-found` count | Covered by |
|---|---|---|---|---|
| Full roster (initial load) | empty | 3 | 0 | T-1 |
| Match | `Alice` | 1 (`Alice Smith`) | 0 | T-2 |
| No match | `zzzzzz` | 0 | 1 (`No students found`) | T-3 |

`zzzzzz` is a sentinel chosen only because no name in the current roster contains it. If the roster
ever grows a name that collides with it, change the sentinel in
`ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java` together with that data change.

## Selector contract

The automation binds to four `data-testid` attributes and to nothing else — never to a tag name, a
class, an id, the nesting or the position of an element. That is what lets the markup and the styling
change without breaking a test.

| `data-testid` | Produced by | Cardinality | Consumed by |
|---|---|---|---|
| `student-search-input` | static markup in `frontend/src/main.ts` | exactly 1 | `StudentSearchPage.SEARCH_INPUT` |
| `student-list` | static markup in `frontend/src/main.ts` | exactly 1 in every settled state (stays in the DOM even when empty) | `StudentSearchPage.STUDENT_LIST` |
| `student-item` | dynamic render in `frontend/src/studentSearch.ts` | 0…n, one per match, in roster order | `StudentSearchPage.STUDENT_ITEM` |
| `no-students-found` | dynamic render in `frontend/src/studentSearch.ts` | 0 or 1, only when a non-empty term matches nothing | `StudentSearchPage.NO_STUDENTS_FOUND` |

The values asserted on both sides are equally part of the contract:

| Value | Owned by | Also expected by |
|---|---|---|
| The roster `John Doe`, `Alice Smith`, `Robert Brown`, in that order | `frontend/src/students.ts` | `ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java` |
| The empty-state wording `No students found` | `frontend/src/studentSearch.ts` | `ui-automation/src/test/java/com/qa/tests/StudentSearchTest.java` |
| The document title `frontend` | `frontend/index.html` | `ui-automation/src/test/java/com/qa/tests/GoogleTest.java` |

**Change rule.** Renaming a hook, editing a student name or rewording the empty state means changing
the frontend and the Java automation **in the same commit**. Nothing but a running Selenium suite
detects drift between the two projects — there is no shared module, no code generation and no
compile-time link that would catch it for you.

## Running the frontend

Use two terminals. This is Terminal 1, and it stays open for as long as you want to run the suite.

```bash
cd frontend
npm install
npm run build
npm run dev
```

- `npm install` installs exactly what `frontend/package-lock.json` already resolves — TypeScript
  6.0.3 and Vite 8.2.1, both dev-only. It adds nothing.
- `npm run build` is `tsc && vite build`, so the build **is** the frontend's type-check gate: a type
  error fails it before Vite bundles anything. There is no separate lint, unit-test or coverage step
  in this project.
- `npm run dev` serves the app at <http://localhost:5173>. The port is pinned in
  `frontend/vite.config.ts` with `strictPort: true`, so when 5173 is already taken Vite **fails to
  start instead of quietly moving to the next free port**. That is deliberate: the automation's
  default `baseUrl` targets 5173, and a silent port change would point it at a dead origin.
- `npm run preview` (`vite preview`) also exists, to serve the output of `npm run build` rather than
  the dev server. The Selenium suite is happy with either, as long as the origin you pass it is the
  one actually being served.

## Running the Selenium suite

This is Terminal 2. **The frontend must already be serving before Maven starts** — the repository
contains no orchestrator, and nothing in the Maven build will start, wait for or health-check the
application for you.

```bash
cd ui-automation
mvn clean test -DbaseUrl=http://localhost:5173
```

A healthy run ends with exit status 0 and a Surefire summary of **4 tests, 0 failures, 0 errors,
0 skipped**: three Student Search scenarios in `StudentSearchTest` (T-1, T-2, T-3) and one local
document-title smoke test in `GoogleTest`. Treat those numbers as the contract for the run — a
summary reporting fewer tests means something was not discovered rather than that less work was
needed. Surefire is configured with `failIfNoTests=true` in `ui-automation/pom.xml`, so a build that
discovers no test at all fails instead of reporting a green, empty run.

### Configuration

All three settings are ordinary JVM system properties. Maven forwards `-D` user properties to the
forked test JVM, so you configure a run entirely from the command line — **no source file, `pom.xml`,
`package.json` or lockfile ever needs editing to build, configure or run either project.**
`ui-automation/src/test/java/com/qa/config/TestConfig.java` is the single owner of the defaults and
of the validation; the POM deliberately does not repeat them, so there is exactly one place a default
can come from.

| Property | Default | Accepted values and validation |
|---|---|---|
| `baseUrl` | `http://localhost:5173` | Trimmed. Must be an absolute `http` or `https` URL carrying a host. Absent or blank falls back to the default; a malformed value fails fast, before any browser navigation |
| `headless` | `true` | Trimmed. Strictly `true` or `false`; anything else fails fast |
| `timeoutSeconds` | `10` | Trimmed. Must be a positive integer; zero, a negative number or a non-number fails fast |

Optional diagnostic overrides:

- `-Dheadless=false` — watch the browser locally instead of running headless.
- `-DtimeoutSeconds=20` — raise the budget an explicit wait may spend on a DOM state, for slow
  hardware.
- `-Dtest=StudentSearchTest` — isolate the new scenarios while debugging one of them. Useful, but not
  a substitute for the full suite: **rerun `mvn clean test -DbaseUrl=http://localhost:5173` before
  calling the work done.**

### How the suite is put together

- `com.qa.base.BaseTest` owns the browser lifecycle. Every test gets a **fresh Chrome session**,
  created in `@BeforeEach` with a fixed 1280×900 window, `--headless=new` when `headless` is true,
  and an explicit `WebDriverWait` built from `timeoutSeconds`; it then navigates to `baseUrl`. The
  `@AfterEach` teardown always quits the session, including when the test failed, so no browser
  process is left behind.
- `com.qa.pages.StudentSearchPage` is the only place the four locators are declared, and it exposes
  intent-level operations (wait for the region, type a term, wait for a row count, read the rendered
  names, read the empty-state text) instead of exposing elements. Waits are **state-based**
  `WebDriverWait` conditions on the DOM — the suite contains no fixed-duration sleep and no retry
  loop.
- `com.qa.tests.StudentSearchTest` holds T-1, T-2 and T-3 and no locator of its own.
- `com.qa.tests.GoogleTest` is a local smoke test: it constructs no driver, names no URL and only
  asserts that the configured origin serves the document title `frontend`. The class keeps its
  historical name so its history stays intact; nothing in the suite contacts an external site.

## Running as an unprivileged user (required)

**Run both terminals as an ordinary, non-root operating-system account.**

Chrome refuses to start as `root` while its sandbox is enabled, and the committed browser options
contain **only** headless mode and the fixed window size. There is no `--no-sandbox`, no
certificate-verification bypass and no generic pass-through that would let an arbitrary Chrome
argument be injected from the command line — the sandbox stays on, by design. So a run that only
succeeds as root, or only with a sandbox bypass supplied from outside the repository, is **not** a
supported way to verify this project.

If your shell happens to be root — a container, for example — switch to an unprivileged account
before running the suite, rather than weakening the browser:

```bash
sudo -u <unprivileged-user> -H bash -lc "cd ui-automation && mvn clean test -DbaseUrl=http://localhost:5173"
```

That account needs read access to the checkout, a writable Maven repository and a writable Selenium
Manager cache in its own home directory. Headless Chrome needs no X display.

## Troubleshooting

| Symptom | Likely cause | What to do |
|---|---|---|
| `npm run dev` exits complaining that port 5173 is already in use | Something else already holds the port, and `strictPort: true` makes that fatal on purpose | Stop the other server, or free the port, and start again. The port is pinned because the suite defaults to it; if you genuinely have to serve the app on another origin, leave the pin alone and point the suite at that origin with `-DbaseUrl=…` instead |
| Maven run fails immediately with a connection refused, or with `ERR_CONNECTION_REFUSED` in the browser | Terminal 1 is not serving, or the origin passed with `-DbaseUrl` is not the origin actually being served | Confirm <http://localhost:5173> answers in a browser or with `curl -I http://localhost:5173`, then rerun the suite with a `-DbaseUrl` that matches it exactly, scheme and port included |
| Every test fails with `session not created: Chrome instance exited`, or with a message naming the sandbox | The suite is running as `root`, and the committed options keep the sandbox enabled, which Chrome refuses to combine | Rerun as an ordinary user, as described above. Do not add a sandbox bypass |
| The first run stalls or fails while obtaining a driver | Selenium Manager needs outbound network access on a cold machine to fetch a ChromeDriver matching the installed Chrome | Give the run network access once. Afterwards it works from the local Selenium Manager cache, so keep that cache writable by the account running the suite, and expect a fresh fetch after Chrome updates |
| Compilation fails with an unsupported class-file or release error | Maven is not using JDK 17 | Check `java -version` and `javac -version` both report 17, and that `JAVA_HOME` points at that same JDK 17 — Maven forks with `JAVA_HOME`, not with whatever is first on `PATH` |
| `GoogleTest` fails with an assertion about the title | `frontend/index.html` no longer declares the title `frontend` | Either restore the title or update the expected value in `GoogleTest`, in the same change — the two are one contract |
| Waits time out on a slow or heavily loaded machine even though the page looks right | The default 10-second budget for a DOM state is too tight for that hardware | Rerun with `-DtimeoutSeconds=20`. If it only passes with a very large budget, investigate the machine rather than raising the number further |
| Every test errors immediately with `Invalid system property …` and no browser ever opens | `TestConfig` validated an override and rejected it — a `baseUrl` that is not an absolute `http`/`https` URL, a `headless` value that is not exactly `true` or `false`, or a `timeoutSeconds` that is not a positive integer | Fix the `-D` value; the message names the property and shows an accepted example. `BaseTest` reads all three settings before it launches Chrome, so failing here is intentional: a typo cannot silently become a misleading test failure |
| The suite reports fewer than 4 tests but still succeeds | Tests were filtered out, for example by a leftover `-Dtest=…` | Rerun the bare command. `failIfNoTests=true` catches a completely empty run, but only reading the summary catches a partial one |

## Scope and limitations

Known limitations, worth understanding before you rely on this setup:

- **The cross-project contract is a convention, not a compiler.** The four hook strings, the three
  student names and the empty-state wording are duplicated in TypeScript and in Java. Only a running
  Selenium suite detects drift.
- **The frontend has to be serving first.** There is no orchestrator, no health-check wait and no
  Maven-managed server; ordering the two terminals is your job.
- **Selenium Manager may need the network.** On a cold machine it downloads a matching driver; from
  then on the run depends on the local cache and the installed Chrome version.
- **Chrome only.** No other browser is configured or supported here.
- **No frontend test harness.** Frontend verification is the `tsc` type-check inside `npm run build`
  plus this Selenium suite. There is no unit-test runner, no coverage tooling and no linter config.
- **`GoogleTest` is a historical name.** Its behaviour is a local document-title smoke test; the file
  and class keep their original name so the change stays a modification rather than a delete plus an
  add. If `frontend/index.html` changes its `<title>`, the assertion changes with it.

Deliberately not part of this feature, so nobody goes looking for it:

- No create, edit, delete or detail flow for a student; no sorting, no pagination, no persistence and
  no browser storage. The roster is fixed, in-memory data.
- No search on anything other than the name, and no reflection of the term into the URL.
- No backend, API, database, ORM, migration, authentication or session handling.
- No React or other UI framework, no state-management library, and no added npm package, Maven
  dependency or driver-manager library.
- No CI pipeline, container image, deployment step, reporting plugin, screenshot or video capture,
  Selenium Grid, remote WebDriver or cross-browser matrix.

## Keeping both sides in sync

When you change the feature, keep these in mind — they are the conventions the current code follows:

- Change a hook string, a student name or the empty-state wording in the frontend **and** in the Java
  automation in the same commit. Nothing else will tell you they diverged.
- Keep the roster in `frontend/src/students.ts`. No other frontend module hard-codes a student name.
- Keep configuration defaults in `ui-automation/src/test/java/com/qa/config/TestConfig.java` only,
  and let callers override them with `-D` properties. Do not add a second default to the POM.
- Keep all four locators in `ui-automation/src/test/java/com/qa/pages/StudentSearchPage.java`. A test
  class should read intent-level page operations, never a locator.
- Wait on state with `WebDriverWait`, never on the clock, and let `BaseTest` own the driver so every
  test keeps its fresh session and its unconditional teardown.
- Render dynamic and user-controlled values with `document.createElement` and `textContent`, never by
  assigning markup, so a search term can never be parsed as HTML.
- Style inside the existing `#student-search` scope using the custom properties already defined in
  `frontend/src/style.css` and the existing `max-width: 1024px` breakpoint. Do not hard-code a colour
  or introduce another breakpoint.
- Never hide an unmatched row with `display`, `visibility` or `opacity`; remove it, so the rendered
  row count stays truthful.

