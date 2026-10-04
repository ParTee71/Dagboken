// query, get och stats (skill db-access) – bara läsning. Endast emulatorn.
import { beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { getDocument, parseWhere, queryCollection, resolveUser, userStats } from '../lib/query.mjs';
import { UID, otherUser, seed, useCleanEmulator } from './helpers/emulator.mjs';
import { runScript } from './helpers/run.mjs';

const database = useCleanEmulator();

beforeEach(() => seed(database()));

test('en enda användare väljs automatiskt; relativa samlingar tolkas i den', async () => {
  const rows = await queryCollection(database(), 'options', { fields: ['name'] });
  assert.deepEqual(rows, [
    { id: 'activity-ovrigt-c067e8', name: 'Övrigt' },
    { id: 'activity-promenad-c78928', name: 'Promenad' },
    { id: 'event-yrsel-6db696', name: 'Yrsel' },
    { id: 'symptom-huvudvark-d55e7d', name: 'Huvudvärk' },
    { id: 'symptom-ovrigt-c067e8', name: 'Övrigt' },
  ]);
  assert.deepEqual((await queryCollection(database(), 'illnessEpisodes/8d9e0f1a-2b3c-4d4e-9f5a-6b7c8d9e0f1a/checkins')).map((r) => r.id), ['a0b1c2d3-e4f5-4a6b-8c7d-9e0f1a2b3c4d']);
});

test('--where filtrerar på likhet med tolkade värden, --limit begränsar', async () => {
  const db = database();
  assert.deepEqual((await queryCollection(db, 'options', { where: [parseWhere('kind=symptom')] })).map((r) => r.id), ['symptom-huvudvark-d55e7d', 'symptom-ovrigt-c067e8']);
  assert.deepEqual((await queryCollection(db, 'doses', { where: [parseWhere('status=taken')] })).map((r) => r.id), ['3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f', 'recept_6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a_2026-09-21_Förmiddag']);
  assert.deepEqual((await queryCollection(db, 'options', { where: [parseWhere('archived=true')] })).map((r) => r.id), ['event-yrsel-6db696', 'symptom-huvudvark-d55e7d']);
  assert.deepEqual((await queryCollection(db, 'doses', { where: [parseWhere('dose="500"')] })).map((r) => r.id), ['3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f']);
  assert.deepEqual((await queryCollection(db, 'activities', { where: [parseWhere('energy=-2')] })).map((r) => r.id), ['5a6b7c8d-9e0f-4a1b-8c2d-3e4f5a6b7c8d']);
  assert.deepEqual((await queryCollection(db, 'doses', { where: [parseWhere('takenAt=null')] })).map((r) => r.id), ['recept_6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a_2026-09-21_Kväll']);
  assert.equal((await queryCollection(db, 'doses', { limit: 1 })).length, 1);
  assert.throws(() => parseWhere('utanlikhetstecken'), /fält=värde/);
});

test('--where tolkar bara strikta decimaltal som tal; citattecken tvingar text', () => {
  assert.equal(parseWhere('n=-12').value, -12);
  assert.equal(parseWhere('n=0.5').value, 0.5);
  assert.equal(parseWhere('n=007').value, 7);
  assert.equal(parseWhere('n="007"').value, '007');
  assert.equal(parseWhere('n= ').value, ' ');
  assert.equal(parseWhere('n=1e3').value, '1e3');
  assert.equal(parseWhere('n=0x10').value, '0x10');
  assert.equal(parseWhere('n="true"').value, 'true');
});

test('ett datafält som heter id skriver inte över dokument-ID:t', async () => {
  const db = database();
  await seed(db, [{ path: `users/${UID}/events/riktigt-id`, data: { id: 'falskt', note: 'X' } }]);
  const row = (await queryCollection(db, 'events', { where: [parseWhere('note=X')] }))[0];
  assert.equal(row.id, 'riktigt-id');
  assert.equal(Object.keys(row)[0], 'id');
});

test('--user som inte finns, eller som inte matchar en full sökväg, ger fel', async () => {
  const db = database();
  await assert.rejects(queryCollection(db, 'doses', { user: 'finns-inte' }), /Användaren finns-inte finns inte/);
  await assert.rejects(getDocument(db, `users/${UID}/options/activity-promenad-c78928`, { user: 'annan' }), /annan användare/);
});

test('flera användare kräver --user; users listar alla', async () => {
  const db = database();
  await seed(db, [otherUser]);
  await assert.rejects(queryCollection(db, 'doses'), /Flera användare \(2\) – ange --user/);
  assert.equal((await queryCollection(db, 'doses', { user: UID })).length, 3);
  assert.deepEqual((await queryCollection(db, 'users', { fields: ['schemaVersion'] })).map((r) => r.id).sort(), ['annan', UID]);
});

test('okända samlingar, saknade dokument och saknade användare ger tydliga fel', async () => {
  const db = database();
  await assert.rejects(queryCollection(db, 'hemligt'), /Okänd samling/);
  await assert.rejects(getDocument(db, 'doses/finns-inte'), new RegExp(`Dokumentet finns inte: users/${UID}/doses/finns-inte`));
  await assert.rejects(userStats(db, { user: 'finns-inte' }), /Användaren finns-inte finns inte/);
  await db.recursiveDelete(db.collection('users'));
  await assert.rejects(resolveUser(db), /Inga användare/);
});

test('get visar ett dokument med tidsstämplar som ISO; relativ och full sökväg ger samma', async () => {
  const db = database();
  const relative = await getDocument(db, 'doses/recept_6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a_2026-09-21_Förmiddag');
  assert.deepEqual(relative, await getDocument(db, `users/${UID}/doses/recept_6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a_2026-09-21_Förmiddag`));
  assert.deepEqual(relative.takenAt, { __ts: '2026-09-21T08:12:45.000001000Z' });
});

test('stats visar schemaVersion och antal per samling, inget innehåll', async () => {
  const [stats] = await userStats(database());
  assert.deepEqual(stats, {
    user: UID,
    schemaVersion: 1,
    counts: {
      users: 1, settings: 1, options: 5, prescriptions: 2, prnMedicines: 1, doses: 3,
      screenings: 2, activities: 2, events: 1, illnessEpisodes: 2, checkins: 2,
    },
  });
});

test('skripten från kommandoraden: tabell, --json och --help', () => {
  const run = runScript;
  const table = run('query.mjs', ['options', '--fields', 'name']);
  assert.equal(table.status, 0, table.stderr);
  assert.match(table.stdout, /^id\s+name\n/);
  assert.match(table.stdout, /\(5 dokument\)/);
  assert.deepEqual(JSON.parse(run('query.mjs', ['prescriptions', '--json', '--fields', 'name']).stdout), [
    { id: '1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9', name: 'D-vitamin' },
    { id: '6f1c2a9e-0b7d-4c55-9a43-1f2e3d4c5b6a', name: 'Levaxin' },
  ]);
  assert.match(run('stats.mjs', []).stdout, /doses: 3/);
  assert.match(run('get.mjs', ['settings/app']).stdout, /"theme"/);
  assert.match(run('get.mjs', ['--help']).stdout, /Användning: node tools\/db\/get.mjs/);
  assert.equal(run('query.mjs', []).status, 1);
});
