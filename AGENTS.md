# Rules for AI coding tools (Claude Code, etc.)

Read `SPEC.md`, `docs/data-model.md` and `contract/assignment-status.json` first. They win over your assumptions.

## Hard rules
1. **Never trust the client.** Every permission lives in `firebase/firestore.rules` / `storage.rules`. UI hiding a button is not security.
2. Status transitions and limits come from `contract/assignment-status.json`. Do not hard-code a new status or number in one place only. If the contract changes, change: the JSON, both rules files, `StatusRules.kt`, `dashboard/src/domain`, and the tests. All in one commit.
3. No new dependency without a one-line reason in the commit message.
4. Android: Kotlin, Jetpack Compose, one ViewModel per screen, repositories behind an interface, no Firebase calls inside composables.
5. Dashboard: React function components, no router library, no state library. Firebase calls only in `src/api.js`.
6. User-facing text in Bahasa Indonesia (Android: `strings.xml`). Identifiers and comments in English.
7. Never commit: `google-services.json`, `.env*` (except `.env.example`), service-account JSON, `.firebaserc`.
8. Every rules change needs a test in `firebase/test/rules.test.mjs`, written from `SPEC.md`, not from the rules file.
9. Do not add Cloud Functions. If a requirement seems to need one, stop and say so (see limitation 1 in `docs/data-model.md`).

## Before you say "done"
- `cd dashboard && npm test` (pure-logic tests, no install needed)
- `cd firebase && npm test` (needs the Firebase emulator)
- `cd android && ./gradlew testDebugUnitTest`
- Say plainly which of these you actually ran.
