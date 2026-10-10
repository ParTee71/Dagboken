// Skripten som backup.yml och sessionen kör: export.mjs och import.mjs från kommandoraden.
// Endast emulatorn (barnprocessen ärver NODE_TEST_CONTEXT och är därmed i testläge).
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fixture, seed, useCleanEmulator } from './helpers/emulator.mjs';
import { runScript as node } from './helpers/run.mjs';

const database = useCleanEmulator();
let dir;
before(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'tools-db-'));
});
beforeEach(() => seed(database()));
after(() => rmSync(dir, { recursive: true, force: true }));

test('export.mjs skriver filen och visar bara antal per samling', () => {
  const out = path.join(dir, 'backup.json');
  const result = node('export.mjs', ['--out', out]);
  assert.equal(result.status, 0, result.stderr);
  assert.equal(JSON.parse(readFileSync(out, 'utf8')).documents.length, fixture.documents.length);
  assert.match(result.stdout, /doses: 3/);
  assert.doesNotMatch(result.stdout, /Levaxin/, 'utskriften får inte innehålla dokumentinnehåll');
});

test('import.mjs --dry-run visar vad som skulle skrivas', () => {
  const out = path.join(dir, 'backup.json');
  assert.equal(node('export.mjs', ['--out', out]).status, 0);
  const result = node('import.mjs', ['--in', out, '--dry-run']);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Torrkörning/);
  assert.match(result.stdout, /users: 1/);
});

test('fel ger en rad på svenska och exitkod 1; --help fungerar', () => {
  const missing = node('export.mjs', []);
  assert.equal(missing.status, 1);
  assert.match(missing.stderr, /^Fel: --out saknas/);
  assert.match(node('import.mjs', ['--help']).stdout, /Användning: node tools\/db\/import.mjs/);
  assert.equal(node('migrate.mjs', ['--okänd']).status, 1);
});

test('printTable: kolumner från alla rader, avkortade celler markeras och radbrytningar syns', async () => {
  const { printTable } = await import('../lib/cli.mjs');
  const lines = [];
  const log = console.log;
  console.log = (line) => lines.push(line);
  try {
    printTable([{ id: 'a', name: 'x'.repeat(60) }, { id: 'b', notes: 'rad ett\nrad två' }]);
    printTable([]);
  } finally {
    console.log = log;
  }
  assert.match(lines[0], /^id\s+name\s+notes$/);
  assert.ok(lines[2].includes(`${'x'.repeat(39)}…`));
  assert.ok(lines[3].includes('rad ett ⏎ rad två'));
  assert.equal(lines[4], '(2 dokument)');
  assert.equal(lines[5], '(inga dokument)');
});

test('export.mjs fäller när databasen är tom – en tom backup ska aldrig bli grön', async () => {
  const out = path.join(dir, 'tom.json');
  const docs = await database().collection('users').listDocuments();
  for (const ref of docs) await database().recursiveDelete(ref);
  const result = node('export.mjs', ['--out', out]);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Inga dokument/);
});

test('import.mjs --replace från kommandoraden återställer användaren exakt', async () => {
  const out = path.join(dir, 'backup.json');
  assert.equal(node('export.mjs', ['--out', out]).status, 0);
  await database().doc('users/uid-test/options/extra').set({ name: 'Tillagd efter backupen' });
  const result = node('import.mjs', ['--in', out, '--replace']);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Tog bort:\n {2}options: 1/);
  assert.equal((await database().doc('users/uid-test/options/extra').get()).exists, false);
});

test('import.mjs --update från kommandoraden: bara fälten, saknade hoppas över; utan --update vägras filen', async () => {
  const file = path.join(dir, 'renamed.json');
  const id = 'a7b8c9d0-e1f2-4a3b-8c4d-5e6f7a8b9c0d';
  writeFileSync(file, JSON.stringify({
    exportedAt: '2026-10-01T00:00:00.000Z', schemaVersion: 1,
    updates: [{ path: `users/uid-test/prnMedicines/${id}`, data: { name: 'Alvedon', form: 'tablet' } }, { path: 'users/uid-test/prnMedicines/borta', data: { name: 'X' } }],
  }));
  const plain = node('import.mjs', ['--in', file, '--dry-run']);
  assert.equal(plain.status, 1);
  assert.match(plain.stderr, /--update/);
  const dry = node('import.mjs', ['--in', file, '--update', '--dry-run']);
  assert.equal(dry.status, 0, dry.stderr);
  assert.match(dry.stdout, /skulle skriva:\n {2}prnMedicines: 1/);
  assert.match(dry.stdout, /Skulle hoppa över \(saknas\):\n {2}prnMedicines: 1/);
  const result = node('import.mjs', ['--in', file, '--update']);
  assert.equal(result.status, 0, result.stderr);
  const stored = (await database().doc(`users/uid-test/prnMedicines/${id}`).get()).data();
  assert.deepEqual([stored.form, stored.maxPerDay, stored.note], ['tablet', 4, 'Max 3 g per dygn']);
  assert.equal((await database().doc('users/uid-test/prnMedicines/borta').get()).exists, false);
});

test('en trasig fil ger ett fast felmeddelande utan utdrag ur innehållet', () => {
  const broken = path.join(dir, 'trasig.json');
  writeFileSync(broken, '{"documents": [{"path": "users/u1/doses/d1", "data": {"name": "Levaxin 50" ');
  const result = node('import.mjs', ['--in', broken, '--dry-run']);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /inte giltig JSON/);
  assert.doesNotMatch(result.stderr, /Levaxin/);
});
