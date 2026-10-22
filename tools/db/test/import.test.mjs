// Appens import (BCK-6, DAT-13) genom rules: importen skriver råa dokument som klient, i batchar om högst 500
// skrivningar och högst 20 episoder, där en episod ligger i samma batch som sina incheckningar (existsAfter), med
// merge och samma dokument-id:n varje gång (idempotent). Plus migreringsmarkören, som 4.0–4.7 skrev och som rules
// fortsatt ska godta och skydda (regel 1). Testdata = fixturen user.json (syntetisk). Endast emulatorn.
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteField, doc, getCountFromServer, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { exportData } from '../lib/backup.mjs';
import { COLLECTIONS } from '../lib/collections.mjs';
import { byPath } from '../lib/compare.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { collectionOf } from '../lib/walk.mjs';
import { toClient } from './helpers/client.mjs';
import { fixture, UID, rulesTestEnvironment, useCleanEmulator } from './helpers/emulator.mjs';

const database = useCleanEmulator();
let env;
before(async () => {
  env = await rulesTestEnvironment();
});
after(async () => {
  await env?.cleanup();
});

/**
 * Det appens import skriver: fixturens dokument i sökvägsordning (episoden före sina incheckningar), utan
 * användardokumentet (det finns sedan inloggningen och skrivs aldrig) och utan den föräldralösa incheckningen (den
 * stoppar importen).
 */
const imported = byPath(fixture.documents.filter((d) => d.path !== `users/${UID}` && !d.path.includes('/utan-dokument/')));

/** Ett dokument i [collectionName] ur fixturen, som klientens värden – en giltig mall att skriva många av. */
const template = (collectionName) => toClient(imported.find((d) => collectionOf(d.path) === collectionName).data);

/** Antal dokument i samlingen på servern (det importen räknar efter skrivningen). */
const countOf = async (store, ...segments) => (await getCountFromServer(collection(store, 'users', UID, ...segments))).data().count;

/** Som appens batchskrivare: sökvägsordning, set med merge. */
const writeAll = async (store, documents = imported) => {
  const batch = writeBatch(store);
  for (const { path: docPath, data: fields } of documents) batch.set(doc(store, docPath), toClient(fields), { merge: true });
  await assertSucceeds(batch.commit());
};

test('batchformen: 20 episoder med sina incheckningar i en batch om exakt 500 skrivningar, med merge', async () => {
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  const episode = template('illnessEpisodes');
  const checkin = template('checkins');
  const batch = writeBatch(owner);
  for (let e = 0; e < 20; e++) {
    batch.set(doc(owner, 'users', UID, 'illnessEpisodes', `ep${e}`), episode, { merge: true });
    for (let c = 0; c < 24; c++) batch.set(doc(owner, 'users', UID, 'illnessEpisodes', `ep${e}`, 'checkins', `c${c}`), checkin, { merge: true });
  }
  await assertSucceeds(batch.commit());
  assert.equal(await countOf(owner, 'illnessEpisodes'), 20);
  assert.equal(await countOf(owner, 'illnessEpisodes', 'ep0', 'checkins'), 24);
  assert.equal(await countOf(owner, 'illnessEpisodes', 'ep19', 'checkins'), 24);
});

test('upprepad körning (DAT-13): samma dokument-id:n med merge ger inga dubbletter och exakt filens dokument', async () => {
  const db = database();
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  await writeAll(owner);
  const first = await exportData(db, { user: UID });
  await writeAll(owner); // avbruten körning som görs om
  const second = await exportData(db, { user: UID });
  assert.deepEqual(byPath(second.documents), byPath(first.documents));
  assert.deepEqual(byPath(second.documents.filter((d) => d.path !== `users/${UID}`)), imported, 'exakt filens dokument, inga dubbletter');
  for (const c of COLLECTIONS.filter((c) => !['users', 'checkins'].includes(c.name))) {
    assert.equal(await countOf(owner, c.name), imported.filter((d) => collectionOf(d.path) === c.name).length, c.name);
  }
});

test('ägaren raderar dokumenten i en batch – incheckningar och episoder – och en ensam incheckning får skrivas när episoden finns', async () => {
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  await assertSucceeds(setDoc(doc(owner, 'users', UID), { schemaVersion: CURRENT_VERSION }));
  await writeAll(owner);
  // En incheckning som skrivs om ensam (episoden är redan verifierad på servern) går genom existsAfter.
  const checkin = imported.find((d) => collectionOf(d.path) === 'checkins');
  await assertSucceeds(setDoc(doc(owner, checkin.path), toClient(checkin.data), { merge: true }));
  // Radera allt utom users/{uid}, incheckningar före episoder (som rundturen på enhet).
  const paths = imported.map((d) => d.path).sort((a, b) => b.split('/').length - a.split('/').length);
  const erase = writeBatch(owner);
  for (const docPath of paths) erase.delete(doc(owner, docPath));
  await assertSucceeds(erase.commit());
  for (const c of COLLECTIONS.filter((c) => !['users', 'checkins'].includes(c.name))) assert.equal(await countOf(owner, c.name), 0, c.name);
  assert.equal(await countOf(owner, 'illnessEpisodes', checkin.path.split('/')[3], 'checkins'), 0);
  assert.ok((await getDoc(doc(owner, 'users', UID))).exists(), 'användardokumentet rörs aldrig');
});

test('appens import av en 4.0-export: ägaren skriver över dokument med samma id med merge genom rules, och exporten blir filens (BCK-6)', async () => {
  const db = database();
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  await assertSucceeds(setDoc(doc(owner, 'users', UID), { schemaVersion: CURRENT_VERSION }));
  await writeAll(owner);
  // Ändrad i appen efter exporten: importen ersätter värdet med filens.
  const activity = imported.find((d) => collectionOf(d.path) === 'activities');
  await assertSucceeds(updateDoc(doc(owner, activity.path), { note: 'Ändrad i appen' }));
  await writeAll(owner);
  const exported = await exportData(db, { user: UID });
  assert.deepEqual(byPath(exported.documents.filter((d) => d.path !== `users/${UID}`)), imported);
});

// Migreringsmarkören skrevs av 4.0–4.7 (migreringen på enheten, borttagen i 4.8.0); rules är oförändrade, så en
// befintlig markör godtas fortsatt och kan aldrig ändras eller tas bort (regel 1).
test('migreringsmarkören users/{uid}.legacyMigration sätts en gång av ägaren och kan aldrig ändras eller tas bort', async () => {
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  const me = doc(owner, 'users', UID);
  await assertSucceeds(setDoc(me, { schemaVersion: CURRENT_VERSION }));
  const marker = { completedAt: serverTimestamp(), source: 'room', sourceCreatedAt: Timestamp.fromDate(new Date('2026-01-15T20:00:00Z')), appVersion: '4.0.0', counts: { doses: 4 } };
  // Ofullständig eller feltypad markör nekas.
  await assertFails(setDoc(me, { legacyMigration: { source: 'room' } }, { merge: true }), 'completedAt saknas');
  await assertFails(setDoc(me, { legacyMigration: { ...marker, source: 'okänd' } }, { merge: true }));
  await assertFails(setDoc(me, { legacyMigration: { ...marker, counts: 3 } }, { merge: true }));
  await assertFails(setDoc(me, { legacyMigration: { ...marker, appVersion: 'x'.repeat(201) } }, { merge: true }));
  await assertFails(setDoc(me, { legacyMigration: { ...marker, completedAt: Timestamp.fromDate(new Date('2026-10-07T18:30:00Z')) } }, { merge: true }), 'klientens tid duger inte – bara serverns');
  await assertFails(setDoc(me, { legacyMigration: { ...marker, counts: { doses: 4, okänd: 1 } } }, { merge: true }), 'bara kända samlingar i counts');
  await assertFails(setDoc(me, { legacyMigration: { ...marker, counts: { doses: '4' } } }, { merge: true }), 'antalen är heltal');
  await assertFails(setDoc(me, { legacyMigration: { ...marker, extra: 1 } }, { merge: true }), 'exakt markörens fält');
  const allCounts = Object.fromEntries(COLLECTIONS.filter((c) => c.name !== 'users').map((c) => [c.name, 1]));
  await assertSucceeds(setDoc(doc(env.authenticatedContext('alla').firestore(), 'users', 'alla'), { schemaVersion: 1, legacyMigration: { ...marker, counts: allCounts } }));
  await assertFails(setDoc(doc(env.authenticatedContext('annan').firestore(), 'users', UID), { legacyMigration: marker }, { merge: true }), 'någon annan');
  // Som appen gjorde: merge med serverns tid – en gång.
  await assertSucceeds(setDoc(me, { legacyMigration: marker }, { merge: true }));
  const stored = (await getDoc(me)).data();
  assert.equal(stored.schemaVersion, CURRENT_VERSION, 'resten av dokumentet orört');
  assert.ok(stored.legacyMigration.completedAt instanceof Timestamp);
  // Aldrig igen: inte skrivas om, inte ändras i ett fält, inte tas bort – men andra fält får fortfarande ändras.
  await assertFails(setDoc(me, { legacyMigration: marker }, { merge: true }));
  await assertFails(updateDoc(me, { 'legacyMigration.appVersion': '4.0.1' }));
  await assertFails(updateDoc(me, { legacyMigration: deleteField() }));
  await assertFails(setDoc(me, { schemaVersion: CURRENT_VERSION }), 'set utan merge tar bort markören');
  await assertSucceeds(updateDoc(me, { createdAt: Timestamp.now() }));
  // Ett nytt konto kan skapas med markören direkt (importen från verktygen sätter den så).
  const other = env.authenticatedContext('ny').firestore();
  await assertSucceeds(setDoc(doc(other, 'users', 'ny'), { schemaVersion: 1, legacyMigration: marker }));
});
