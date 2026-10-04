// Läsning för sessionen (skill db-access, agent db-inspektor): query, get och stats. Skriver
// aldrig och sparar inget på disk. Samlingar och sökvägar tolkas relativt en användare.
import { readUsers, summarize } from './backup.mjs';
import { toJson } from './serialize.mjs';
import { collectionOf, userExists, userIds, userOf } from './walk.mjs';

/**
 * Användaren att läsa i: [user] om angiven, annars den enda som finns. Flera användare utan
 * val ger ett fel som listar ID:na (uid, ingen persondata).
 */
export async function resolveUser(db, user) {
  if (user) {
    if (!(await userExists(db, user))) throw new Error(`Användaren ${user} finns inte`);
    return user;
  }
  const ids = await userIds(db);
  if (ids.length === 1) return ids[0];
  if (ids.length === 0) throw new Error('Inga användare finns (eller nyckeln ser inga)');
  throw new Error(`Flera användare (${ids.length}) – ange --user <uid>: ${ids.join(', ')}`);
}

/** Relativ sökväg (`doses`, `illnessEpisodes/<eid>/checkins`) → full sökväg under användaren. */
async function absolute(db, relative, user) {
  const clean = relative.replace(/^\/+|\/+$/g, '');
  if (clean === 'users') return clean;
  if (clean.startsWith('users/')) {
    if (user && userOf(clean) !== user) throw new Error(`Sökvägen hör till en annan användare än --user ${user}`);
    return clean;
  }
  return `users/${await resolveUser(db, user)}/${clean}`;
}

/**
 * `fält=värde`; true/false/null och decimaltal (`-12`, `0.5`) tolkas som sådana, allt annat är
 * text. Text som ser ut som något annat skrivs inom citattecken: `fält="007"`.
 */
export function parseWhere(expression) {
  const at = expression.indexOf('=');
  if (at < 1) throw new Error(`--where ska vara fält=värde: ${expression}`);
  const raw = expression.slice(at + 1);
  const quoted = /^"(.*)"$/.exec(raw);
  const value = quoted ? quoted[1]
    : raw === 'true' ? true : raw === 'false' ? false : raw === 'null' ? null
    : /^-?\d+(\.\d+)?$/.test(raw) ? Number(raw) : raw;
  return { field: expression.slice(0, at), value };
}

/** Dokumenten i en samling som `[{ id, ...fält }]` (JSON-värden); bara [fields] om angivna. */
export async function queryCollection(db, collection, { user, where = [], limit = 50, fields } = {}) {
  if (!Number.isInteger(limit) || limit < 1) throw new Error('--limit ska vara ett positivt heltal');
  const path = await absolute(db, collection, user);
  if (!collectionOf(`${path}/x`)) throw new Error(`Okänd samling: ${path} (se tools/db/lib/collections.mjs)`);
  let query = db.collection(path);
  for (const { field, value } of where) query = query.where(field, '==', value);
  const snap = await query.limit(limit).get();
  return snap.docs.map((doc) => {
    const data = toJson(doc.data());
    const picked = fields ? Object.fromEntries(fields.filter((f) => f in data).map((f) => [f, data[f]])) : data;
    // Dokument-ID:t först och alltid – ett datafält som heter "id" skriver inte över det.
    const row = { id: doc.id, ...picked };
    row.id = doc.id;
    return row;
  });
}

/** Ett dokument som JSON-värden, eller ett fel om det inte finns. */
export async function getDocument(db, docPath, { user } = {}) {
  const path = await absolute(db, docPath, user);
  if (!collectionOf(path)) throw new Error(`Okänd sökväg: ${path}`);
  const snap = await db.doc(path).get();
  if (!snap.exists) throw new Error(`Dokumentet finns inte: ${path}`);
  return toJson(snap.data());
}

/** Per användare: schemaVersion och antal dokument per samling – inget innehåll. */
export async function userStats(db, { user } = {}) {
  return (await readUsers(db, { user })).map(({ user: id, schemaVersion, documents }) => ({
    user: id,
    schemaVersion,
    counts: summarize(documents),
  }));
}
