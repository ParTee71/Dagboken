// Regel 1: all data överlever export → radera → import → export (BCK-12, BCK-16). Endast emulatorn.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData, importData, MAX_BATCH } from '../lib/backup.mjs';
import { COLLECTIONS } from '../lib/collections.mjs';
import { collectionOf } from '../lib/walk.mjs';
import { UID, clearUsers, fixture, otherUser, seed, useCleanEmulator } from './helpers/emulator.mjs';

const database = useCleanEmulator();
const byPath = (docs) => [...docs].sort((a, b) => a.path.localeCompare(b.path));

test('fixturen täcker alla samlingar', () => {
  const covered = new Set(fixture.documents.map((d) => collectionOf(d.path)));
  assert.deepEqual([...covered].sort(), COLLECTIONS.map((c) => c.name).sort());
});

test('exporten innehåller exakt fixturens dokument, med tidsstämplar på mikrosekunden (Firestores precision)', async () => {
  const db = database();
  await seed(db);
  const exported = await exportData(db, { now: new Date('2026-10-01T00:00:00Z') });
  assert.equal(exported.exportedAt, '2026-10-01T00:00:00.000Z');
  assert.equal(exported.schemaVersion, 1);
  assert.deepEqual(byPath(exported.documents), byPath(fixture.documents));
});

test('rundtur: export → radera → import → export ger identiskt innehåll', async () => {
  const db = database();
  await seed(db);
  const first = await exportData(db);
  await clearUsers(db);
  assert.equal((await exportData(db)).documents.length, 0);

  const { written } = await importData(db, first);
  assert.equal(written.doses, 3);
  assert.equal(written.checkins, 2);

  const second = await exportData(db);
  assert.deepEqual(byPath(second.documents), byPath(first.documents));
});

test('--user exporterar och importerar bara den användaren', async () => {
  const db = database();
  await seed(db);
  await seed(db, [otherUser]);
  const one = await exportData(db, { user: UID });
  assert.ok(one.documents.every((d) => d.path.startsWith(`users/${UID}`)));

  const all = await exportData(db);
  await clearUsers(db);
  await importData(db, all, { user: 'annan' });
  assert.deepEqual((await exportData(db)).documents.map((d) => d.path), ['users/annan']);
});

test('torrkörning skriver ingenting', async () => {
  const db = database();
  const { written } = await importData(db, { schemaVersion: 1, documents: fixture.documents }, { dryRun: true });
  assert.equal(written.users, 1);
  assert.equal((await exportData(db)).documents.length, 0);
});

test(`import delar upp i batchar om ${MAX_BATCH}`, async () => {
  const db = database();
  const documents = [
    { path: 'users/stor', data: { schemaVersion: 1 } },
    ...Array.from({ length: MAX_BATCH + 20 }, (_, i) => ({
      path: `users/stor/doses/dos-${String(i).padStart(4, '0')}`,
      data: { name: `Dos ${i}`, dose: i },
    })),
  ];
  await importData(db, { schemaVersion: 1, documents });
  assert.equal((await exportData(db)).documents.length, MAX_BATCH + 21);
});

test('en användare utan eget dokument exporteras ändå och fäller inte backupen', async () => {
  const db = database();
  await seed(db);
  await seed(db, [{ path: 'users/trasig/doses/d1', data: { name: 'Kvar' } }]);
  const paths = (await exportData(db)).documents.map((d) => d.path);
  assert.ok(paths.includes('users/trasig/doses/d1'));
  assert.ok(paths.includes(`users/${UID}`));
  await assert.rejects(exportData(db, { user: 'finns-inte' }), /finns inte/);
});

test('--replace gör användaren exakt som i filen: dokument som tillkommit efter backupen tas bort', async () => {
  const db = database();
  await seed(db);
  await seed(db, [otherUser]);
  const backup = await exportData(db, { user: UID });
  await seed(db, [
    { path: `users/${UID}/illnessEpisodes/forkylning/checkins/ny`, data: { note: 'Tillkommen' } },
    { path: `users/${UID}/doses/ny`, data: { name: 'Ny' } },
  ]);

  const dry = await importData(db, backup, { replace: true, dryRun: true });
  assert.deepEqual(dry.removed, { checkins: 1, doses: 1 });
  assert.equal((await exportData(db, { user: UID })).documents.length, fixture.documents.length + 2);

  await importData(db, backup, { replace: true });
  assert.deepEqual(byPath((await exportData(db, { user: UID })).documents), byPath(backup.documents));
  assert.ok((await exportData(db)).documents.some((d) => d.path === 'users/annan'), 'andra användare rörs inte');
});

test('import skriver ingenting om ett dokument längre fram i filen är trasigt', async () => {
  const db = database();
  const documents = [
    { path: 'users/u', data: { schemaVersion: 1 } },
    ...Array.from({ length: MAX_BATCH + 5 }, (_, i) => ({ path: `users/u/doses/d${i}`, data: { name: `D${i}` } })),
    { path: 'users/u/doses/trasig', data: { at: { __ts: 'inte ett datum' } } },
  ];
  await assert.rejects(importData(db, { schemaVersion: 1, documents }), /users\/u\/doses\/trasig: ogiltig tidsstämpel/);
  assert.equal((await exportData(db)).documents.length, 0);
});

test('en rotsamling utanför listan stoppar exporten', async () => {
  const db = database();
  await seed(db);
  await db.doc('okand/x').set({ a: 1 });
  try {
    await assert.rejects(exportData(db), /Okänd samling okand/);
  } finally {
    await db.recursiveDelete(db.collection('okand'));
  }
});

test('en samling som saknas i collections.mjs stoppar exporten i stället för att tappas', async () => {
  const db = database();
  await seed(db, [
    { path: 'users/u', data: { schemaVersion: 1 } },
    { path: 'users/u/hemlig/x', data: { a: 1 } },
  ]);
  await assert.rejects(exportData(db), /Okänd samling users\/u\/hemlig/);
});

test('en okänd undersamling under en episod stoppar också exporten', async () => {
  const db = database();
  await seed(db, [
    { path: 'users/u', data: { schemaVersion: 1 } },
    { path: 'users/u/illnessEpisodes/e/annat/x', data: { a: 1 } },
  ]);
  await assert.rejects(exportData(db), /Okänd samling users\/u\/illnessEpisodes\/e\/annat/);
});
