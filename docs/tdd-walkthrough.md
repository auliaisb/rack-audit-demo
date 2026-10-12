# TDD walkthrough: the "overdue" rule

A real red → green → refactor cycle from this repo, small enough to show on stage in about 8 minutes. Each step is a git tag, so you can `git checkout` any of them.

| Tag | Step | State of the suite |
|---|---|---|
| `tdd-1-red` | tests + a do-nothing stub | 3 of 8 new tests fail |
| `tdd-2-green` | simplest implementation | all 40 pass |
| `tdd-3-refactor` | cleaner code, same tests | all 40 pass, plus UI wiring |

Run the Kotlin tests with `cd android && ./gradlew testDebugUnitTest`. (When this was recorded, the suite was run on a plain JVM with the Kotlin compiler and JUnit directly, because Gradle was not available.)

## 0. The requirement, in words (this is what you give the AI)
> A task is overdue when the rep can still act on it (assigned or rejected) and the due moment has passed. Once submitted it is never overdue. Exactly at the due moment is not overdue. No due date, or an unknown status: not overdue. "Days overdue" rounds up: 1 ms late is 1 day, exactly 24 h late is still 1 day, 24 h + 1 ms is 2.

The boundary sentences matter most. They are where AI-written code is usually wrong, and they become test cases.

## 1. RED — write the tests first
`OverdueRuleTest.kt`, 8 tests, written before any implementation.

First run, with **no** `OverdueRule` at all:
```
OverdueRuleTest.kt:25:20: error: unresolved reference 'OverdueRule'.
... (12 more)
```
That is red, but a weak red: it fails because the code does not exist, not because behaviour is wrong. So add a stub that compiles and gives a wrong answer (`false` / `0`) and run again:
```
1) assignedAndPastDue_isOverdue          AssertionError
2) rejectedAndPastDue_isOverdue          AssertionError
3) daysOverdue_roundsUp                  expected:<1> but was:<0>
Tests run: 40,  Failures: 3
```
Two things to say out loud:
- **Failing for the right reason** is the point of the red step. A test you never saw fail proves nothing.
- **5 of the 8 new tests passed against the stub.** "Never overdue when submitted", "not overdue at the exact due moment", and so on are satisfied by `return false`. They are still worth having (they will catch a *wrong* implementation later), but a green bar early does not mean the feature works.

## 2. GREEN — the simplest thing that passes
```kotlin
fun isOverdue(a: Assignment, nowMillis: Long): Boolean {
    val due = a.dueDateMillis ?: return false
    val status = a.status ?: return false
    if (status != AssignmentStatus.ASSIGNED && status != AssignmentStatus.REJECTED) return false
    return nowMillis > due
}
```
`OK (40 tests)`. Not pretty: the list "assigned or rejected" now exists in two places, the status contract and here.

## 3. REFACTOR — improve the code, tests stay untouched
The duplicated rule is replaced by `StatusRules.salesCanEdit(status)` (which reads from the contract), and the arithmetic goes into one private function. Same 8 tests, still `OK (40 tests)`. This is the step where having tests is a gift: you can restructure without being afraid.

## 4. Does the safety net actually catch anything? (mutation check)
Four deliberate bugs injected into the finished rule:

| Mutation | Result |
|---|---|
| `> 0` → `>= 0` (exactly-at-due becomes overdue) | caught (1 test fails) |
| `DAY_MS - 1` → `DAY_MS` (rounding off by one) | caught |
| ignore status, treat everything as actionable | caught (2 tests fail) |
| subtract in the wrong direction | caught (4 tests fail) |

## 5. Then the UI
The list row shows "Terlambat N hari" using `OverdueRule` (Compose, **not compiled or run**). The rule is tested; the screen that uses it is not. Say that.

## TDD with an AI tool: the working agreement
1. **You write (or approve) the tests; the AI writes the code.** If the AI writes both, it will happily test its own misunderstanding.
2. Ask the AI to *show the failing run first*. Do not accept "the tests should fail now".
3. Ask for the *minimum* code to pass, then refactor as a separate step.
4. Give the AI the boundary sentences from the requirement and ask it to turn each into a test. Then read the tests as carefully as you would read the code.
5. Run the mutation idea by hand on anything important: break the code, see if a test notices.

## Where TDD fits and where it doesn't (be honest on stage)
- **Good fit:** rules, validation, state machines, sorting, parsing, money and date logic, ViewModel/presenter logic with fake repositories. In this repo: `StatusRules`, `SubmissionValidator`, `OverdueRule`, `DetailModel`.
- **Poor fit:** Compose layout, camera hand-off, Firebase wiring. Use previews, instrumented tests, the emulator, and a manual checklist instead.
- TDD pushes the design: `OverdueRule` takes `now` as a parameter instead of reading the clock, so no fake clock is needed. Hard-to-test code is often badly shaped code.
- For Firestore Security Rules the equivalent is `firebase/test/rules.test.mjs`: write the "who may do what" test first, then the rule. (Those tests are written but have not been run yet.)
