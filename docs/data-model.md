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
| storagePath | string | must equal `assignments/{id}/{photoId}.jpg` |
| caption | string ≤200 | |
| uploadedBy | uid | must equal caller |
| createdAt | serverTimestamp | |

Only the assigned sales rep can add/remove photos, and only while status is `assigned` or `rejected`. No updates.

## Storage
`assignments/{assignmentId}/{photoId}.jpg` — `image/jpeg`, < 5 MB. Create/delete only by the assigned rep while the assignment is editable. Read by the assigned rep or any supervisor. Rules read Firestore (`firestore.get`) to decide.

## Known limitations (be honest about these on stage)
1. **`photoCount` is a client-supplied number.** Rules cannot count a subcollection. A malicious client could submit with `photoCount: 3` and 0 photos. Impact: a supervisor sees an empty submission and rejects it. Real fix: a Cloud Function that recounts on status change (out of scope: "Firebase only, no server code of our own").
2. Orphan files: upload succeeds but the photo doc write fails → file in Storage without a doc. Harmless, small; a lifecycle cleanup is a later task.
3. Firestore rules `get()` calls count as billed reads (about 1–3 per write). Fine at this scale.
4. Deleting a user from Auth does not delete their `users` doc.

## Indexes
`assignments`: `status ASC, createdAt DESC` (dashboard status filter). The sales query (`assignedTo ==`) needs no composite index because sorting happens on the device.
