// Pure-logic tests. Run: node --test test/   (no npm install needed)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { canTransition, nextStatuses, isTerminal, statusLabel } from '../src/domain/status.js';
import { validateNewAssignment, validateReview, hasErrors } from '../src/domain/validation.js';

const contract = JSON.parse(readFileSync(new URL('../../contract/assignment-status.json', import.meta.url), 'utf8'));
const L = contract.limits;

// ---- status contract ------------------------------------------------------
test('the happy path is allowed, with the right actor', () => {
  assert.ok(canTransition(contract, 'assigned', 'submitted', 'sales'));
  assert.ok(canTransition(contract, 'submitted', 'approved', 'supervisor'));
  assert.ok(canTransition(contract, 'submitted', 'rejected', 'supervisor'));
  assert.ok(canTransition(contract, 'rejected', 'submitted', 'sales'));
});

test('actors cannot do each other\'s steps', () => {
  assert.ok(!canTransition(contract, 'assigned', 'submitted', 'supervisor'));
  assert.ok(!canTransition(contract, 'submitted', 'approved', 'sales'));
  assert.ok(!canTransition(contract, 'assigned', 'cancelled', 'sales'));
});

test('no skipping: assigned cannot jump to approved', () => {
  for (const by of ['sales', 'supervisor']) assert.ok(!canTransition(contract, 'assigned', 'approved', by));
});

test('approved and cancelled are terminal; the others are not', () => {
  assert.ok(isTerminal(contract, 'approved'));
  assert.ok(isTerminal(contract, 'cancelled'));
  for (const s of ['assigned', 'submitted', 'rejected']) assert.ok(!isTerminal(contract, s));
});

test('supervisor choices on a submitted assignment are exactly approve/reject', () => {
  assert.deepEqual(nextStatuses(contract, 'submitted', 'supervisor').sort(), ['approved', 'rejected']);
});

test('every status in the contract has an Indonesian label', () => {
  for (const s of contract.statuses) assert.notEqual(statusLabel(s), s, `no label for ${s}`);
});

test('every transition only mentions known statuses', () => {
  for (const t of contract.transitions) {
    assert.ok(contract.statuses.includes(t.from), t.from);
    assert.ok(contract.statuses.includes(t.to), t.to);
  }
});

// ---- new assignment validation -------------------------------------------
const good = () => ({
  title: 'Cek rak minuman', storeName: 'Toko Sumber Rejeki', storeAddress: 'Jl. Mawar 1',
  instructions: 'Foto rak atas dan bawah', assignedTo: 'rep1', dueDate: '2026-10-20',
});
const today = new Date('2026-10-14T10:00:00');

test('a complete assignment has no errors', () => {
  assert.equal(hasErrors(validateNewAssignment(good(), L, today)), false);
});

test('required fields are reported; whitespace-only counts as empty', () => {
  const e = validateNewAssignment({ ...good(), title: '   ', storeName: '', assignedTo: '' }, L, today);
  assert.deepEqual(Object.keys(e).sort(), ['assignedTo', 'storeName', 'title']);
});

test('length limits come from the contract', () => {
  const e = validateNewAssignment({ ...good(), title: 'x'.repeat(L.titleMaxLength + 1) }, L, today);
  assert.ok(e.title);
  const ok = validateNewAssignment({ ...good(), title: 'x'.repeat(L.titleMaxLength) }, L, today);
  assert.equal(ok.title, undefined);
});

test('due date: missing, malformed, past are rejected; today is fine', () => {
  assert.ok(validateNewAssignment({ ...good(), dueDate: '' }, L, today).dueDate);
  assert.ok(validateNewAssignment({ ...good(), dueDate: '14/10/2026' }, L, today).dueDate);
  assert.ok(validateNewAssignment({ ...good(), dueDate: '2026-10-13' }, L, today).dueDate);
  assert.equal(validateNewAssignment({ ...good(), dueDate: '2026-10-14' }, L, today).dueDate, undefined);
});

// ---- review validation ----------------------------------------------------
test('approve needs no note; reject needs one', () => {
  assert.equal(hasErrors(validateReview('approved', '', L)), false);
  assert.ok(validateReview('rejected', '  ', L).note);
  assert.equal(hasErrors(validateReview('rejected', 'Rak bawah kosong', L)), false);
});

test('unknown decision and over-long note are rejected', () => {
  assert.ok(validateReview('pending', '', L).decision);
  assert.ok(validateReview('rejected', 'x'.repeat(L.reviewNoteMaxLength + 1), L).note);
});
