// Går igenom en användares data enligt collections.mjs – används av export, migrate och stats.
// En samling som inte står i listan – under en användare eller i roten –
// stoppar genomgången: den skulle annars aldrig backas upp.
import { COLLECTIONS } from './collections.mjs';

const segments = (p) => p.split('/');
const ROOT = 'users';

/** Undersamlingarna direkt under ett dokument i samlingen `template`. */
const childrenOf = (template) =>
  COLLECTIONS.filter((c) => c.path.startsWith(`${template}/`) && segments(c.path).length === segments(template).length + 2);

/** Stoppar om [collections] innehåller något som inte är [known]. Sökvägen är ID:n, aldrig innehåll. */
function assertKnown(collections, known) {
  const unknown = collections.find((c) => !known.includes(c.id));
  if (unknown) {
    throw new Error(`Okänd samling ${unknown.path} – lägg till den i tools/db/lib/collections.mjs (skill data-safety-backup)`);
  }
}

/** Dokumenten i en samling och allt under dem, enligt samlingen `template`. */
async function* walkCollection(db, refs, template) {
  if (refs.length === 0) return;
  const children = childrenOf(template);
  // Ett anrop för alla dokument och parallella listningar av undersamlingar – inte två
  // sekventiella anrop per dokument.
  const [snaps, subcollections] = await Promise.all([
    db.getAll(...refs),
    Promise.all(refs.map((ref) => ref.listCollections())),
  ]);
  for (const [i, ref] of refs.entries()) {
    // listDocuments ger även "saknade" föräldrar – dokument utan egen data men med undersamlingar.
    if (snaps[i].exists) yield { path: ref.path, data: snaps[i].data() };
    assertKnown(subcollections[i], children.map((c) => c.name));
    for (const child of children) {
      yield* walkCollection(db, await ref.collection(child.name).listDocuments(), child.path);
    }
  }
}

/**
 * Alla dokument för en användare: användardokumentet först, sedan undersamlingarna. Saknas
 * användardokumentet tas undersamlingarna ändå med – en trasig användare ska inte fälla backupen.
 */
export async function* walkUser(db, uid) {
  yield* walkCollection(db, [db.doc(`${ROOT}/${uid}`)], ROOT);
}

/** ID:n för alla användare; stoppar om databasen har en rotsamling utanför listan. */
export async function userIds(db) {
  assertKnown(await db.listCollections(), [ROOT]);
  return (await db.collection(ROOT).listDocuments()).map((d) => d.id);
}

/** Samlingens namn för en dokumentsökväg, eller null om den inte finns i listan. */
export function collectionOf(docPath) {
  const parts = segments(docPath);
  if (parts.length % 2 !== 0) return null;
  const match = COLLECTIONS.find((c) => {
    const t = segments(c.path);
    return t.length === parts.length - 1 && t.every((s, i) => s.startsWith('{') || s === parts[i]);
  });
  return match?.name ?? null;
}

/**
 * Om varje segment i sökvägen är ett ID som Firestore godtar: inte tomt, inte `.`/`..`, inte
 * reserverat `__…__` och högst 1500 byte. Kontrolleras innan en import skriver något.
 */
export const hasValidIds = (docPath) =>
  segments(docPath).every((id) => id !== '' && id !== '.' && id !== '..' && !/^__.*__$/.test(id) && Buffer.byteLength(id) <= 1500);

/** Användarens ID (uid) för en dokumentsökväg. */
export const userOf = (docPath) => segments(docPath)[1];

/** Om sökvägen är ett användardokument (`users/<uid>`). */
export const isUserDoc = (docPath) => segments(docPath).length === 2;

/** Om användaren finns: eget dokument eller åtminstone undersamlingar. */
export async function userExists(db, uid) {
  const ref = db.doc(`${ROOT}/${uid}`);
  return (await ref.get()).exists || (await ref.listCollections()).length > 0;
}
