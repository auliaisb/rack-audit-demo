// The ONLY file that talks to Firebase (see AGENTS.md rule 5).
import {
  signInWithEmailAndPassword, signOut as fbSignOut, onAuthStateChanged,
} from 'firebase/auth';
import {
  collection, doc, getDoc, getDocs, addDoc, updateDoc, query, where, orderBy,
  onSnapshot, serverTimestamp, Timestamp,
} from 'firebase/firestore';
import { auth, db } from './firebase.js';

// ---- auth ----
export const watchAuth = (cb) => onAuthStateChanged(auth, cb);
export const signIn = (email, password) => signInWithEmailAndPassword(auth, email, password);
export const signOut = () => fbSignOut(auth);

export async function loadProfile(uid) {
  const snap = await getDoc(doc(db, 'users', uid));
  return snap.exists() ? { uid, ...snap.data() } : null;
}

export async function listSalesUsers() {
  const snap = await getDocs(query(collection(db, 'users'), where('role', '==', 'sales'), where('active', '==', true)));
  return snap.docs.map((d) => ({ uid: d.id, ...d.data() }));
}

// ---- assignments ----
const withId = (d) => ({ id: d.id, ...d.data() });

export function subscribeAssignments(statusFilter, onData, onError) {
  const base = collection(db, 'assignments');
  const q = statusFilter
    ? query(base, where('status', '==', statusFilter), orderBy('createdAt', 'desc'))
    : query(base, orderBy('createdAt', 'desc'));
  return onSnapshot(q, (snap) => onData(snap.docs.map(withId)), onError);
}

export async function getAssignment(id) {
  const snap = await getDoc(doc(db, 'assignments', id));
  return snap.exists() ? withId(snap) : null;
}

export async function createAssignment(input) {
  return addDoc(collection(db, 'assignments'), {
    title: input.title.trim(),
    instructions: (input.instructions ?? '').trim(),
    storeName: input.storeName.trim(),
    storeAddress: (input.storeAddress ?? '').trim(),
    assignedTo: input.assignedTo,
    assignedBy: auth.currentUser.uid,
    dueDate: Timestamp.fromDate(new Date(`${input.dueDate}T23:59:00`)),
    status: 'assigned',
    photoCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export function reviewAssignment(id, decision, note) {
  return updateDoc(doc(db, 'assignments', id), {
    status: decision, // 'approved' | 'rejected'
    reviewNote: (note ?? '').trim(),
    reviewedBy: auth.currentUser.uid,
    reviewedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export function cancelAssignment(id) {
  return updateDoc(doc(db, 'assignments', id), { status: 'cancelled', updatedAt: serverTimestamp() });
}

// ---- photos ----
// Photos are stored inside their Firestore documents (field `data`, type Bytes) because Cloud Storage
// needs a paid plan. Each one is turned into a blob: URL for <img>; call releasePhotos() when done.
export async function listPhotos(assignmentId) {
  const snap = await getDocs(query(collection(db, 'assignments', assignmentId, 'photos'), orderBy('createdAt', 'asc')));
  return snap.docs.map((d) => {
    const { data, caption } = d.data();
    const url = data ? URL.createObjectURL(new Blob([data.toUint8Array()], { type: 'image/jpeg' })) : null;
    return { id: d.id, caption: caption ?? '', url }; // url null = field missing: UI shows a placeholder
  });
}

export function releasePhotos(photos) {
  photos.forEach((p) => { if (p.url) URL.revokeObjectURL(p.url); });
}
