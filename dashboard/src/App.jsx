import { useEffect, useState } from 'react';
import { watchAuth, loadProfile, signOut } from './api.js';
import Login from './pages/Login.jsx';
import AssignmentList from './pages/AssignmentList.jsx';
import NewAssignment from './pages/NewAssignment.jsx';
import Review from './pages/Review.jsx';

export default function App() {
  const [session, setSession] = useState({ state: 'loading', profile: null, error: '' });
  const [view, setView] = useState({ name: 'list' });

  useEffect(() => watchAuth(async (user) => {
    if (!user) return setSession({ state: 'out', profile: null, error: '' });
    try {
      const profile = await loadProfile(user.uid);
      if (!profile || profile.role !== 'supervisor' || profile.active !== true) {
        await signOut();
        return setSession({ state: 'out', profile: null, error: 'Akun ini bukan akun supervisor.' });
      }
      setSession({ state: 'in', profile, error: '' });
    } catch {
      await signOut();
      setSession({ state: 'out', profile: null, error: 'Gagal memuat profil. Coba lagi.' });
    }
  }), []);

  if (session.state === 'loading') return <p className="center">Memuat…</p>;
  if (session.state === 'out') return <Login initialError={session.error} />;

  return (
    <div className="app">
      <header className="topbar">
        <h1>Audit Display Rak</h1>
        <nav>
          <button onClick={() => setView({ name: 'list' })}>Penugasan</button>
          <button onClick={() => setView({ name: 'new' })}>+ Tugas baru</button>
        </nav>
        <span className="who">{session.profile.displayName}</span>
        <button className="link" onClick={signOut}>Keluar</button>
      </header>
      <main>
        {view.name === 'list' && <AssignmentList onOpen={(id) => setView({ name: 'review', id })} />}
        {view.name === 'new' && <NewAssignment onDone={() => setView({ name: 'list' })} />}
        {view.name === 'review' && <Review id={view.id} onBack={() => setView({ name: 'list' })} />}
      </main>
    </div>
  );
}
