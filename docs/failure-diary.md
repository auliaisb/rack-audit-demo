# Failure diary

Real things that went wrong while building this with AI help, how each was found, and what changed. Kept so the talk can say "this is what actually happened" instead of "AI is magic". Add your own entries when you run the project on your machine — the first Gradle sync will certainly produce some.

Entries are tagged by **how it was found**: `ran` (a tool failed), `read` (spotted on re-reading, nothing ran), `design` (thought of before it could happen).

---

### 1. The sandbox could not build the project at all — `ran`
**What happened.** npm, Maven Central and Google Maven are all blocked from the environment where the AI worked (`CONNECT tunnel failed, response 403`). No Android SDK, no emulator, no Firebase emulator.
**Consequence.** The Compose screens, the Firebase repositories, the React pages, the Security Rules and the rules tests have **never been executed**. Everything the AI wrote there is a first draft, honestly unverified.
**What changed.** The code was split so that the risky logic is Android-free and runs on a plain JVM / plain Node: `StatusRules`, `SubmissionValidator`, `ImageMath`, `AssignmentOrdering`, `DetailModel` (Kotlin) and `status.js`, `validation.js` (JS). Those *were* executed (53 tests: 13 JS, 40 Kotlin).
**Lesson for the talk.** Architecture decides what you can test. "Thin UI, logic in plain classes" is not taste, it is what made verification possible when tooling was missing.

### 2. `node --test test/` failed on Node 22 — `ran`
**What happened.** The AI wrote `"test": "node --test test/"`. Node 22 treats `test/` as a module path: `MODULE_NOT_FOUND`.
**Fix.** A glob (`"test/**/*.test.js"`). Then a `sed` edit to apply the fix to the second package.json produced **invalid JSON** (nested escaped quotes) — caught only because the next step parsed the file.
**Lesson.** AI-generated commands are plausible, not tested. And scripted edits to config files need a parse check afterwards.

### 3. A function that does not exist — `read`
**What happened.** In `MainActivity.kt` the AI wrote `login.state.collectAsStateCompat()`. There is no such API (the real one is `collectAsStateWithLifecycle()`). It compiled in the AI's head and would have failed at the first Gradle sync.
**How found.** Re-reading the file after writing it. Nothing compiled it — see entry 1.
**Lesson.** Hallucinated APIs look exactly like real ones. The only reliable detector is a compiler.

### 4. The next user on the phone would see the previous user's list — `read`
**What happened.** `ListViewModel` was created with a fixed key. After sign-out and sign-in as another rep on the same phone, Compose would hand back the *same* ViewModel, still holding the first rep's Firestore listener (which now fails with permission-denied) and cached items.
**Fix.** ViewModels are keyed by uid (`list-$uid`, `detail-$uid-$id`).
**Not covered by any test.** It is UI wiring; it needs an instrumented test or a manual two-account check. Listed in the README's manual checklist.
**Lesson.** The security rules protect the server. Stale client state is a separate leak.

### 5. Unit tests would not compile — repository interface lived in the Firebase file — `ran`
**What happened.** The first JVM compile of `DetailModelTest` failed: `unresolved reference 'AssignmentRepository'`. The interface and its Firebase implementation were in one file, so any test touching the interface dragged in Firebase and Android classes.
**Fix.** Interface in its own file, implementation in `FirebaseAssignmentRepository.kt`.
**Lesson.** "Depend on an interface" only works if the interface is *physically* separate from the implementation's dependencies.

### 6. Offline writes never finish — `design`
**What happened (before it happened).** A Firestore `set()/update()` Task completes only after the *server* acknowledges. With no signal the Task hangs, so a naive "busy" spinner spins forever. Storage uploads silently retry for 10 minutes by default.
**Fix.** `withTimeout(30s)` around writes, `maxUploadRetryTimeMillis = 30s` for Storage, and — important — after a failed submit the app **re-reads the server state**, because a timed-out write is not cancelled and may still land later. `DetailModelTest.timeoutButServerAppliedWrite_isNotReportedAsFailure` covers that.
**Lesson.** The happy path of generated code is usually fine; the network edge cases are where it needs a human who has shipped Android apps.

### 7. A "fix" that reported the wrong problem — `read`
**What happened.** When the camera could not be opened (cache full), the first draft of `launchCamera()` reused the "photo could not be processed" message by feeding the model a non-existent file. Misleading for the user and a hack in a catch block.
**Fix.** An explicit `CAMERA_FAILED` message and `onCameraFailed()`.

### 8. A rule the rules cannot enforce — `design`
**What happened.** Firestore Rules cannot count a subcollection, so `photoCount` on the assignment is client-supplied. A modified client could submit with `photoCount: 3` and no photos.
**Decision.** Not fixed (the requirement was "Firebase only, no server code"); documented as limitation 1 in `docs/data-model.md`; the dashboard shows "Tidak ada foto pada pengajuan ini" so a supervisor sees it immediately. The real fix is a Cloud Function that recounts.
**Lesson.** Say out loud what your backend cannot guarantee.

### 9. Green bars that meant nothing — `ran` (TDD)
**What happened.** In the TDD cycle for `OverdueRule`, 5 of the 8 new tests passed against a stub that just returns `false`. A "all green" early in the cycle proved nothing about the feature.
**What changed.** The cycle includes a stub step that must fail the *behavioural* tests, and a mutation check afterwards (4 of 4 injected bugs caught).
**Lesson.** A test you have never seen fail has not earned your trust.

---

## What did not go wrong (and why you should not trust that)
- The Firestore and Storage rules passed a bracket-balance check and nothing else. **No rule has been evaluated.** The 26 rules tests in `firebase/test/rules.test.mjs` are written but unrun. Expect at least one rule or one test to be wrong on the first run. That is normal, and a good story for the talk: record the first run and show what failed.
- The React pages passed a JSX syntax transform. They have not been bundled or opened in a browser.
- Dependency versions in `libs.versions.toml` and `package.json` are from the AI's memory. They have not been resolved.

## Check on the checks (mutation test)
Twelve deliberate bugs (eight, then four more on `OverdueRule`) were injected into the executable logic (dropped transition, wrong photo limit, missing trim, wrong sort direction, removed double-tap guard, no server reconciliation after failed submit, transition ignoring the actor, reject without a reason). All twelve were caught by the tests. This says the *logic* tests are meaningful; it says nothing about the UI, data layer or rules.

## Your entries
<!-- Add: date, what you asked the AI, what it produced, what broke, how you found out, what you changed. -->
