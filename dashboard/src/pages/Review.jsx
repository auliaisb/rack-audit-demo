import { useEffect, useRef, useState } from 'react';
import { getAssignment, listPhotos, releasePhotos, reviewAssignment, cancelAssignment } from '../api.js';
import contract from '../domain/contract.js';
import { nextStatuses, statusLabel } from '../domain/status.js';
import { validateReview, hasErrors } from '../domain/validation.js';

export default function Review({ id, onBack }) {
  const [a, setA] = useState(undefined); // undefined = loading, null = not found
  const [photos, setPhotos] = useState([]);
  const [reviewNote, setReviewNote] = useState('');
  const [errors, setErrors] = useState({});
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  const photosRef = useRef([]); // blob: URLs must be revoked when replaced or when the page closes
  function showPhotos(list) {
    releasePhotos(photosRef.current);
    photosRef.current = list;
    setPhotos(list);
  }

  // Photos are whole Firestore documents (bytes): read them once, not again after every decision.
  async function load(withPhotos = true) {
    try {
      const data = await getAssignment(id);
      setA(data);
      if (data && withPhotos) showPhotos(await listPhotos(id));
    } catch {
      setA(null);
      setMessage('Gagal memuat penugasan.');
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [id]);
  useEffect(() => () => releasePhotos(photosRef.current), []);

  if (a === undefined) return <p className="muted">Memuat…</p>;
  if (a === null) return <><p className="error">{message || 'Penugasan tidak ditemukan.'}</p><button onClick={onBack}>Kembali</button></>;

  // What may a supervisor do now? Ask the shared contract, not a hard-coded if.
  const choices = nextStatuses(contract, a.status, 'supervisor');
  const canReview = choices.includes('approved') && choices.includes('rejected');
  const canCancel = choices.includes('cancelled');

  async function decide(decision) {
    const found = validateReview(decision, reviewNote, contract.limits);
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setMessage('');
    try {
      await reviewAssignment(id, decision, reviewNote);
      await load(false);
      setReviewNote('');
    } catch {
      setMessage('Gagal menyimpan keputusan. Status mungkin sudah berubah — muat ulang.');
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    setBusy(true);
    try {
      await cancelAssignment(id);
      await load(false);
    } catch {
      setMessage('Gagal membatalkan penugasan.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <button className="link" onClick={onBack}>← Kembali</button>
      <h2>{a.title} <span className={`badge ${a.status}`}>{statusLabel(a.status)}</span></h2>
      <p><strong>{a.storeName}</strong><br />{a.storeAddress}</p>
      {a.instructions && <p className="muted">Instruksi: {a.instructions}</p>}

      {a.note && <div className="card"><h3>Deskripsi dari sales</h3><p className="pre">{a.note}</p></div>}
      {a.status === 'rejected' && a.reviewNote && <p className="error">Alasan ditolak: {a.reviewNote}</p>}

      <div className="grid">
        {photos.map((p) => (
          <figure key={p.id}>
            {p.url
              ? <a href={p.url} target="_blank" rel="noopener noreferrer"><img src={p.url} alt={p.caption || 'Foto rak'} loading="lazy" /></a>
              : <div className="missing">Foto tidak tersedia</div>}
            <figcaption>{p.caption || '—'}</figcaption>
          </figure>
        ))}
      </div>
      {photos.length === 0 && a.status !== 'assigned' && <p className="error">Tidak ada foto pada pengajuan ini.</p>}

      {canReview && (
        <div className="card">
          <h3>Verifikasi</h3>
          <label>Catatan (wajib jika menolak)
            <textarea rows={3} value={reviewNote} onChange={(e) => setReviewNote(e.target.value)} maxLength={contract.limits.reviewNoteMaxLength} />
            {errors.note && <span className="error">{errors.note}</span>}
          </label>
          <div className="row">
            <button disabled={busy} onClick={() => decide('approved')}>Setujui</button>
            <button disabled={busy} className="danger" onClick={() => decide('rejected')}>Tolak</button>
          </div>
        </div>
      )}
      {canCancel && <button className="link danger" disabled={busy} onClick={cancel}>Batalkan penugasan</button>}
      {message && <p className="error" role="alert">{message}</p>}
    </section>
  );
}
