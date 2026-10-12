# Firebase setup (first time, ~30 minutes)

Prerequisites on your machine: Android Studio (recent stable), JDK 17+, Node 20+, a Google account.

## 1. Create the project
1. Firebase console → Add project (e.g. `rack-audit-demo`). Analytics not needed.
2. Build → **Authentication** → Get started → enable **Email/Password**. Leave self-registration to the console only (the apps have no sign-up screen).
3. Build → **Firestore Database** → Create (production mode, region close to users, e.g. `asia-southeast2` Jakarta).
4. **Do not enable Storage.** Cloud Storage for Firebase needs the paid Blaze plan; this project keeps photos in Firestore instead, so the free Spark plan is enough.
5. Project settings → Your apps → add an **Android app** with package `com.rackaudit.app` → download `google-services.json` → put it in `android/app/` (git-ignored).
6. Project settings → Your apps → add a **Web app** → copy the config into `dashboard/.env.local` (see `dashboard/.env.example`).

## 2. Deploy rules and indexes
```bash
cp .firebaserc.example .firebaserc      # edit the project id
npm i -g firebase-tools && firebase login
firebase deploy --only firestore:rules,firestore:indexes
```

## 3. Create the first users (console)
Create each person under Authentication → Users → Add user. Then in Firestore create `users/<their uid>`:

| uid | fields |
|---|---|
| supervisor's uid | `role: "supervisor"`, `displayName: "Bu Rina"`, `active: true` |
| sales rep's uid | `role: "sales"`, `displayName: "Andi"`, `active: true` |

The rules deliberately forbid clients from writing `users`, so this step is manual (or Admin SDK / Cloud Function later — out of scope).

## 4. Run
- Dashboard: `cd dashboard && npm install && npm run dev`
- Android: open `android/` in Android Studio, sync, run on a device/emulator with a camera app.
- Tests: `cd dashboard && npm test` · `cd firebase && npm install && npm test` · `cd android && ./gradlew testDebugUnitTest`

## 5. Local emulators (no cloud project needed)
```bash
cd firebase && npm install && npm run emulators
# dashboard/.env.local: VITE_USE_EMULATORS=true   (project id demo-rack-audit)
```
The Android app does not point at the emulators yet (a `useEmulator` switch in `AppGraph` for debug builds is the next small task).

## 6. Deploy the dashboard
```bash
cd dashboard && npm run build && cd .. && firebase deploy --only hosting
```

## Before real users
- [ ] If you ever move to the Blaze plan (for Cloud Storage or Functions), set a **budget alert** first and check what it can and cannot stop.
- [ ] Watch Firestore usage (photos are documents): free quota is 50,000 reads/day.
- [ ] Turn on Authentication email enumeration protection (Authentication → Settings).
- [ ] Restrict the Web API key to your hosting domain (Google Cloud → Credentials).
- [ ] Add Android release signing and register the SHA-256 in the Firebase Android app.
- [ ] Personal data note: photos taken inside client stores may include staff/customers. Check the client's policy and UU PDP obligations before collecting.
