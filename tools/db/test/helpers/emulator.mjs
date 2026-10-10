// Gemensam testhjälpare: tester i tools/db/test körs BARA mot Firebase-emulatorn med ett
// demo-projekt – aldrig mot den riktiga databasen (skill data-safety-backup, BCK-16).
import { after, before, beforeEach } from 'node:test';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { openFirestore } from '../../lib/admin.mjs';
import { fromJson } from '../../lib/serialize.mjs';
import { readRepoFile } from './repo.mjs';

export const PROJECT_ID = 'demo-dagboken';


/** Värd och port för emulatorn; avbryter om FIRESTORE_EMULATOR_HOST saknas. */
export function emulatorHost() {
  const host = process.env.FIRESTORE_EMULATOR_HOST;
  if (!host) {
    throw new Error(
      'FIRESTORE_EMULATOR_HOST saknas – testerna körs bara mot Firebase-emulatorn. Kör från repots rot:\n' +
        `  npx --prefix tools/db firebase emulators:exec --only firestore --project ${PROJECT_ID} "npm --prefix tools/db test"`,
    );
  }
  const [hostname, port] = host.split(':');
  return { host: hostname, port: Number(port) };
}

/**
 * Testmiljö för rules-tester med repots firestore.rules – eller med [rules] (en ändrad kopia, t.ex.
 * för att mäta uttrycksbudgeten) i ett eget demo-projekt [projectId], så att repots rules står kvar.
 */
export function rulesTestEnvironment({ rules = readRepoFile('firestore.rules'), projectId = PROJECT_ID } = {}) {
  if (!projectId.startsWith('demo-')) throw new Error(`bara demo-projekt i testerna: ${projectId}`);
  return initializeTestEnvironment({
    projectId,
    firestore: { ...emulatorHost(), rules },
  });
}

/** Admin-Firestore mot emulatorn – alltid i testläge (bara emulator och demo-projekt). */
export function adminFirestore() {
  emulatorHost();
  return openFirestore({ test: true, projectId: PROJECT_ID });
}

/** Den syntetiska fixturen (test/fixtures/user.json), delad av testerna. */
export const fixture = JSON.parse(readRepoFile('tools/db/test/fixtures/user.json'));

/** Fixturens användare. */
export const UID = 'uid-test';

/** En andra användare, för tester av att bara den valda användaren rörs. */
export const otherUser = { path: 'users/annan', data: { schemaVersion: 1 } };

/** Lägger fixturens dokument direkt i databasen – utan import, så att exporten testas fristående. */
export async function seed(db, documents = fixture.documents) {
  for (const { path: docPath, data } of documents) await db.doc(docPath).set(fromJson(data));
}

/** Raderar alla användare och allt under dem. */
export async function clearUsers(db) {
  await db.recursiveDelete(db.collection('users'));
}

/**
 * Admin-Firestore med tomma användare före och efter varje test i filen. Ger en funktion som
 * returnerar databasen (den skapas i `before`).
 */
export function useCleanEmulator() {
  let db;
  before(() => {
    db = adminFirestore();
  });
  beforeEach(() => clearUsers(db));
  after(() => clearUsers(db));
  return () => db;
}
