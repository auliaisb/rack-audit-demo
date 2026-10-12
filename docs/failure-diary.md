# Failure diary

Real things that went wrong while building this with AI help, how each was found, and what changed. Kept so the talk can say "this is what actually happened" instead of "AI is magic". Add your own entries when you run the project on your machine — the first Gradle sync will certainly produce some.

Entries are tagged by **how it was found**: `ran` (a tool failed), `read` (spotted on re-reading, nothing ran), `design` (thought of before it could happen).

---

### 1. The sandbox could not build the project at all — `ran`
**What happened.** npm, Maven Central and Google Maven are all blocked from the environment where the AI worked (`CONNECT tunnel failed, response 403`). No Android SDK, no emulator, no Firebase emulator.
**Consequence.** The Compose screens, the Firebase repositories, the React pages, the Security Rules and the rules tests have **never been executed**. Everything the AI wrote there is a first draft, honestly unverified.
**What changed.** The code was split so that the risky logic is Android-free and runs on a plain JVM / plain Node: `StatusRules`, `SubmissionValidator`, `ImageMath`, `AssignmentOrdering`, `DetailModel` (Kotlin) and `status.js`, `validation.js` (JS). Those *were* executed (59 tests: 13 JS, 46 Kotlin).
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
**What happened (before it happened).** A Firestore `set()/update()` Task completes only after the *server* acknowledges. With no signal the Task hangs, so a naive "busy" spinner spins forever. (Storage uploads also retry silently for 10 minutes by default; moot since entry 10, but the timeout trap moved to photo writes.)
**Fix.** `withTimeout(30s)` around writes and — important — after a failed submit the app **re-reads the server state**, because a timed-out write is not cancelled and may still land later. `DetailModelTest.timeoutButServerAppliedWrite_isNotReportedAsFailure` covers that.
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

### 10. Firebase Storage needs a paid plan: the design changed two days before the talk — `ran`
**What happened.** The first design stored photos in Cloud Storage. When the author set up the Firebase project, Storage demanded the Blaze (pay-as-you-go) plan. [Firebase's own FAQ](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024) says Spark projects lose access to all buckets. The setup doc had a vague "newer projects may require Blaze; check the console" line, which nobody acted on before it became a blocker.
**Fix.** Photos became a `data` `Bytes` field of `assignments/{id}/photos/{photoId}`. The contract changed first (`photoMaxBytes` 1,500,000 → 500,000, new `photoMaxEdgePx` 1280, because a document is capped at 1 MiB); `ContractTest` went red, then `Limits` was updated. `storage.rules`, the Storage SDK and Coil were removed; the 26 rules tests were rewritten for the new shape (still 26).
**A bug the change would have introduced.** A photo write is now a Firestore write, so a timed-out write is queued and may land later (entry 6). The first version would have shown "upload failed" for a photo that then appeared, tempting a double photo. `DetailModelTest.photoWriteTimesOutButLandsLater_isShown_notReportedAsFailure` was written first (red: expected 1 but was 0), then the fix (green), then 3 mutants (all caught).
**Costs to say out loud.** Opening a submission reads up to 8 documents of ≤ 500 KB; free quota is 50,000 reads/day and 1 GiB stored. Fine for a demo or small team, not a design for thousands of assignments (that is Storage's job).
**Lesson.** The change was quick and low-risk because the limit lives in one contract file that tests read, and the logic is separate from the Firebase code. And: read the platform's billing constraints in the first hour, not the last two days.

### 11. The detail screen kept showing "waiting for the supervisor" after approval — `ran` (found by the author on a phone)
**What happened.** The supervisor approved a submission, but the rep's detail screen still said "Menunggu verifikasi supervisor". Two causes, both in code the AI wrote and I had read as correct: (1) the ViewModel is cached per assignment (which fixed entry 4), but `load()` ran only once when it was created, so reopening the same assignment reused old data; (2) after a successful submit the one-shot message "Terkirim. Menunggu verifikasi supervisor." was never cleared. The list was fine because it uses a live listener; the detail screen read once.
**Fix.** Written test-first (3 of 5 new tests red, 2 green against the stub: entry 9 again): `DetailModel.refresh()` re-reads the assignment and photos with `serverOnly` (so offline it fails instead of returning the cached copy), clears the stale message, keeps what the rep is typing, ignores itself while an upload or submit is running, and reports "Gagal memuat ulang" offline. Triggered by pull-to-refresh and every time the screen is shown again. The list also got pull-to-refresh. Six mutants, six caught.
**Not covered by any test.** The `PullToRefreshBox` wiring, the `LaunchedEffect`, and `Source.SERVER` reads are UI/Firebase code and have never been compiled or run.
**Lesson.** Caching a ViewModel to fix one bug created another. State that can change on someone else's device needs either a live listener or an explicit refresh, and the manual checklist needs a "someone else changed it meanwhile" case.

---

## What did not go wrong (and why you should not trust that)
- The Firestore rules passed a bracket-balance check and nothing else (including the new `data.size()` checks on `bytes`: I believe `size()` exists for bytes in rules, but nothing has evaluated it). **No rule has been evaluated.** The 26 rules tests in `firebase/test/rules.test.mjs` are written but unrun. Expect at least one rule or one test to be wrong on the first run. That is normal, and a good story for the talk: record the first run and show what failed.
- The React pages passed a JSX syntax transform. They have not been bundled or opened in a browser.
- Dependency versions in `libs.versions.toml` and `package.json` are from the AI's memory. They have not been resolved.

## Check on the checks (mutation test)
Twenty-one deliberate bugs (eight, four on `OverdueRule`, three on the photo-failure handling, six on refresh) were injected into the executable logic (dropped transition, wrong photo limit, missing trim, wrong sort direction, removed double-tap guard, no server reconciliation after failed submit, transition ignoring the actor, reject without a reason). All twenty-one were caught by the tests. This says the *logic* tests are meaningful; it says nothing about the UI, data layer or rules.

## Your entries
<!-- Add: date, what you asked the AI, what it produced, what broke, how you found out, what you changed. -->
