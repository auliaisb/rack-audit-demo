# Data model

All timestamps are Firestore `Timestamp`, written with `serverTimestamp()` where noted (rules require `== request.time`).

## `users/{uid}`  (uid = Firebase Auth uid)
| field | type | notes |
|---|---|---|
| role | `"sales"` \| `"supervisor"` | set in the console, never by a client |
| displayName | string | |
| active | bool | `false` locks the user out at rules level |

Clients can read their own doc; supervisors can list all. **No client can write this collection.**

## `assignments/{id}`
| field | type | who sets it | notes |
|---|---|---|---|
| title | string 1–120 | supervisor, create | |
| instructions | string ≤1000 | supervisor, create | what the rack should look like |
| storeName | string 1–120 | supervisor, create | |
| storeAddress | string ≤200 | supervisor, create | |
| assignedTo | uid | supervisor, create | must be a `sales` user |
| assignedBy | uid | supervisor, create | must equal caller |
| dueDate | Timestamp | supervisor, create | |
| status | enum | see contract | |
| photoCount | int 0–8 | create: 0; sales at submit | denormalised, see limitation |
| note | string 1–1000 | sales, submit | overall description |
| submittedAt | serverTimestamp | sales, submit | |
| reviewNote | string ≤500 | supervisor, review | required when rejecting |
| reviewedBy | uid | supervisor, review | |
| reviewedAt | serverTimestamp | supervisor, review | |
| createdAt / updatedAt | serverTimestamp | | |

## `assignments/{id}/photos/{photoId}`
| field | type | notes |
|---|---|---|
| data | bytes (Firestore `Bytes`) | the JPEG itself, 1–500,000 bytes (`photoMaxBytes` in the contract; a document is capped at 1 MiB) |
| caption | string ≤200 | |
| uploadedBy | uid | must equal caller |
| createdAt | serverTimestamp | |

Only the assigned sales rep can add/remove photos, and only while status is `assigned` or `rejected`. No updates.

## Why photos are inside Firestore
Cloud Storage for Firebase needs the paid Blaze plan (Spark projects get no bucket access). To keep the project free and "Firebase only", each photo is a `Bytes` field of its own document. Same access rules as before: create/delete only by the assigned rep while the assignment is editable; read by the assigned rep or any supervisor. At scale, move the files to Cloud Storage and keep only a path here.

## Known limitations (be honest about these on stage)
1. **`photoCount` is a client-supplied number.** Rules cannot count a subcollection. A malicious client could submit with `photoCount: 3` and 0 photos. Impact: a supervisor sees an empty submission and rejects it. Real fix: a Cloud Function that recounts on status change (out of scope: "Firebase only, no server code of our own").
2. **Photos cost document reads and bandwidth.** Opening a submission reads up to 8 documents of ≤ 500 KB each. Free (Spark) quota, per the Firestore docs: 1 GiB stored, 50,000 reads and 20,000 writes per day. Fine for a demo or a small team, wrong for thousands of assignments. Also: the phone's Firestore cache holds these bytes too.
3. Firestore rules `get()` calls count as billed reads (about 1–3 per write). Fine at this scale.
4. Deleting a user from Auth does not delete their `users` doc.

## Indexes
`assignments`: `status ASC, createdAt DESC` (dashboard status filter). The sales query (`assignedTo ==`) needs no composite index because sorting happens on the device.
