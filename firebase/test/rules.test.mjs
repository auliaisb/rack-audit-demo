// Security Rules tests. Written from SPEC.md, not from the rules file.
// Run with the emulators:  cd firebase && npm test
// (needs: npm install, and Java for the Firestore/Storage emulators)
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment, assertFails, assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, getDocs, updateDoc, deleteDoc, addDoc,
  collection, query, where, serverTimestamp, Timestamp,
} from 'firebase/firestore';
import { ref, uploadBytes, getBytes } from 'firebase/storage';

const PROJECT = 'demo-rack-audit';
let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT,
    firestore: { rules: readFileSync('firestore.rules', 'utf8'), host: '127.0.0.1', port: 8080 },
    storage: { rules: readFileSync('storage.rules', 'utf8'), host: '127.0.0.1', port: 9199 },
  });
});
after(async () => { await env.cleanup(); });

// ---- fixtures -------------------------------------------------------------
async function seed({ status = 'assigned', photoCount = 0 } = {}) {
  await env.clearFirestore();
  await env.clearStorage();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, 'users/boss'), { role: 'supervisor', displayName: 'Boss', active: true });
    await setDoc(doc(db, 'users/rep1'), { role: 'sales', displayName: 'Rep 1', active: true });
    await setDoc(doc(db, 'users/rep2'), { role: 'sales', displayName: 'Rep 2', active: true });
    await setDoc(doc(db, 'users/gone'), { role: 'sales', displayName: 'Gone', active: false });
    await setDoc(doc(db, 'assignments/a1'), {
      title: 'Rak minuman', instructions: '', storeName: 'Toko A', storeAddress: 'Jl. A',
      assignedTo: 'rep1', assignedBy: 'boss', dueDate: Timestamp.now(),
      status, photoCount, createdAt: Timestamp.now(), updatedAt: Timestamp.now(),
    });
  });
}
const as = (uid) => env.authenticatedContext(uid);
const dbOf = (uid) => as(uid).firestore();
const submitFields = () => ({
  status: 'submitted', note: 'Rak penuh', photoCount: 2,
  submittedAt: serverTimestamp(), updatedAt: serverTimestamp(),
});
const reviewFields = (status, reviewNote) => ({
  status, reviewNote, reviewedBy: 'boss', reviewedAt: serverTimestamp(), updatedAt: serverTimestamp(),
});
const newAssignment = (over = {}) => ({
  title: 'Cek rak', instructions: 'Foto semua rak', storeName: 'Toko B', storeAddress: 'Jl. B',
  assignedTo: 'rep1', assignedBy: 'boss', dueDate: Timestamp.now(), status: 'assigned',
  photoCount: 0, createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...over,
});

beforeEach(async () => { await seed(); });

// ---- unauthenticated / roles ---------------------------------------------
test('anonymous cannot read anything', async () => {
  const db = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(db, 'assignments/a1')));
  await assertFails(getDoc(doc(db, 'users/rep1')));
});

test('nobody can write users, not even a supervisor or the user themself', async () => {
  await assertFails(setDoc(doc(dbOf('boss'), 'users/rep1'), { role: 'supervisor', active: true }));
  await assertFails(updateDoc(doc(dbOf('rep1'), 'users/rep1'), { role: 'supervisor' }));
});

test('a deactivated user is locked out', async () => {
  await assertFails(getDocs(query(collection(dbOf('gone'), 'assignments'), where('assignedTo', '==', 'gone'))));
});

// ---- reading --------------------------------------------------------------
test('rep reads own assignment but not another rep\'s', async () => {
  await assertSucceeds(getDoc(doc(dbOf('rep1'), 'assignments/a1')));
  await assertFails(getDoc(doc(dbOf('rep2'), 'assignments/a1')));
});

test('rep can list only own assignments; unfiltered list is rejected', async () => {
  await assertSucceeds(getDocs(query(collection(dbOf('rep1'), 'assignments'), where('assignedTo', '==', 'rep1'))));
  await assertFails(getDocs(collection(dbOf('rep1'), 'assignments')));
});

test('supervisor reads everything', async () => {
  await assertSucceeds(getDocs(collection(dbOf('boss'), 'assignments')));
});

// ---- creating -------------------------------------------------------------
test('supervisor creates a valid assignment', async () => {
  await assertSucceeds(addDoc(collection(dbOf('boss'), 'assignments'), newAssignment()));
});

test('rep cannot create an assignment', async () => {
  await assertFails(addDoc(collection(dbOf('rep1'), 'assignments'),
    newAssignment({ assignedBy: 'rep1', assignedTo: 'rep1' })));
});

test('create rejects: wrong status, assigning to a supervisor, extra field, impersonated assignedBy, empty title', async () => {
  const col = collection(dbOf('boss'), 'assignments');
  await assertFails(addDoc(col, newAssignment({ status: 'approved' })));
  await assertFails(addDoc(col, newAssignment({ assignedTo: 'boss' })));
  await assertFails(addDoc(col, newAssignment({ extra: 1 })));
  await assertFails(addDoc(col, newAssignment({ assignedBy: 'rep1' })));
  await assertFails(addDoc(col, newAssignment({ title: '' })));
});

// ---- the flow -------------------------------------------------------------
test('rep submits assigned -> submitted', async () => {
  await assertSucceeds(updateDoc(doc(dbOf('rep1'), 'assignments/a1'), submitFields()));
});

test('other rep cannot submit', async () => {
  await assertFails(updateDoc(doc(dbOf('rep2'), 'assignments/a1'), submitFields()));
});

test('rep cannot self-approve or skip to approved', async () => {
  await assertFails(updateDoc(doc(dbOf('rep1'), 'assignments/a1'), { status: 'approved', updatedAt: serverTimestamp() }));
});

test('submit needs a note and 1..8 photos and must not touch other fields', async () => {
  const ref1 = doc(dbOf('rep1'), 'assignments/a1');
  await assertFails(updateDoc(ref1, { ...submitFields(), note: '' }));
  await assertFails(updateDoc(ref1, { ...submitFields(), photoCount: 0 }));
  await assertFails(updateDoc(ref1, { ...submitFields(), photoCount: 9 }));
  await assertFails(updateDoc(ref1, { ...submitFields(), title: 'Diubah' }));
  await assertFails(updateDoc(ref1, { ...submitFields(), assignedTo: 'rep2' }));
});

test('supervisor approves a submitted assignment', async () => {
  await seed({ status: 'submitted', photoCount: 2 });
  await assertSucceeds(updateDoc(doc(dbOf('boss'), 'assignments/a1'), reviewFields('approved', '')));
});

test('supervisor must give a reason to reject', async () => {
  await seed({ status: 'submitted', photoCount: 2 });
  const r = doc(dbOf('boss'), 'assignments/a1');
  await assertFails(updateDoc(r, reviewFields('rejected', '')));
  await assertSucceeds(updateDoc(r, reviewFields('rejected', 'Rak bawah belum difoto')));
});

test('supervisor cannot review something not submitted', async () => {
  await assertFails(updateDoc(doc(dbOf('boss'), 'assignments/a1'), reviewFields('approved', '')));
});

test('supervisor cannot edit the rep\'s note while reviewing', async () => {
  await seed({ status: 'submitted', photoCount: 2 });
  await assertFails(updateDoc(doc(dbOf('boss'), 'assignments/a1'), { ...reviewFields('approved', ''), note: 'ubah' }));
});

test('approved is terminal for everyone', async () => {
  await seed({ status: 'approved', photoCount: 2 });
  await assertFails(updateDoc(doc(dbOf('rep1'), 'assignments/a1'), submitFields()));
  await assertFails(updateDoc(doc(dbOf('boss'), 'assignments/a1'), { status: 'cancelled', updatedAt: serverTimestamp() }));
});

test('rejected -> submitted again by the rep', async () => {
  await seed({ status: 'rejected', photoCount: 2 });
  await assertSucceeds(updateDoc(doc(dbOf('rep1'), 'assignments/a1'), submitFields()));
});

test('supervisor cancels assigned, but not submitted', async () => {
  await assertSucceeds(updateDoc(doc(dbOf('boss'), 'assignments/a1'), { status: 'cancelled', updatedAt: serverTimestamp() }));
  await seed({ status: 'submitted', photoCount: 1 });
  await assertFails(updateDoc(doc(dbOf('boss'), 'assignments/a1'), { status: 'cancelled', updatedAt: serverTimestamp() }));
});

test('nobody deletes assignments', async () => {
  await assertFails(deleteDoc(doc(dbOf('boss'), 'assignments/a1')));
  await assertFails(deleteDoc(doc(dbOf('rep1'), 'assignments/a1')));
});

// ---- photos (Firestore docs) ---------------------------------------------
const photo = (id, over = {}) => ({
  storagePath: `assignments/a1/${id}.jpg`, caption: 'Rak atas', uploadedBy: 'rep1',
  createdAt: serverTimestamp(), ...over,
});

test('assigned rep adds a photo doc while editable; storagePath must match', async () => {
  const d = dbOf('rep1');
  await assertSucceeds(setDoc(doc(d, 'assignments/a1/photos/abcdefgh12345678'), photo('abcdefgh12345678')));
  await assertFails(setDoc(doc(d, 'assignments/a1/photos/zzzzzzzz12345678'), photo('abcdefgh12345678')));
  await assertFails(setDoc(doc(d, 'assignments/a1/photos/yyyyyyyy12345678'),
    photo('yyyyyyyy12345678', { uploadedBy: 'rep2' })));
});

test('photos cannot be added after submission, nor by another rep or a supervisor', async () => {
  await assertFails(setDoc(doc(dbOf('rep2'), 'assignments/a1/photos/abcdefgh12345678'), photo('abcdefgh12345678')));
  await assertFails(setDoc(doc(dbOf('boss'), 'assignments/a1/photos/abcdefgh12345678'), photo('abcdefgh12345678')));
  await seed({ status: 'submitted', photoCount: 1 });
  await assertFails(setDoc(doc(dbOf('rep1'), 'assignments/a1/photos/abcdefgh12345678'), photo('abcdefgh12345678')));
});

test('supervisor can read photos; other rep cannot', async () => {
  await env.withSecurityRulesDisabled(async (c) =>
    setDoc(doc(c.firestore(), 'assignments/a1/photos/abcdefgh12345678'), { storagePath: 'x', caption: '', uploadedBy: 'rep1', createdAt: Timestamp.now() }));
  await assertSucceeds(getDocs(collection(dbOf('boss'), 'assignments/a1/photos')));
  await assertFails(getDocs(collection(dbOf('rep2'), 'assignments/a1/photos')));
});

// ---- Storage --------------------------------------------------------------
const jpeg = () => new Uint8Array([0xff, 0xd8, 0xff, 0xd9]);
const meta = { contentType: 'image/jpeg' };

test('storage: assigned rep uploads a jpeg; other rep and wrong type are refused', async () => {
  const ok = ref(as('rep1').storage(), 'assignments/a1/abcdefgh12345678.jpg');
  await assertSucceeds(uploadBytes(ok, jpeg(), meta));
  const other = ref(as('rep2').storage(), 'assignments/a1/bbbbbbbb12345678.jpg');
  await assertFails(uploadBytes(other, jpeg(), meta));
  const wrongType = ref(as('rep1').storage(), 'assignments/a1/cccccccc12345678.jpg');
  await assertFails(uploadBytes(wrongType, jpeg(), { contentType: 'text/html' }));
});

test('storage: upload refused after submission; supervisor can read but not write', async () => {
  const p = 'assignments/a1/abcdefgh12345678.jpg';
  await assertSucceeds(uploadBytes(ref(as('rep1').storage(), p), jpeg(), meta));
  await assertSucceeds(getBytes(ref(as('boss').storage(), p)));
  await assertFails(getBytes(ref(as('rep2').storage(), p)));
  await assertFails(uploadBytes(ref(as('boss').storage(), 'assignments/a1/dddddddd12345678.jpg'), jpeg(), meta));
  await seed({ status: 'submitted', photoCount: 1 });
  await assertFails(uploadBytes(ref(as('rep1').storage(), 'assignments/a1/eeeeeeee12345678.jpg'), jpeg(), meta));
});
