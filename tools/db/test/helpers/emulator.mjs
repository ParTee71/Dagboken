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

/** Ett tal som skrivs som Firestores `doubleValue` även när det är helt (3.0) – klientens SDK skickar det som heltal. */
export class Double {
  constructor(value) { this.value = value; }
}

/** Ett klientvärde (som rules-testet skriver med setDoc) som REST-värde; hela tal blir heltal, utom [Double]. */
const toRestValue = (v) => {
  if (v === null) return { nullValue: null };
  if (v instanceof Double) return { doubleValue: v.value };
  if (typeof v === 'string') return { stringValue: v };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(toRestValue) } };
  if (typeof v?.toDate === 'function') return { timestampValue: v.toDate().toISOString() };
  if (typeof v === 'object') return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, toRestValue(x)])) } };
  throw new Error(`okänd typ i testdata: ${typeof v}`);
};

/** Osignerad ID-token för [uid] i [projectId], som emulatorn godtar (samma form som rules-unit-testing skapar). */
const mockToken = (uid, projectId) => {
  const part = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const payload = {
    iss: `https://securetoken.google.com/${projectId}`, aud: projectId, iat: 0, exp: 3600, auth_time: 0, sub: uid, user_id: uid,
    firebase: { sign_in_provider: 'custom', identities: {} },
  };
  return `${part({ alg: 'none', type: 'JWT' })}.${part(payload)}.`;
};

/**
 * Skriver hela dokumentet `users/{uid}/…[path]` som [uid] genom rules via emulatorns REST (som setDoc: create
 * eller update) – för värden som klientens SDK inte kan skicka, som [Double]. Ger HTTP-statusen (200 eller 403).
 */
export async function restSetAs(uid, path, data) {
  const { host, port } = emulatorHost();
  const url = `http://${host}:${port}/v1/projects/${PROJECT_ID}/databases/(default)/documents/users/${uid}/${path.join('/')}`;
  const response = await fetch(url, {
    method: 'PATCH',
    headers: { Authorization: `Bearer ${mockToken(uid, PROJECT_ID)}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ fields: toRestValue(data).mapValue.fields }),
  });
  await response.body?.cancel();
  return response.status;
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
