// migrate.mjs: stämplar användaren sist och bara när alla steg finns (BCK-15). Endast emulatorn.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { exportData } from '../lib/backup.mjs';
import { migrateUsers } from '../lib/migrate.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { fixture, seed, useCleanEmulator } from './helpers/emulator.mjs';

const database = useCleanEmulator();

test('användare som redan har versionen lämnas orörda', async () => {
  const db = database();
  await seed(db);
  const before = (await exportData(db)).documents;
  assert.deepEqual(await migrateUsers(db, CURRENT_VERSION), {});
  assert.deepEqual((await exportData(db)).documents, before);
  assert.equal(before.length, fixture.documents.length);
});

test('en version nyare än verktyget vägras', async () => {
  const db = database();
  await assert.rejects(migrateUsers(db, CURRENT_VERSION + 1), /nyare än verktygets/);
});

// Mekaniken med påhittade steg 1 → 2 (current = 2), oberoende av de riktiga stegen (inga än).
const v2 = { current: 2 };

test('ett steg lyfter alla dokument och användaren stämplas med den nya versionen', async () => {
  const db = database();
  await seed(db, [
    { path: 'users/gammal', data: { schemaVersion: 1 } },
    { path: 'users/gammal/options/o1', data: { namn: 'Promenad' } },
    { path: 'users/gammal/illnessEpisodes/e1/checkins/c1', data: { namn: 'Feber' } },
  ]);
  const steps = { 1: (collection, doc) => ('namn' in doc ? { name: doc.namn, from: collection } : doc) };

  assert.deepEqual(await migrateUsers(db, 2, { ...v2, steps }), { 'användare från v1': 1 });

  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.deepEqual(docs['users/gammal/options/o1'], { name: 'Promenad', from: 'options' });
  assert.deepEqual(docs['users/gammal/illnessEpisodes/e1/checkins/c1'], { name: 'Feber', from: 'checkins' });
  assert.equal(docs['users/gammal'].schemaVersion, 2);
});

test('ett steg som ändrar dokumentet på plats skrivs ändå', async () => {
  const db = database();
  await seed(db, [
    { path: 'users/gammal', data: { schemaVersion: 1, createdAt: new Date('2026-01-01T00:00:00Z') } },
    { path: 'users/gammal/options/o1', data: { namn: 'Promenad' } },
  ]);
  const inPlace = (collection, doc) => {
    if (collection === 'options' && 'namn' in doc) { doc.name = doc.namn; delete doc.namn; }
    if (collection === 'users') doc.migrated = true;
    return doc;
  };
  await migrateUsers(db, 2, { ...v2, steps: { 1: inPlace } });
  assert.deepEqual((await db.doc('users/gammal/options/o1').get()).data(), { name: 'Promenad' });
  const user = (await db.doc('users/gammal').get()).data();
  assert.equal(user.migrated, true);
  assert.equal(user.schemaVersion, 2);
});

test('ett oförändrat användardokument får bara ny version – en ändring under körningen står kvar', async () => {
  const real = database();
  await seed(real, [
    { path: 'users/gammal', data: { schemaVersion: 1, note: 'före' } },
    { path: 'users/gammal/options/o1', data: { name: 'Promenad' } },
  ]);
  // Appen ändrar användardokumentet precis innan det stämplas.
  const bind = (target, key) => (typeof target[key] === 'function' ? target[key].bind(target) : target[key]);
  const db = new Proxy(real, {
    get(target, key) {
      if (key !== 'doc') return bind(target, key);
      return (path) => {
        const ref = target.doc(path);
        if (path !== 'users/gammal') return ref;
        return new Proxy(ref, {
          get(r, method) {
            if (method !== 'set' && method !== 'update') return bind(r, method);
            return async (...args) => {
              await real.doc(path).update({ note: 'under körningen' });
              return r[method](...args);
            };
          },
        });
      };
    },
  });

  assert.deepEqual(await migrateUsers(db, 2, { ...v2, steps: { 1: (_, doc) => doc } }), { 'användare från v1': 1 });

  const user = (await real.doc('users/gammal').get()).data();
  assert.equal(user.schemaVersion, 2);
  assert.equal(user.note, 'under körningen', 'ändringen i appen skrivs inte över med den äldre läsningen');
});

test('en trasig version under den första migreras som den första', async () => {
  const db = database();
  await seed(db, [{ path: 'users/noll', data: { schemaVersion: 0 } }]);
  assert.deepEqual(await migrateUsers(db, 1), {}, 'version 0 räknas som 1 och är redan klar');
  assert.deepEqual(await migrateUsers(db, 2, { ...v2, steps: { 1: (_, doc) => doc } }), { 'användare från v1': 1 });
});

test('en användare utan användardokument hoppas över utan att stoppa de andra', async () => {
  const db = database();
  await seed(db, [
    { path: 'users/a-trasig/options/o1', data: { namn: 'Kvar' } },
    { path: 'users/b-gammal', data: { schemaVersion: 1 } },
  ]);
  const steps = { 1: (_, doc) => doc };
  assert.deepEqual(await migrateUsers(db, 2, { ...v2, steps }), {
    'hoppade över (saknar användardokument)': 1,
    'användare från v1': 1,
  });
});

test('en användare som inte går att migrera rapporteras och stoppar inte de andra', async () => {
  const db = database();
  await seed(db, [
    { path: 'users/a-trasig', data: { schemaVersion: 1 } },
    { path: 'users/a-trasig/options/o1', data: { name: 'Går inte' } },
    { path: 'users/b-frisk', data: { schemaVersion: 1 } },
  ]);
  const steps = {
    1: (collection, doc) => {
      if (collection === 'options') throw new Error('steget klarar inte dokumentet');
      return doc;
    },
  };

  const result = await migrateUsers(db, 2, { ...v2, steps });

  assert.deepEqual(result.fel, ['a-trasig: steget klarar inte dokumentet']);
  assert.equal(result['användare från v1'], 1, 'bara den friska räknas som migrerad');
  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.equal(docs['users/a-trasig'].schemaVersion, 1, 'den trasiga stämplas inte');
  assert.equal(docs['users/b-frisk'].schemaVersion, 2);
});

test('saknat steg skriver ingenting och rapporteras', async () => {
  const db = database();
  await seed(db, [{ path: 'users/gammal', data: { schemaVersion: 1 } }]);
  const result = await migrateUsers(db, 2, { ...v2, steps: {} });
  assert.deepEqual(result.fel, ['gammal: Migreringssteg saknas för version 1']);
  const docs = Object.fromEntries((await exportData(db)).documents.map((d) => [d.path, d.data]));
  assert.equal(docs['users/gammal'].schemaVersion, 1);
});

test('torrkörning räknar men skriver ingenting', async () => {
  const db = database();
  await seed(db, [{ path: 'users/gammal', data: { schemaVersion: 1 } }, { path: 'users/gammal/options/o1', data: { namn: 'X' } }]);
  const steps = { 1: (_, doc) => ({ ...doc, ändrad: true }) };
  assert.deepEqual(await migrateUsers(db, 2, { ...v2, steps, dryRun: true }), { 'användare från v1': 1 });
  assert.deepEqual((await db.doc('users/gammal/options/o1').get()).data(), { namn: 'X' });
  assert.equal((await db.doc('users/gammal').get()).data().schemaVersion, 1);
});
