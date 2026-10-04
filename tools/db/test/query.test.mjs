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
    { id: 'huvudvark', name: 'Huvudvärk' },
    { id: 'promenad', name: 'Promenad' },
    { id: 'yrsel', name: 'Yrsel' },
  ]);
  assert.deepEqual((await queryCollection(database(), 'illnessEpisodes/forkylning/checkins')).map((r) => r.id), ['c-1']);
});

test('--where filtrerar på likhet med tolkade värden, --limit begränsar', async () => {
  const db = database();
  assert.deepEqual((await queryCollection(db, 'options', { where: [parseWhere('kind=symptom')] })).map((r) => r.id), ['huvudvark']);
  assert.deepEqual((await queryCollection(db, 'doses', { where: [parseWhere('status=taken')] })).map((r) => r.id), ['prn-1', 'rx_levaxin_2026-09-21_morning']);
  assert.deepEqual((await queryCollection(db, 'options', { where: [parseWhere('archived=true')] })).map((r) => r.id), ['yrsel']);
  assert.deepEqual((await queryCollection(db, 'doses', { where: [parseWhere('dose=500')] })).map((r) => r.id), ['prn-1']);
  assert.deepEqual((await queryCollection(db, 'activities', { where: [parseWhere('energy=-2')] })).map((r) => r.id), ['a-1']);
  assert.deepEqual((await queryCollection(db, 'doses', { where: [parseWhere('takenAt=null')] })).map((r) => r.id), ['rx_levaxin_2026-09-22_morning']);
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
  await assert.rejects(getDocument(db, `users/${UID}/options/promenad`, { user: 'annan' }), /annan användare/);
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
  const relative = await getDocument(db, 'doses/rx_levaxin_2026-09-21_morning');
  assert.deepEqual(relative, await getDocument(db, `users/${UID}/doses/rx_levaxin_2026-09-21_morning`));
  assert.deepEqual(relative.takenAt, { __ts: '2026-09-21T06:12:45.000001000Z' });
});

test('stats visar schemaVersion och antal per samling, inget innehåll', async () => {
  const [stats] = await userStats(database());
  assert.deepEqual(stats, {
    user: UID,
    schemaVersion: 1,
    counts: {
      users: 1, settings: 1, options: 3, prescriptions: 1, prnMedicines: 1, doses: 3,
      screenings: 1, activities: 1, events: 1, illnessEpisodes: 1, checkins: 2,
    },
  });
});

test('skripten från kommandoraden: tabell, --json och --help', () => {
  const run = runScript;
  const table = run('query.mjs', ['options', '--fields', 'name']);
  assert.equal(table.status, 0, table.stderr);
  assert.match(table.stdout, /^id\s+name\n/);
  assert.match(table.stdout, /\(3 dokument\)/);
  assert.deepEqual(JSON.parse(run('query.mjs', ['prescriptions', '--json', '--fields', 'name']).stdout), [{ id: 'levaxin', name: 'Levaxin' }]);
  assert.match(run('stats.mjs', []).stdout, /doses: 3/);
  assert.match(run('get.mjs', ['settings/app']).stdout, /"theme"/);
  assert.match(run('get.mjs', ['--help']).stdout, /Användning: node tools\/db\/get.mjs/);
  assert.equal(run('query.mjs', []).status, 1);
});
