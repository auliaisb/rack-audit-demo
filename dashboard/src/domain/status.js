// Pure functions; the contract is passed in so tests can load it with fs.

export function canTransition(contract, from, to, by) {
  return contract.transitions.some((t) => t.from === from && t.to === to && t.by === by);
}

export function nextStatuses(contract, from, by) {
  return contract.transitions.filter((t) => t.from === from && t.by === by).map((t) => t.to);
}

export function isTerminal(contract, status) {
  return !contract.transitions.some((t) => t.from === status);
}

export const STATUS_LABEL_ID = {
  assigned: 'Ditugaskan',
  submitted: 'Menunggu verifikasi',
  approved: 'Disetujui',
  rejected: 'Ditolak',
  cancelled: 'Dibatalkan',
};

export function statusLabel(status) {
  return STATUS_LABEL_ID[status] ?? status;
}
