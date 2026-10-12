import { useEffect, useState } from 'react';
import { createAssignment, listSalesUsers } from '../api.js';
import contract from '../domain/contract.js';
import { validateNewAssignment, hasErrors } from '../domain/validation.js';

const EMPTY = { title: '', storeName: '', storeAddress: '', instructions: '', assignedTo: '', dueDate: '' };

export default function NewAssignment({ onDone }) {
  const [form, setForm] = useState(EMPTY);
  const [reps, setReps] = useState([]);
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    listSalesUsers().then(setReps).catch(() => setSubmitError('Gagal memuat daftar sales.'));
  }, []);

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const L = contract.limits;

  async function submit(e) {
    e.preventDefault();
    const found = validateNewAssignment(form, L);
    setErrors(found);
    if (hasErrors(found)) return;
    setBusy(true);
    setSubmitError('');
    try {
      await createAssignment(form);
      onDone();
    } catch {
      setSubmitError('Gagal menyimpan penugasan. Coba lagi.');
      setBusy(false);
    }
  }

  const field = (k, label, props = {}) => (
    <label>{label}
      <input value={form[k]} onChange={set(k)} {...props} />
      {errors[k] && <span className="error">{errors[k]}</span>}
    </label>
  );

  return (
    <form className="card" onSubmit={submit} noValidate>
      <h2>Tugas baru</h2>
      {field('title', 'Judul', { maxLength: L.titleMaxLength })}
      {field('storeName', 'Nama toko', { maxLength: L.storeNameMaxLength })}
      {field('storeAddress', 'Alamat toko', { maxLength: L.storeAddressMaxLength })}
      <label>Instruksi
        <textarea rows={4} value={form.instructions} onChange={set('instructions')} maxLength={L.instructionsMaxLength} />
        {errors.instructions && <span className="error">{errors.instructions}</span>}
      </label>
      <label>Sales
        <select value={form.assignedTo} onChange={set('assignedTo')}>
          <option value="">— pilih —</option>
          {reps.map((r) => <option key={r.uid} value={r.uid}>{r.displayName}</option>)}
        </select>
        {errors.assignedTo && <span className="error">{errors.assignedTo}</span>}
      </label>
      {field('dueDate', 'Tenggat', { type: 'date' })}
      {submitError && <p className="error" role="alert">{submitError}</p>}
      <div className="row">
        <button type="submit" disabled={busy}>{busy ? 'Menyimpan…' : 'Simpan'}</button>
        <button type="button" className="link" onClick={onDone}>Batal</button>
      </div>
    </form>
  );
}
