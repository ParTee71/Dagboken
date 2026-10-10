// compare.mjs och lib/compare.mjs – grinden OMB-4: två exporter jämförs fältvis per dokumentsökväg, och utskriften
// visar bara antal, sökvägar och fältnamn. Ingen emulator: kan köras med `node --test tools/db/test/compare.test.mjs`.
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { byPath, compareDocuments, isIdentical } from '../lib/compare.mjs';
import { runScript } from './helpers/run.mjs';

const doc = (p, data) => ({ path: p, data });
const base = [
  doc('users/u1', { schemaVersion: 1 }),
  doc('users/u1/screenings/s1', { energy: 4, note: 'Trött efter lunch', symptoms: [{ optionId: 'o1', score: 3 }] }),
  doc('users/u1/doses/d1', { status: 'taken', plannedTime: '08:00' }),
];

let dir;
before(() => {
  dir = mkdtempSync(path.join(tmpdir(), 'tools-db-compare-'));
});
after(() => rmSync(dir, { recursive: true, force: true }));

const write = (name, documents, exportedAt = '2026-10-01T00:00:00.000Z') => {
  const file = path.join(dir, name);
  writeFileSync(file, JSON.stringify({ exportedAt, schemaVersion: 1, documents }));
  return file;
};

test('byPath sorterar på sökväg utan att ändra listan', () => {
  const sorted = byPath(base);
  assert.deepEqual(sorted.map((d) => d.path), ['users/u1', 'users/u1/doses/d1', 'users/u1/screenings/s1']);
  assert.equal(base[1].path, 'users/u1/screenings/s1');
});

test('samma dokument i annan ordning och med annan exporttid är identiska', () => {
  const diff = compareDocuments(base, [...base].reverse());
  assert.ok(isIdentical(diff));
  assert.equal(diff.same, 3);
});

test('saknade dokument och ändrade, tillagda eller borttagna fält rapporteras med sökväg och fältnamn', () => {
  const other = [
    doc('users/u1', { schemaVersion: 1 }),
    doc('users/u1/screenings/s1', { energy: 5, note: 'Trött efter lunch', symptoms: [{ optionId: 'o1', score: 4 }], extra: true }),
    doc('users/u1/doses/d2', { status: 'planned' }),
  ];
  const diff = compareDocuments(base, other);
  assert.deepEqual(diff.onlyA, ['users/u1/doses/d1']);
  assert.deepEqual(diff.onlyB, ['users/u1/doses/d2']);
  assert.deepEqual(diff.changed, [{ path: 'users/u1/screenings/s1', fields: ['energy', 'extra', 'symptoms'] }]);
  assert.equal(diff.same, 1);
  assert.ok(!isIdentical(diff));
});

test('compare.mjs: identiska exporter ger exitkod 0', () => {
  const result = runScript('compare.mjs', ['--a', write('a.json', base), '--b', write('b.json', [...base].reverse(), '2026-10-02T00:00:00.000Z')]);
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Inga skillnader/);
});

test('compare.mjs: skillnader ger exitkod 1 och visar sökvägar och fältnamn, aldrig värden', () => {
  const changed = base.map((d) => (d.path.endsWith('s1') ? doc(d.path, { ...d.data, note: 'Huvudvärk på kvällen' }) : d));
  const result = runScript('compare.mjs', ['--a', write('c.json', base), '--b', write('d.json', changed.slice(0, 2))]);
  assert.equal(result.status, 1, result.stderr);
  assert.match(result.stdout, /users\/u1\/screenings\/s1: note/);
  assert.match(result.stdout, /Bara i A \(1\):\n {2}users\/u1\/doses\/d1/);
  assert.doesNotMatch(result.stdout, /Trött|Huvudvärk|taken|08:00/, 'utskriften får inte innehålla värden');
});

test('compare.mjs: fel argument och fel fil ger exitkod 1 utan innehåll', () => {
  assert.equal(runScript('compare.mjs', ['--a', write('e.json', base)]).status, 1);
  const notExport = path.join(dir, 'f.json');
  writeFileSync(notExport, '{"hemligt": "Trött"');
  const result = runScript('compare.mjs', ['--a', notExport, '--b', notExport]);
  assert.equal(result.status, 1);
  assert.doesNotMatch(result.stderr, /Trött/);
});
