// Security rules (skill firestore-data-layer, TP-12). Körs bara mot emulatorn – se helpers/emulator.mjs.
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { Timestamp, collection, collectionGroup, deleteDoc, deleteField, doc, getDoc, getDocs, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { COLLECTIONS } from '../lib/collections.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { rulesTestEnvironment } from './helpers/emulator.mjs';

const OWNER = 'anna';
const user = { schemaVersion: 1, createdAt: Timestamp.fromDate(new Date('2026-09-01T08:00:00Z')) };

/** Exportens JSON (`{ __ts }`-tidsstämplar) som klientens värden, som appen skriver dem. */
const toClient = (value) => {
  if (Array.isArray(value)) return value.map(toClient);
  if (value && typeof value === 'object') {
    if (typeof value.__ts === 'string' && Object.keys(value).length === 1) return Timestamp.fromDate(new Date(value.__ts));
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toClient(v)]));
  }
  return value;
};

/** Ett dokument i varje undersamling i collections.mjs, som sökvägssegment under users/{uid}. */
const subPaths = COLLECTIONS.filter((c) => c.name !== 'users').map((c) =>
  c.path.split('/').slice(2).map((s) => (s.startsWith('{') ? 'e1' : s)).concat(`${c.name}-1`),
);

let env;
const db = (uid) => (uid ? env.authenticatedContext(uid) : env.unauthenticatedContext()).firestore();
const mine = (...path) => doc(db(OWNER), 'users', OWNER, ...path);

before(async () => {
  env = await rulesTestEnvironment();
});

after(async () => {
  await env?.cleanup();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', OWNER), user);
    for (const path of subPaths) await setDoc(doc(ctx.firestore(), 'users', OWNER, ...path), { note: 'Befintlig' });
  });
});

test('varje undersamling i collections.mjs har ett dokument i testet', () => {
  if (subPaths.length !== COLLECTIONS.length - 1) throw new Error('subPaths täcker inte alla samlingar');
});

test('ägaren läser och skriver sitt användardokument och allt under det', async () => {
  await assertSucceeds(getDoc(mine()));
  await assertSucceeds(updateDoc(mine(), { createdAt: Timestamp.now() }));
  for (const path of subPaths) {
    await assertSucceeds(getDoc(mine(...path)));
    await assertSucceeds(setDoc(mine(...path), { note: 'Ändrad' }));
    await assertSucceeds(updateDoc(mine(...path), { note: 'Igen' }));
    await assertSucceeds(deleteDoc(mine(...path)));
    await assertSucceeds(setDoc(mine(...path), { note: 'Ny' }));
  }
  await assertSucceeds(getDocs(collection(db(OWNER), 'users', OWNER, 'doses')));
});

test('någon annan och oautentiserad nekas läsning, skrivning och radering överallt', async () => {
  for (const uid of ['bertil', null]) {
    const store = db(uid);
    await assertFails(getDoc(doc(store, 'users', OWNER)));
    await assertFails(updateDoc(doc(store, 'users', OWNER), { schemaVersion: 1 }));
    await assertFails(setDoc(doc(store, 'users', OWNER), { schemaVersion: 1 }));
    for (const path of subPaths) {
      const ref = doc(store, 'users', OWNER, ...path);
      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref, { note: 'Kapat' }));
      await assertFails(updateDoc(ref, { note: 'Kapat' }));
      await assertFails(deleteDoc(ref));
    }
    await assertFails(getDocs(collection(store, 'users', OWNER, 'doses')));
  }
});

test('ingen kan lista alla användare eller fråga över allas samlingar', async () => {
  await assertFails(getDocs(collection(db(OWNER), 'users')));
  await assertFails(getDocs(collection(db(null), 'users')));
  for (const c of COLLECTIONS.filter((c) => c.name !== 'users')) {
    await assertFails(getDocs(collectionGroup(db(OWNER), c.name)));
  }
});

test('ett användardokument skapas bara av ägaren själv, och bara med schemaVersion 1', async () => {
  await assertSucceeds(setDoc(doc(db('cecilia'), 'users', 'cecilia'), { schemaVersion: 1 }));
  await assertFails(setDoc(doc(db('cecilia'), 'users', 'david'), { schemaVersion: 1 }), 'någon annans uid');
  await assertFails(setDoc(doc(db(null), 'users', 'cecilia2'), { schemaVersion: 1 }));
  await assertFails(setDoc(doc(db('erik'), 'users', 'erik'), {}), 'schemaVersion saknas');
  await assertFails(setDoc(doc(db('erik'), 'users', 'erik'), { schemaVersion: 0 }));
  await assertFails(setDoc(doc(db('erik'), 'users', 'erik'), { schemaVersion: 2 }));
  await assertFails(setDoc(doc(db('erik'), 'users', 'erik'), { schemaVersion: '1' }));
  await assertFails(setDoc(doc(db('erik'), 'users', 'erik'), { schemaVersion: 1, createdAt: '2026-10-04' }));
});

test('ett befintligt användardokument kan inte skrivas över av någon annan', async () => {
  await assertFails(setDoc(doc(db('bertil'), 'users', OWNER), { schemaVersion: 1 }));
});

test('användardokumentet kan inte raderas, inte ens av ägaren', async () => {
  await assertFails(deleteDoc(mine()));
});

test('schemaVersion kan inte sänkas, inte tas bort och inte skruvas upp förbi den högsta (BCK-15)', async () => {
  await assertFails(updateDoc(mine(), { schemaVersion: 0 }));
  await assertFails(updateDoc(mine(), { schemaVersion: '1' }));
  await assertFails(updateDoc(mine(), { schemaVersion: deleteField() }));
  await assertFails(updateDoc(mine(), { schemaVersion: CURRENT_VERSION + 1 }), 'skulle låsa ute appen');
  await assertSucceeds(updateDoc(mine(), { schemaVersion: CURRENT_VERSION }));
  // Data från en nyare app (stämplad av verktygen) får stå kvar i sin version, men aldrig sänkas.
  const newer = CURRENT_VERSION + 1;
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', 'nyare'), { schemaVersion: newer });
  });
  await assertSucceeds(updateDoc(doc(db('nyare'), 'users', 'nyare'), { schemaVersion: newer, createdAt: Timestamp.now() }));
  await assertFails(updateDoc(doc(db('nyare'), 'users', 'nyare'), { schemaVersion: CURRENT_VERSION }), 'sänkt');
});

test('saknad lagrad schemaVersion räknas som den första: ägaren får stämpla den', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', 'utan-version'), { createdAt: Timestamp.now() });
  });
  await assertSucceeds(setDoc(doc(db('utan-version'), 'users', 'utan-version'), { schemaVersion: 1 }, { merge: true }));
});

test('okända samlingar och djup nekas – bara samlingarna i collections.mjs (BCK-16)', async () => {
  await assertFails(setDoc(mine('okand', 'x'), { a: 1 }));
  await assertFails(getDoc(mine('okand', 'x')));
  await assertFails(setDoc(mine('doses', 'd1', 'extra', 'x'), { a: 1 }));
  await assertFails(setDoc(mine('illnessEpisodes', 'e1', 'checkins', 'c1', 'extra', 'x'), { a: 1 }));
  await assertFails(setDoc(mine('checkins', 'c1'), { a: 1 }), 'checkins bara under en episod');
  await assertFails(setDoc(doc(db(OWNER), 'households', 'h1'), { schemaVersion: 1 }));
  await assertFails(setDoc(doc(db(OWNER), 'annat', 'x'), { a: 1 }));
});

test('för långa texter och för många fält nekas (TP-12)', async () => {
  const long = (n) => 'x'.repeat(n);
  for (const path of subPaths) {
    await assertSucceeds(setDoc(mine(...path), { note: long(2000), name: long(200) }));
    await assertFails(setDoc(mine(...path), { note: long(2001) }));
    await assertFails(setDoc(mine(...path), { name: long(201) }));
    await assertFails(setDoc(mine(...path), { note: 42 }), 'note ska vara text');
  }
  const fields = (n) => Object.fromEntries(Array.from({ length: n }, (_, i) => [`f${i}`, i]));
  await assertSucceeds(setDoc(mine('doses', 'm1'), fields(40)));
  await assertFails(setDoc(mine('doses', 'm2'), fields(41)));
  await assertSucceeds(setDoc(mine('doses', 'm3'), fields(39)));
  await assertSucceeds(updateDoc(mine('doses', 'm3'), { extra: 1 }), 'växer till 40 fält');
  await assertFails(updateDoc(mine('doses', 'm3'), { extra2: 2 }), 'växer till 41 fält');
  await assertFails(setDoc(doc(db('cecilia'), 'users', 'cecilia'), { schemaVersion: 1, ...fields(40) }));
});

test('null, saknade och okända fält är tillåtna (codecens defaults, nyare appar)', async () => {
  await assertSucceeds(setDoc(mine('doses', 'n1'), { note: null, name: null, framtidaFalt: { a: 1 } }));
  await assertSucceeds(setDoc(mine('options', 'n2'), {}));
});

test('ett lagrat för stort värde eller många fält hindrar inte att andra fält sparas', async () => {
  const fields = Object.fromEntries(Array.from({ length: 45 }, (_, i) => [`f${i}`, i]));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', OWNER, 'prescriptions', 'gammal'), { name: 'Gammal', note: 'x'.repeat(5000), active: false, ...fields });
  });
  const ref = mine('prescriptions', 'gammal');
  await assertSucceeds(setDoc(ref, { active: true }, { merge: true }));
  await assertFails(setDoc(ref, { note: 'x'.repeat(2001) }, { merge: true }));
  await assertFails(setDoc(ref, { nyttFalt: 1 }, { merge: true }), 'fler fält än förut');
});

test('fixturens dokument (alla samlingar, null och tidsstämplar) godtas för ägaren', async () => {
  const { documents } = JSON.parse(readFileSync(new URL('./fixtures/user.json', import.meta.url), 'utf8'));
  await env.clearFirestore();
  const owner = db('uid-test');
  for (const { path, data } of documents) await assertSucceeds(setDoc(doc(owner, path), toClient(data)));
  await assertFails(setDoc(doc(db('annan'), documents[1].path), toClient(documents[1].data)));
});

test('många poster sparas i en batch (t.ex. importen från 3.x)', async () => {
  const store = db(OWNER);
  const batch = writeBatch(store);
  for (let i = 0; i < 100; i++) {
    batch.set(doc(store, 'users', OWNER, 'doses', `rx_levaxin_2026-09-${String((i % 28) + 1).padStart(2, '0')}_${i}`), {
      date: '2026-09-21', slot: 'morning', name: 'Levaxin', dose: 50, unit: 'µg', status: 'taken', note: '',
    });
  }
  await assertSucceeds(batch.commit());
});
