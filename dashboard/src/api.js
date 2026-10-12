// The ONLY file that talks to Firebase (see AGENTS.md rule 5).
import {
  signInWithEmailAndPassword, signOut as fbSignOut, onAuthStateChanged,
} from 'firebase/auth';
import {
  collection, doc, getDoc, getDocs, addDoc, updateDoc, query, where, orderBy,
  onSnapshot, serverTimestamp, Timestamp,
} from 'firebase/firestore';
import { getDownloadURL, ref } from 'firebase/storage';
import { auth, db, storage } from './firebase.js';

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
export async function listPhotosWithUrls(assignmentId) {
  const snap = await getDocs(query(collection(db, 'assignments', assignmentId, 'photos'), orderBy('createdAt', 'asc')));
  return Promise.all(snap.docs.map(async (d) => {
    const p = { id: d.id, ...d.data() };
    try {
      p.url = await getDownloadURL(ref(storage, p.storagePath));
    } catch {
      p.url = null; // missing/denied file: UI shows a placeholder instead of crashing
    }
    return p;
  }));
}
