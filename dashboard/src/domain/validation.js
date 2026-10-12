// Client-side validation for friendly messages. The Rules are the real gate.
// Messages are in Bahasa Indonesia (user-facing).

function tooLong(value, max) {
  return typeof value === 'string' && value.length > max;
}

export function validateNewAssignment(input, limits, today = new Date()) {
  const errors = {};
  const title = (input.title ?? '').trim();
  const storeName = (input.storeName ?? '').trim();

  if (!title) errors.title = 'Judul wajib diisi.';
  else if (tooLong(title, limits.titleMaxLength)) errors.title = `Judul maksimal ${limits.titleMaxLength} karakter.`;

  if (!storeName) errors.storeName = 'Nama toko wajib diisi.';
  else if (tooLong(storeName, limits.storeNameMaxLength)) errors.storeName = `Nama toko maksimal ${limits.storeNameMaxLength} karakter.`;

  if (tooLong(input.storeAddress ?? '', limits.storeAddressMaxLength)) errors.storeAddress = `Alamat maksimal ${limits.storeAddressMaxLength} karakter.`;
  if (tooLong(input.instructions ?? '', limits.instructionsMaxLength)) errors.instructions = `Instruksi maksimal ${limits.instructionsMaxLength} karakter.`;

  if (!input.assignedTo) errors.assignedTo = 'Pilih sales yang ditugaskan.';

  if (!input.dueDate || !/^\d{4}-\d{2}-\d{2}$/.test(input.dueDate)) {
    errors.dueDate = 'Tanggal tenggat wajib diisi.';
  } else {
    const due = new Date(`${input.dueDate}T23:59:00`);
    const startOfToday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    if (Number.isNaN(due.getTime())) errors.dueDate = 'Tanggal tidak valid.';
    else if (due < startOfToday) errors.dueDate = 'Tenggat tidak boleh di masa lalu.';
  }
  return errors;
}

export function validateReview(decision, note, limits) {
  const errors = {};
  const text = (note ?? '').trim();
  if (decision !== 'approved' && decision !== 'rejected') errors.decision = 'Keputusan tidak valid.';
  if (decision === 'rejected' && !text) errors.note = 'Alasan penolakan wajib diisi.';
  if (tooLong(text, limits.reviewNoteMaxLength)) errors.note = `Catatan maksimal ${limits.reviewNoteMaxLength} karakter.`;
  return errors;
}

export function hasErrors(errors) {
  return Object.keys(errors).length > 0;
}
