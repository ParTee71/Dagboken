// Kontroller före import och serialisering – ingen emulator behövs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { GeoPoint, Timestamp } from 'firebase-admin/firestore';
import { prepareImport } from '../lib/backup.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { fromJson, toJson } from '../lib/serialize.mjs';
import { hasValidIds } from '../lib/walk.mjs';
import { readRepoFile } from './helpers/repo.mjs';

const user = { path: 'users/u', data: { schemaVersion: 1 } };

test('import vägrar användare med nyare schemaVersion än verktyget, men bara de användarna (BCK-15)', () => {
  const newer = { path: 'users/ny', data: { schemaVersion: CURRENT_VERSION + 1 } };
  const data = { schemaVersion: CURRENT_VERSION + 1, documents: [user, newer] };
  assert.throws(() => prepareImport(data), /users\/ny har schemaVersion/);
  assert.deepEqual(prepareImport(data, { user: 'u' }).map((d) => d.path), ['users/u']);
});

test('import vägrar okända sökvägar och filer som inte är exporter', () => {
  assert.throws(() => prepareImport({ documents: [{ path: 'households/x', data: {} }] }), /Okänd sökväg/);
  assert.throws(() => prepareImport({ documents: [user, { path: 'users/u/okand/x', data: {} }] }), /Okänd sökväg/);
  assert.throws(() => prepareImport({ schemaVersion: 1 }), /documents saknas/);
  assert.throws(() => prepareImport({ documents: [{ path: 'users/u' }] }), /users\/u: data saknas/);
  assert.throws(() => prepareImport({ documents: [user] }, { user: 'annan' }), /finns inte i filen/);
});

test('en ändringsfil (updates) kräver --update, och --update kräver en ändringsfil utan användardokument', () => {
  const updates = { schemaVersion: 1, updates: [{ path: 'users/u/doses/d1', data: { name: 'Ny' } }] };
  assert.throws(() => prepareImport(updates), /bara ändrade fält.*--update/);
  assert.throws(() => prepareImport({ schemaVersion: 1, documents: [user] }, { update: true }), /ingen ändringsfil/);
  assert.throws(() => prepareImport({ updates: [user] }, { update: true }), /users\/u: en ändringsfil får inte ändra användardokumentet/);
  assert.throws(() => prepareImport({ updates: [{ path: 'users/u/doses/d1', data: {} }] }, { update: true }), /inga fält att ändra/);
  assert.throws(() => prepareImport({ updates: [{ path: 'users/u/okand/x', data: { a: 1 } }] }, { update: true }), /Okänd sökväg/);
  assert.deepEqual(prepareImport(updates, { update: true }), [{ path: 'users/u/doses/d1', data: { name: 'Ny' } }]);
});

/** Den delade id-fixturen (samma i DocumentRulesTest i :core); `{ text, times }` är en upprepad text. */
const ids = JSON.parse(readRepoFile('tools/db/test/fixtures/ids.json'));
const idText = (v) => (typeof v === 'string' ? v : v.text.repeat(v.times));

test('hasValidIds godtar och vägrar samma id som DocumentRules.isValidId i :core (fixtures/ids.json)', () => {
  for (const id of ids.valid.map(idText)) assert.ok(hasValidIds(`users/u/doses/${id}`), JSON.stringify(id));
  // Ett `/` i ett id byter sökväg – hasValidIds ser bara led, prepareImport vägrar den som okänd sökväg (testet nedan).
  for (const id of ids.invalid.map(idText).filter((id) => !id.includes('/'))) assert.ok(!hasValidIds(`users/u/doses/${id}`), JSON.stringify(id));
});

test('import vägrar ogiltiga och dubbla ID:n innan något skrivs (BCK-16)', () => {
  const doc = (path) => ({ path, data: {} });
  for (const path of ['users//doses/x', 'users/./doses/x', 'users/u/doses/..', 'users/__x__', `users/${'å'.repeat(751)}`]) {
    assert.throws(() => prepareImport({ documents: [user, doc(path)] }), /Ogiltigt ID/, path);
  }
  assert.throws(() => prepareImport({ documents: [user, doc('users/u/doses/a/b')] }), /Okänd sökväg/, 'ett / i id:t byter sökväg');
  assert.throws(() => prepareImport({ documents: [user, user] }), /users\/u finns två gånger/);
  assert.equal(prepareImport({ documents: [user, doc('users/u/doses/recept_a-1_2026-09-21_Morgon')] }).length, 2);
  assert.equal(prepareImport({ documents: [user, doc('users/u/illnessEpisodes/e/checkins/c')] }).length, 2);
});

test('tidsstämplar serialiseras med alla nio decimaler och tillbaka', () => {
  const ts = new Timestamp(1_790_000_000, 123_456_789);
  const json = toJson({ at: ts, list: [ts], n: 0.25, empty: null });
  assert.deepEqual(json, { at: { __ts: '2026-09-21T14:13:20.123456789Z' }, list: [{ __ts: '2026-09-21T14:13:20.123456789Z' }], n: 0.25, empty: null });
  const back = fromJson(json);
  assert.ok(back.at.isEqual(ts));
  assert.ok(back.list[0].isEqual(ts));
});

test('okända Firestore-typer stoppar exporten', () => {
  assert.throws(() => toJson({ ref: new Uint8Array([1]) }), /stöds inte/);
  assert.throws(() => toJson({ plats: new GeoPoint(59.3, 18.1) }), /stöds inte/);
});

test('en egen map med nyckeln __ts överlever rundturen som map', () => {
  const data = { fält: { __ts: 'inte en tid' }, annat: { __map: 1 } };
  const json = toJson(data);
  assert.deepEqual(json, { fält: { __map: { __ts: 'inte en tid' } }, annat: { __map: { __map: 1 } } });
  assert.deepEqual(fromJson(json), data);
});

test('felmeddelanden innehåller aldrig värdet', () => {
  assert.throws(() => toJson({ n: 2 ** 60 }), (error) => !error.message.includes(String(2 ** 60)));
  assert.throws(() => fromJson({ t: { __ts: 'hemligt-värde' } }), (error) => !error.message.includes('hemligt'));
});

test('tal som JSON inte kan bära exakt stoppar exporten i stället för att ändras', () => {
  assert.throws(() => toJson({ n: 2 ** 53 + 2 }), /kan inte exporteras exakt/);
  assert.throws(() => toJson({ n: Number.NaN }), /kan inte exporteras exakt/);
  assert.throws(() => toJson({ n: Infinity }), /kan inte exporteras exakt/);
  assert.equal(toJson(Number.MAX_SAFE_INTEGER), Number.MAX_SAFE_INTEGER);
});
