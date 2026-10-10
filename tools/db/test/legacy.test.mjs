// Konverterarens förväntade exporter (OMB-3, :core/legacy/BackupJsonConverter): det import.mjs tar
// emot i grinden OMB-4. Varje fil godtas av torrkörningen, överlever import → export identiskt och
// kan skrivas dokument för dokument av ägaren genom rules – så valideringen i :core bevisligen
// motsvarar rules. Endast emulatorn.
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { Timestamp, collection, deleteField, doc, getCountFromServer, getDoc, serverTimestamp, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { exportData, importData } from '../lib/backup.mjs';
import { COLLECTIONS } from '../lib/collections.mjs';
import { byPath } from '../lib/compare.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { collectionOf } from '../lib/walk.mjs';
import { toClient } from './helpers/client.mjs';
import { fixture, UID as FIXTURE_UID, rulesTestEnvironment, useCleanEmulator } from './helpers/emulator.mjs';
import { readRepoFile, repoRoot } from './helpers/repo.mjs';
import { runScript } from './helpers/run.mjs';

/** Användaren i de förväntade exporterna (LegacyFixtures.UID i :core). */
const UID = 'uid-legacy';
const FIXTURES = ['backup-v2', 'backup-v1'];
const expected = Object.fromEntries(
  FIXTURES.map((name) => [name, JSON.parse(readRepoFile(`tools/db/test/fixtures/legacy/${name}.expected.json`))]),
);

const database = useCleanEmulator();
let env;
before(async () => {
  env = await rulesTestEnvironment();
});
after(async () => {
  await env?.cleanup();
});

test('de förväntade exporterna har dagens schemaVersion och bara kända samlingar, v2 i alla', () => {
  for (const [name, data] of Object.entries(expected)) {
    assert.equal(data.schemaVersion, CURRENT_VERSION, name);
    assert.ok(data.documents.every((d) => collectionOf(d.path) && d.path.startsWith(`users/${UID}`)), name);
    assert.equal(data.documents[0].path, `users/${UID}`);
    assert.deepEqual(data.documents[0].data, { schemaVersion: CURRENT_VERSION });
  }
  const covered = new Set(expected['backup-v2'].documents.map((d) => collectionOf(d.path)));
  assert.deepEqual([...covered].sort(), COLLECTIONS.map((c) => c.name).sort(), 'v2-fixturen fyller alla samlingar');
});

test('import.mjs --dry-run godtar filerna (grinden OMB-4, steg två)', () => {
  for (const name of FIXTURES) {
    const result = runScript('import.mjs', ['--in', path.join(repoRoot, `tools/db/test/fixtures/legacy/${name}.expected.json`), '--dry-run']);
    assert.equal(result.status, 0, `${name}: ${result.stderr}`);
    assert.match(result.stdout, /Torrkörning – skulle skriva:/);
    assert.match(result.stdout, /users: 1/);
  }
});

test('rundtur: import → export ger exakt konverterarens dokument', async () => {
  const db = database();
  for (const [name, data] of Object.entries(expected)) {
    const { written } = await importData(db, data);
    assert.equal(written.users, 1, name);
    const exported = await exportData(db, { user: UID });
    assert.deepEqual(byPath(exported.documents), byPath(data.documents), name);
    await db.recursiveDelete(db.doc(`users/${UID}`));
  }
});

test('varje konverterat dokument skrivs av ägaren genom rules – valideringen i :core motsvarar rules', async () => {
  for (const [name, data] of Object.entries(expected)) {
    await env.clearFirestore();
    const owner = env.authenticatedContext(UID).firestore();
    // I sökvägsordning ligger varje episod före sina incheckningar (existsAfter).
    for (const { path: docPath, data: fields } of byPath(data.documents)) {
      await assertSucceeds(setDoc(doc(owner, docPath), toClient(fields))).catch((e) => assert.fail(`${name} ${docPath}: ${e.message}`));
    }
  }
});

// ── Migreringen på enheten (OMB-2, OMB-7): legacy-läsaren skriver konverterarens dokument genom rules ──
// som klient, i batchar om högst 500 skrivningar och högst 20 episoder, där en episod ligger i samma
// batch som sina incheckningar (existsAfter), med merge och samma dokument-id:n varje gång (idempotent).

/** Ett dokument i [collectionName] ur v2-exporten, som klientens värden – en giltig mall att skriva många av. */
const template = (collectionName) => toClient(expected['backup-v2'].documents.find((d) => collectionOf(d.path) === collectionName).data);

/** Antal dokument i samlingen på servern (det legacy-läsaren räknar efter skrivningen). */
const countOf = async (store, ...segments) => (await getCountFromServer(collection(store, 'users', UID, ...segments))).data().count;

test('batchformen på enheten: 20 episoder med sina incheckningar i en batch om exakt 500 skrivningar, med merge (OMB-2)', async () => {
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

test('upprepad körning (OMB-7, DAT-13): samma dokument-id:n med merge ger inga dubbletter och exakt samma export', async () => {
  const db = database();
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  const data = expected['backup-v2'];
  const run = async () => {
    // Som appens batchskrivare: sökvägsordning (episoden före sina incheckningar), set med merge.
    const batch = writeBatch(owner);
    for (const { path: docPath, data: fields } of byPath(data.documents)) batch.set(doc(owner, docPath), toClient(fields), { merge: true });
    await assertSucceeds(batch.commit());
  };
  await run();
  const first = await exportData(db, { user: UID });
  await run(); // avbruten körning som görs om
  const second = await exportData(db, { user: UID });
  assert.deepEqual(byPath(second.documents), byPath(first.documents));
  assert.deepEqual(byPath(second.documents), byPath(data.documents), 'exakt konverterarens dokument, inga dubbletter');
  for (const c of COLLECTIONS.filter((c) => !['users', 'checkins'].includes(c.name))) {
    assert.equal(await countOf(owner, c.name), data.documents.filter((d) => collectionOf(d.path) === c.name).length, c.name);
  }
});

test('Avbryt flytten (OMB-7): ägaren raderar allt migreringen skrev i en batch – incheckningar och episoder – och en ensam incheckning får skrivas när episoden finns', async () => {
  await env.clearFirestore();
  const owner = env.authenticatedContext(UID).firestore();
  const data = expected['backup-v2'];
  const written = byPath(data.documents);
  const batch = writeBatch(owner);
  for (const { path: docPath, data: fields } of written) batch.set(doc(owner, docPath), toClient(fields), { merge: true });
  await assertSucceeds(batch.commit());
  // En incheckning som skrivs om ensam (episoden är redan verifierad på servern) går genom existsAfter.
  const checkin = written.find((d) => collectionOf(d.path) === 'checkins');
  await assertSucceeds(setDoc(doc(owner, checkin.path), toClient(checkin.data), { merge: true }));
  // Avbryt: exakt liggarens sökvägar (allt utom users/{uid}), incheckningar före episoder.
  const paths = written.filter((d) => collectionOf(d.path) !== 'users').map((d) => d.path).sort((a, b) => b.split('/').length - a.split('/').length);
  const erase = writeBatch(owner);
  for (const docPath of paths) erase.delete(doc(owner, docPath));
  await assertSucceeds(erase.commit());
  for (const c of COLLECTIONS.filter((c) => !['users', 'checkins'].includes(c.name))) assert.equal(await countOf(owner, c.name), 0, c.name);
  assert.equal(await countOf(owner, 'illnessEpisodes', written.find((d) => collectionOf(d.path) === 'illnessEpisodes').path.split('/').at(-1), 'checkins'), 0);
  assert.ok((await getDoc(doc(owner, 'users', UID))).exists(), 'användardokumentet rörs aldrig');
});

test('migreringsmarkören users/{uid}.legacyMigration sätts en gång av ägaren och kan aldrig ändras eller tas bort (OMB-7)', async () => {
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
  // Som appen: merge med serverns tid – en gång.
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

// ── Importen i appen (BCK-6, BCK-14, OMB-5): samma batchskrivning som flytten (merge, sökvägsordning), men en
// import ersätter också befintliga dokument med samma id – det ska gå genom rules som en uppdatering.

test('appens import av en 4.0-export: ägaren skriver över dokument med samma id med merge genom rules, och exporten blir filens (BCK-6)', async () => {
  const db = database();
  await env.clearFirestore();
  const owner = env.authenticatedContext(FIXTURE_UID).firestore();
  // Som appen: användardokumentet finns sedan inloggningen och skrivs aldrig; en föräldralös incheckning stoppar importen.
  const documents = byPath(fixture.documents.filter((d) => d.path !== `users/${FIXTURE_UID}` && !d.path.includes('/utan-dokument/')));
  await assertSucceeds(setDoc(doc(owner, 'users', FIXTURE_UID), { schemaVersion: CURRENT_VERSION }));
  const run = async () => {
    const batch = writeBatch(owner);
    for (const { path: docPath, data: fields } of documents) batch.set(doc(owner, docPath), toClient(fields), { merge: true });
    await assertSucceeds(batch.commit());
  };
  await run();
  // Ändrad i 4.0 efter exporten: importen ersätter värdet med filens.
  const activity = documents.find((d) => collectionOf(d.path) === 'activities');
  await assertSucceeds(updateDoc(doc(owner, activity.path), { note: 'Ändrad i 4.0' }));
  await run();
  const exported = await exportData(db, { user: FIXTURE_UID });
  assert.deepEqual(byPath(exported.documents.filter((d) => d.path !== `users/${FIXTURE_UID}`)), documents);
});
