import { useState } from 'react';
import { signIn } from '../api.js';

// Do not reveal whether the email or the password was wrong.
const GENERIC = 'Email atau kata sandi salah.';

export default function Login({ initialError = '' }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(initialError);
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await signIn(email.trim(), password);
    } catch (err) {
      setError(err.code === 'auth/too-many-requests'
        ? 'Terlalu banyak percobaan. Coba lagi beberapa menit lagi.'
        : GENERIC);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card login" onSubmit={submit}>
      <h1>Audit Display Rak</h1>
      <p className="muted">Masuk sebagai supervisor</p>
      <label>Email
        <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>Kata sandi
        <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </label>
      {error && <p className="error" role="alert">{error}</p>}
      <button type="submit" disabled={busy}>{busy ? 'Masuk…' : 'Masuk'}</button>
    </form>
  );
}
