// Export och import – logiken bakom export.mjs, import.mjs och veckobackupen (BCK-12, BCK-16).
import { CURRENT_VERSION, versionOf } from './schema.mjs';
import { FieldPath } from 'firebase-admin/firestore';
import { fromJson, toJson } from './serialize.mjs';
import { collectionOf, hasValidIds, isUserDoc, userIds, userOf, walkUser } from './walk.mjs';

/** Firestores gräns för antal skrivningar i en batch. */
export const MAX_BATCH = 500;

/** Kör [convert] och lägger till dokumentets sökväg i ett fel – aldrig värdet. */
function withPath(path, convert) {
  try {
    return convert();
  } catch (error) {
    throw new Error(`${path}: ${error.message}`);
  }
}

/**
 * `{ exportedAt, schemaVersion, documents: [{ path, data }] }` för en eller alla användare.
 * `schemaVersion` är den högsta bland användarna (informativ – importen kontrollerar varje användare).
 */
export async function exportData(db, { user, now = new Date() } = {}) {
  const documents = [];
  let schemaVersion = null;
  for (const read of await readUsers(db, { user })) {
    if (read.schemaVersion !== null) schemaVersion = Math.max(schemaVersion ?? 0, read.schemaVersion);
    for (const doc of read.documents) documents.push({ path: doc.path, data: withPath(doc.path, () => toJson(doc.data)) });
  }
  return { exportedAt: now.toISOString(), schemaVersion: schemaVersion ?? CURRENT_VERSION, documents };
}

/**
 * Alla dokument och schemaVersion per användare ([user] eller alla) – den enda läsningen
 * av hela användare, delad av export och `stats`. En angiven användare som inte finns ger fel.
 */
export async function readUsers(db, { user } = {}) {
  const ids = user ? [user] : await userIds(db);
  const result = [];
  for (const id of ids) {
    const documents = [];
    let schemaVersion = null;
    for await (const doc of walkUser(db, id)) {
      if (isUserDoc(doc.path)) schemaVersion = versionOf(doc.data);
      documents.push(doc);
    }
    if (user && documents.length === 0) throw new Error(`Användaren ${id} finns inte`);
    result.push({ user: id, schemaVersion, documents });
  }
  return result;
}

/**
 * Läser en exportfil. Ett parsningsfel ger ett fast meddelande: Nodes eget meddelande citerar
 * filens innehåll, och det är hälsodata.
 */
export function parseExport(text) {
  try {
    return JSON.parse(text);
  } catch {
    throw new Error('Filen är inte giltig JSON (innehållet visas inte)');
  }
}

/** Antal dokument per samling – det som visas i stället för innehåll (hälsodata). */
export function summarize(documents) {
  const counts = {};
  for (const { path } of documents) {
    const name = collectionOf(path);
    counts[name] = (counts[name] ?? 0) + 1;
  }
  return counts;
}

/**
 * Kontrollerar och översätter hela filen innan något skrivs: format, kända sökvägar med giltiga
 * och unika ID:n, varje användares schemaVersion och varje dokuments värden. Ger `[{ path, data }]` redo att skrivas.
 * Med `update` läses en ändringsfil (`updates`, bara ändrade fält per dokument – t.ex. från
 * `:core:matchMedicines`); den rör aldrig användardokumentet. En ändringsfil utan `update` vägras, eftersom
 * en vanlig import skriver hela dokument och då skulle tömma alla andra fält.
 */
export function prepareImport(data, { user, update = false } = {}) {
  if (update) {
    if (!data || !Array.isArray(data.updates)) throw new Error('Filen är ingen ändringsfil (updates saknas) – --update läser bara utdata från :core:matchMedicines');
  } else {
    if (data && Array.isArray(data.updates) && !Array.isArray(data.documents)) {
      throw new Error('Filen innehåller bara ändrade fält (updates) – importera den med --update');
    }
    if (!data || !Array.isArray(data.documents)) throw new Error('Filen är inte en export från tools/db (documents saknas)');
  }
  const all = update ? data.updates : data.documents;
  const documents = user
    ? all.filter((d) => userOf(d.path ?? '') === user)
    : all;
  if (user && documents.length === 0) throw new Error(`Användaren ${user} finns inte i filen`);
  const seen = new Set();
  return documents.map(({ path, data: doc }) => {
    if (typeof path !== 'string' || !collectionOf(path)) throw new Error(`Okänd sökväg i filen: ${path}`);
    if (!hasValidIds(path)) throw new Error(`Ogiltigt ID i sökvägen: ${path}`);
    if (seen.has(path)) throw new Error(`${path} finns två gånger i filen`);
    seen.add(path);
    if (doc === null || typeof doc !== 'object' || Array.isArray(doc)) throw new Error(`${path}: data saknas`);
    if (update && isUserDoc(path)) throw new Error(`${path}: en ändringsfil får inte ändra användardokumentet`);
    if (update && Object.keys(doc).length === 0) throw new Error(`${path}: inga fält att ändra`);
    if (isUserDoc(path) && versionOf(doc) > CURRENT_VERSION) {
      throw new Error(`${path} har schemaVersion ${versionOf(doc)}, verktyget förstår bara ${CURRENT_VERSION} – uppdatera tools/db först`);
    }
    return { path, data: withPath(path, () => fromJson(doc)) };
  });
}

/** Dokument som finns i databasen under filens användare men inte i filen. */
async function extraDocuments(db, documents) {
  const inFile = new Set(documents.map((d) => d.path));
  const extra = [];
  for (const id of new Set(documents.map((d) => userOf(d.path)))) {
    for await (const doc of walkUser(db, id)) if (!inFile.has(doc.path)) extra.push(doc.path);
  }
  return extra;
}

async function commitInBatches(db, operations) {
  for (let i = 0; i < operations.length; i += MAX_BATCH) {
    const batch = db.batch();
    for (const op of operations.slice(i, i + MAX_BATCH)) op(batch);
    await batch.commit();
  }
}

/** Vilka av [documents] som finns i databasen, läst i bitar om högst MAX_BATCH. */
async function existing(db, documents) {
  const found = new Set();
  for (let i = 0; i < documents.length; i += MAX_BATCH) {
    const snaps = await db.getAll(...documents.slice(i, i + MAX_BATCH).map((d) => db.doc(d.path)));
    for (const snap of snaps) if (snap.exists) found.add(snap.ref.path);
  }
  return found;
}

/** Fälten i [doc] som `update`-argument: varje toppnyckel som en hel fältväg (en punkt i ett namn är ingen väg). */
const updateArgs = (doc) => Object.entries(doc).flatMap(([key, value]) => [new FieldPath(key), value]);

/**
 * Skriver dokumenten exakt som i filen (set utan merge), i batchar om högst 500 – först när
 * hela filen godkänts. `replace` tar dessutom bort dokument under filens användare som inte finns
 * i filen, så att användarens data blir exakt som i backupen. `update` läser en ändringsfil och skriver
 * bara dess fält i dokument som finns (Firestores `update`: andra fält står kvar, inget skapas); ett
 * dokument som saknas räknas som hoppat. `dryRun` skriver ingenting (med `replace` och `update` läses
 * databasen för att räkna).
 * Ger `{ written, removed, skipped }`: antal per samling.
 */
export async function importData(db, data, { user, dryRun = false, replace = false, update = false } = {}) {
  if (replace && update) throw new Error('--replace och --update går inte att kombinera');
  const documents = prepareImport(data, { user, update });
  const removed = replace ? await extraDocuments(db, documents) : [];
  const found = update ? await existing(db, documents) : null;
  const targets = update ? documents.filter((d) => found.has(d.path)) : documents;
  const skipped = update ? documents.filter((d) => !found.has(d.path)) : [];
  if (!dryRun) {
    await commitInBatches(db, [
      ...removed.map((path) => (batch) => batch.delete(db.doc(path))),
      ...targets.map(({ path, data: doc }) => (update
        ? (batch) => batch.update(db.doc(path), ...updateArgs(doc))
        : (batch) => batch.set(db.doc(path), doc))),
    ]);
  }
  return {
    written: summarize(targets),
    removed: summarize(removed.map((path) => ({ path }))),
    skipped: summarize(skipped),
  };
}
