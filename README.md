# Rack Display Audit

Field-sales app for documenting client store racks. Demo project for the talk *"Turn AI-Generated Code into a Real App"*.

| Part | Tech | Who uses it |
|---|---|---|
| `android/` | Native Android, Kotlin, Jetpack Compose | Sales reps: get assignments, take photos, describe, submit |
| `dashboard/` | React 18 + Vite | Supervisors: create assignments, verify photos, approve / reject |
| `firebase/` + `firebase.json` | Auth, Firestore, Storage, Security Rules, Hosting — **no server code** | Backend |
| `contract/assignment-status.json` | Shared status flow + limits | Read by rules tests, JS tests, Kotlin tests |

Start with [`SPEC.md`](SPEC.md) (one page), then [`docs/data-model.md`](docs/data-model.md). AI-tool rules are in [`AGENTS.md`](AGENTS.md). Setup steps: [`docs/setup-firebase.md`](docs/setup-firebase.md). What went wrong while building: [`docs/failure-diary.md`](docs/failure-diary.md).

## What is verified — read this first

This was written in an environment that could not reach npm / Maven / Google Maven and had no Android SDK or emulator.

| Area | Status |
|---|---|
| Dashboard pure logic (`status.js`, `validation.js`) | **Ran**: 13 tests pass (`cd dashboard && npm test`, no install needed) |
| Kotlin pure logic (`StatusRules`, `SubmissionValidator`, `ImageMath`, `AssignmentOrdering`, `DetailModel`, contract drift) | **Ran** on a plain JVM with the Kotlin 2.0.21 compiler and JUnit 4: 32 tests pass. `ContractTest` used a small org.json stand-in locally; Gradle will use the real library. |
| Mutation check on the above | **Ran**: 8 injected bugs, 8 caught |
| JSX files, JSON, XML, Kotlin syntax | **Parsed OK** only (no bundling, no Gradle) |
| Compose screens, Firebase repositories, `ImageProcessor`, React pages | **Never compiled or run.** First Gradle sync / `npm install && npm run build` will likely show errors (wrong versions, missing imports). |
| `firestore.rules`, `storage.rules` | **Never evaluated** (bracket balance only) |
| `firebase/test/rules.test.mjs` (26 tests) | **Written, never run** (needs `npm install` + Java for the emulators) |
| Dependency versions | From memory; not resolved |

## Run the checks that need your machine, in this order
1. `cd firebase && npm install && npm test` — rules first, because rules are the real backend.
2. `cd dashboard && npm install && npm test && npm run build`
3. `cd android && ./gradlew testDebugUnitTest` (open in Android Studio first so it syncs; put `google-services.json` in `android/app/`)
4. Then the manual path below. Write every surprise into `docs/failure-diary.md`.

## Manual end-to-end checklist
- [ ] Supervisor signs in on the dashboard, creates an assignment for rep A.
- [ ] Rep A signs in on the phone, sees it. Rep B does not see it.
- [ ] Rep A takes 2 photos with captions, writes a description, submits. Dashboard shows it under "Menunggu verifikasi" with both photos.
- [ ] Supervisor rejects **without** a reason → blocked. With a reason → rep A sees it as "Ditolak" with the reason, edits, resubmits.
- [ ] Supervisor approves. Rep A can no longer edit.
- [ ] Airplane mode: submit shows an error within ~30 s, no infinite spinner; going back online shows the true state.
- [ ] Sign out as rep A, sign in as rep B on the same phone: no data from A anywhere (see failure diary #4).
- [ ] Rotate the phone and background the app while the camera is open; the photo still lands.
- [ ] Try to break rules by hand with the Firebase console / a curl call using rep A's token: read B's assignment, set status `approved`.

## Layout
```
SPEC.md  AGENTS.md  README.md
contract/assignment-status.json     single source of truth
docs/                               data-model, setup-firebase, failure-diary
firebase.json  .firebaserc.example
firebase/                           firestore.rules, storage.rules, indexes, rules tests
dashboard/                          Vite + React (src/api.js is the only Firebase-calling file)
android/                            Gradle project; domain/ + ui/detail/DetailModel.kt are plain Kotlin
```

## Suggested stages for the talk (tag them in git as you go)
1. `stage-1-structure` — spec, contract, data model, rules (this commit has them).
2. `stage-2-connect` — wire Android and dashboard to a real Firebase project.
3. `stage-3-debug` — first sync, first rules-test run; fix what breaks; log it.
4. `stage-4-test` — add emulator-backed happy-path test; manual checklist above.
5. `stage-5-deploy` — `firebase deploy`, release-signed APK to 2 testers.
