// Konverterarens förväntade exporter (OMB-3, :core/legacy/BackupJsonConverter): det import.mjs tar
// emot i grinden OMB-4. Varje fil godtas av torrkörningen, överlever import → export identiskt och
// kan skrivas dokument för dokument av ägaren genom rules – så valideringen i :core bevisligen
// motsvarar rules. Endast emulatorn.
import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';
import { exportData, importData } from '../lib/backup.mjs';
import { COLLECTIONS } from '../lib/collections.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { collectionOf } from '../lib/walk.mjs';
import { toClient } from './helpers/client.mjs';
import { rulesTestEnvironment, useCleanEmulator } from './helpers/emulator.mjs';
import { readRepoFile, repoRoot } from './helpers/repo.mjs';
import { runScript } from './helpers/run.mjs';

/** Användaren i de förväntade exporterna (LegacyFixtures.UID i :core). */
const UID = 'uid-legacy';
const FIXTURES = ['backup-v2', 'backup-v1'];
const expected = Object.fromEntries(
  FIXTURES.map((name) => [name, JSON.parse(readRepoFile(`tools/db/test/fixtures/legacy/${name}.expected.json`))]),
);
const byPath = (docs) => [...docs].sort((a, b) => a.path.localeCompare(b.path));

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
