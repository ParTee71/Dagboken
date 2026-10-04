// Migreringssteg – spegling av SchemaMigrator i :core. Nyckel = versionen steget migrerar
// från; varje steg är idempotent och tar (samling, dokument) → dokument. Ett nytt steg läggs
// till här och i SchemaMigrator i samma PR.
import { isDeepStrictEqual } from 'node:util';
import { CURRENT_VERSION, versionOf } from './schema.mjs';
import { fromJson, toJson } from './serialize.mjs';
import { collectionOf, userIds, walkUser } from './walk.mjs';

// Version 1 är 4.0:s första format – inga steg än. Nästa: `1: (collection, doc) => …`.
export const STEPS = {};

/**
 * Migrerar användarna till version [to]: alla dokument lyfts, och användarens schemaVersion höjs
 * först när alla är skrivna. Ger antal migrerade användare per version de lyftes från, och under
 * `fel` de användare som inte gick att migrera (de andra migreras ändå). [current] låter testerna
 * visa mekaniken med en påhittad nyare version.
 */
export async function migrateUsers(db, to, { user, dryRun = false, steps = STEPS, current = CURRENT_VERSION } = {}) {
  if (!Number.isInteger(to) || to < 1) throw new Error(`Ogiltig version: ${to}`);
  if (to > current) throw new Error(`Version ${to} är nyare än verktygets ${current}`);
  const result = {};
  for (const id of user ? [user] : await userIds(db)) {
    try {
      await migrateUser(db, id, to, { dryRun, steps, result });
    } catch (error) {
      // En trasig användare rapporteras utan att stoppa de andra; körningen fallerar sedan
      // (migrate.mjs) så att felet inte går obemärkt. Meddelandet är ID:n, aldrig innehåll.
      result.fel = [...(result.fel ?? []), `${id}: ${error.message}`];
    }
  }
  return result;
}

async function migrateUser(db, id, to, { dryRun, steps, result }) {
  const docs = [];
  for await (const doc of walkUser(db, id)) docs.push(doc);
  // Utan användardokument finns ingen version att utgå från: hoppa över och rapportera,
  // i stället för att stoppa migreringen av alla andra användare.
  if (docs[0]?.path !== `users/${id}`) {
    result['hoppade över (saknar användardokument)'] = (result['hoppade över (saknar användardokument)'] ?? 0) + 1;
    return;
  }
  const from = versionOf(docs[0].data);
  if (from >= to) return;
  for (let v = from; v < to; v++) if (!steps[v]) throw new Error(`Migreringssteg saknas för version ${v}`);
  const count = () => {
    result[`användare från v${from}`] = (result[`användare från v${from}`] ?? 0) + 1;
  };
  if (dryRun) return count();
  // Stegen får en kopia: ett steg som ändrar dokumentet på plats får inte få det att se oförändrat ut.
  const migrated = docs.map(({ path, data }) => {
    let doc = fromJson(toJson(data));
    for (let v = from; v < to; v++) doc = steps[v](collectionOf(path), doc);
    return { path, data: doc };
  });
  // Bara dokument som stegen ändrat skrivs – ett oförändrat dokument skrivs aldrig över med en
  // äldre läsning (appen kan ha sparat under tiden). Användaren stämplas sist: avbryts
  // körningen läses resten om vid nästa försök (idempotent).
  for (const [i, { path, data }] of migrated.entries()) {
    if (i > 0 && !isDeepStrictEqual(data, docs[i].data)) await db.doc(path).set(data);
  }
  // Användardokumentet likadant: har stegen inte ändrat det sätts bara versionen, så att en
  // ändring i appen under körningen inte skrivs över.
  const userDoc = migrated[0];
  if (isDeepStrictEqual(userDoc.data, docs[0].data)) await db.doc(userDoc.path).update({ schemaVersion: to });
  else await db.doc(userDoc.path).set({ ...userDoc.data, schemaVersion: to });
  count();
}
