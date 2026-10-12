import { useEffect, useState } from 'react';
import { subscribeAssignments } from '../api.js';
import contract from '../domain/contract.js';
import { statusLabel } from '../domain/status.js';

const fmt = (ts) => (ts?.toDate ? ts.toDate().toLocaleDateString('id-ID', { day: '2-digit', month: 'short', year: 'numeric' }) : '—');

export default function AssignmentList({ onOpen }) {
  const [filter, setFilter] = useState('submitted'); // what a supervisor needs first
  const [rows, setRows] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    setRows(null);
    setError('');
    return subscribeAssignments(
      filter || null,
      setRows,
      () => setError('Gagal memuat data. Periksa koneksi atau hak akses.'),
    );
  }, [filter]);

  return (
    <section>
      <div className="row">
        <h2>Penugasan</h2>
        <select value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Filter status">
          <option value="">Semua status</option>
          {contract.statuses.map((s) => <option key={s} value={s}>{statusLabel(s)}</option>)}
        </select>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      {rows === null && !error && <p className="muted">Memuat…</p>}
      {rows?.length === 0 && <p className="muted">Tidak ada penugasan dengan status ini.</p>}
      {rows?.length > 0 && (
        <table>
          <thead><tr><th>Judul</th><th>Toko</th><th>Tenggat</th><th>Foto</th><th>Status</th></tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} onClick={() => onOpen(r.id)} tabIndex={0}
                  onKeyDown={(e) => e.key === 'Enter' && onOpen(r.id)}>
                <td>{r.title}</td>
                <td>{r.storeName}</td>
                <td>{fmt(r.dueDate)}</td>
                <td>{r.photoCount}</td>
                <td><span className={`badge ${r.status}`}>{statusLabel(r.status)}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
