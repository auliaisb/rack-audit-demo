# Rack Display Audit — Spec (one page)

Written BEFORE any code. If the code and this page disagree, one of them is wrong; decide which, then fix it.

## Problem
A retail sales team visits client stores and must prove how the client's products are displayed on the rack (planogram compliance). Today this happens in WhatsApp photos with no assignment, no deadline, no review trail.

## Users
- **Sales** (field rep). Uses the native Android app only.
- **Supervisor**. Uses the React web dashboard only.

## Features (the whole list; anything else is a non-goal)
1. Supervisor creates an assignment: store name/address, title, instructions, due date, assigned sales rep.
2. Sales sees only their own assignments (work they can act on first, soonest due first; finished ones last) in the app.
3. Sales opens an assignment, takes 1–8 photos with the camera, adds a caption per photo and one overall description, and submits.
4. Supervisor sees all assignments, filters by status, opens a submission, views photos and description.
5. Supervisor approves, or rejects with a mandatory reason. A rejected assignment returns to the sales rep, who adds/removes photos and resubmits.
6. Supervisor can cancel an assignment that is not yet submitted.

## Status flow (single source of truth: `contract/assignment-status.json`)
`assigned → submitted → approved | rejected`, `rejected → submitted`, `assigned|rejected → cancelled`.
`approved` and `cancelled` are terminal.

## Backend
Firebase only. No server code of our own.
- Authentication: email + password. Accounts are created by an admin in the Firebase console (no self-registration).
- Firestore: `users`, `assignments`, `assignments/{id}/photos` (see `docs/data-model.md`).
- No Cloud Storage: it needs the paid Blaze plan. Each photo is stored inside its Firestore document as a `Bytes` field.
- Security Rules are the real backend: they enforce roles, ownership, allowed fields and the status flow.
- Hosting: the dashboard.

## Constraints
- UI language: Bahasa Indonesia. Code, identifiers and docs: English.
- Photos compressed on the phone to ≤ 500 KB, long edge ≤ 1280 px (a Firestore document is capped at 1 MiB; the rules enforce 500,000 bytes).
- Offline: Firestore offline cache is on, but **saving a photo gives up after 30 s without a connection** (non-goal: upload queue).
- Android minSdk 26. Dashboard on current desktop Chrome/Edge/Firefox.

## Non-goals
Push notifications, planogram image comparison / AI scoring, reports/export, multi-company tenancy, in-app user management, iOS, offline photo queue.

## Done means
- Sales cannot read or change another rep's assignment, cannot approve, cannot skip the flow (rules tests prove it).
- Supervisor cannot edit a submission's content, only review it.
- The same status contract is tested in Kotlin and in JavaScript.
- Both clients build and the happy path works against the Firebase emulator. (See README "What is verified" for what has and has not actually been run.)
