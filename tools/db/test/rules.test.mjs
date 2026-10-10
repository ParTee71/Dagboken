// Security rules (skill firestore-data-layer, TP-12). Körs bara mot emulatorn – se helpers/emulator.mjs.
import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { readFileSync } from 'node:fs';
import { Timestamp, collection, collectionGroup, deleteDoc, deleteField, doc, getDoc, getDocs, serverTimestamp, setDoc, updateDoc, writeBatch } from 'firebase/firestore';
import { COLLECTIONS } from '../lib/collections.mjs';
import { CURRENT_VERSION } from '../lib/schema.mjs';
import { toClient } from './helpers/client.mjs';
import { rulesTestEnvironment } from './helpers/emulator.mjs';
import { documentRulesConstant, readRepoFile, textLimits } from './helpers/repo.mjs';

const OWNER = 'anna';
/** Textgränserna (TextLimits i :core = maxShort/maxLong i rules) och en text med [n] tecken. */
const LIMIT = textLimits();
const text = (n) => 'x'.repeat(n);
const user = { schemaVersion: 1, createdAt: Timestamp.fromDate(new Date('2026-09-01T08:00:00Z')) };

/** Den syntetiska fixturen (alla samlingar, varje fält satt). */
const fixture = JSON.parse(readFileSync(new URL('./fixtures/user.json', import.meta.url), 'utf8'));
const collectionOf = (path) => path.split('/').at(-2);

/** Fixturens första dokument i [collection], som JSON – ett giltigt dokument att ändra ett fält i. */
const base = (collection) => structuredClone(fixture.documents.find((d) => d.path.split('/').length > 2 && collectionOf(d.path) === collection).data);

/** [data] med värdet [value] på fältvägen [path] (`theme.mode`, `reminders.medSlots.0.slot`). */
const withField = (data, path, value) => {
  const copy = structuredClone(data);
  const keys = path.split('.');
  let node = copy;
  for (const key of keys.slice(0, -1)) node = node[key];
  node[keys.at(-1)] = value;
  return copy;
};

/** Ett dokument i varje undersamling i collections.mjs, som sökvägssegment under users/{uid}. */
const subPaths = COLLECTIONS.filter((c) => c.name !== 'users').map((c) =>
  c.path.split('/').slice(2).map((s) => (s.startsWith('{') ? 'e1' : s)).concat(c.name === 'settings' ? 'app' : `${c.name}-1`),
);

/** Var ett giltigt dokument i varje samling skrivs i testerna nedan. */
const docPath = {
  settings: ['settings', 'app'],
  options: ['options', 'o1'],
  prescriptions: ['prescriptions', 'r1'],
  prnMedicines: ['prnMedicines', 'p1'],
  doses: ['doses', 'd1'],
  screenings: ['screenings', 's1'],
  activities: ['activities', 'a1'],
  events: ['events', 'h1'],
  illnessEpisodes: ['illnessEpisodes', 'ep1'],
  checkins: ['illnessEpisodes', 'e1', 'checkins', 'c1'],
};

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
    await setDoc(doc(ctx.firestore(), 'users', OWNER, 'illnessEpisodes', 'e1'), { type: 'Förkylning' });
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
  for (const path of subPaths) {
    await assertSucceeds(setDoc(mine(...path), { note: text(LIMIT.long), name: text(LIMIT.short) }));
    await assertFails(setDoc(mine(...path), { note: text(LIMIT.long + 1) }));
    await assertFails(setDoc(mine(...path), { name: text(LIMIT.short + 1) }));
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

test('en flyttad dos (MED-15): målet skapas med det lagrade dokumentets alla fält och källan raderas i samma skrivning', async () => {
  const store = db(OWNER);
  const dose = (id) => doc(store, 'users', OWNER, 'doses', id);
  const source = { ...toClient(base('doses')), framtidaFalt: { a: 1 } };
  await assertSucceeds(setDoc(dose('recept_r1_2026-09-21_Morgon'), source));
  const move = writeBatch(store);
  move.set(dose('flyttad'), { ...source, date: '2026-09-22', prescriptionId: 'r1' });
  move.delete(dose('recept_r1_2026-09-21_Morgon'));
  await assertSucceeds(move.commit());
  const invalid = writeBatch(store);
  invalid.set(dose('ogiltig'), { ...source, date: '22 sep' });
  invalid.delete(dose('flyttad'));
  await assertFails(invalid.commit(), 'målet valideras som en ny dos – och då raderas inte källan');
  assert.ok((await getDoc(dose('flyttad'))).exists(), 'källan står kvar');
});

test('"Markera tagen" (NOT-10): en batch med bara merges – saknad dos skapas utan anteckning, befintlig och raderad godtas', async () => {
  const { date, slot, name, strength, dose, unit, prescriptionId } = toClient(base('doses'));
  const taken = { date, slot, name, strength, dose, unit, prescriptionId, status: 'taken', takenAt: Timestamp.now() };
  await assertSucceeds(setDoc(mine('doses', 'ny-tagen'), taken, { merge: true }), 'saknas: skapas utan anteckning, skapandetid och klockslag');
  const created = (await getDoc(mine('doses', 'ny-tagen'))).data();
  assert.deepEqual(['createdAt', 'note', 'plannedTime'].filter((k) => k in created), []);
  const existing = { ...toClient(base('doses')), status: 'planned', takenAt: null, note: 'Fastande' };
  await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'users', OWNER, 'doses', 'annan-enhet'), existing));
  await assertSucceeds(setDoc(mine('doses', 'annan-enhet'), taken, { merge: true }), 'finns: slås ihop');
  const merged = (await getDoc(mine('doses', 'annan-enhet'))).data();
  assert.equal(merged.note, 'Fastande', 'anteckningen står kvar');
  assert.ok(merged.createdAt.isEqual(existing.createdAt), 'skapandetiden står kvar');
  assert.equal(merged.plannedTime, existing.plannedTime);
  assert.equal(merged.status, 'taken');
  await assertFails(setDoc(mine('doses', 'annan-enhet'), { ...taken, status: 'tagen' }, { merge: true }));

  // Som appens batch: dosen i cachen får bara status och tid – också en som raderats på servern (återuppstår som tagen).
  const store = db(OWNER);
  const doseRef = (id) => doc(store, 'users', OWNER, 'doses', id);
  const batch = writeBatch(store);
  batch.set(doseRef('annan-enhet'), { status: 'taken', takenAt: Timestamp.now() }, { merge: true });
  batch.set(doseRef('raderad'), { status: 'taken', takenAt: Timestamp.now() }, { merge: true });
  batch.set(doseRef('ny-i-batch'), taken, { merge: true });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(mine('doses', 'raderad'))).data().status, 'taken');
  assert.equal((await getDoc(mine('doses', 'annan-enhet'))).data().note, 'Fastande');
});

test('ett lagrat för stort värde eller många fält hindrar inte att andra fält sparas', async () => {
  const fields = Object.fromEntries(Array.from({ length: 45 }, (_, i) => [`f${i}`, i]));
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', OWNER, 'prescriptions', 'gammal'), { name: 'Gammal', note: text(LIMIT.long * 2), active: false, ...fields });
  });
  const ref = mine('prescriptions', 'gammal');
  await assertSucceeds(setDoc(ref, { active: true }, { merge: true }));
  await assertFails(setDoc(ref, { note: text(LIMIT.long + 1) }, { merge: true }));
  await assertFails(setDoc(ref, { nyttFalt: 1 }, { merge: true }), 'fler fält än förut');
});

test('fixturens dokument (alla samlingar, varje fält, null och tidsstämplar) godtas för ägaren', async () => {
  const { documents } = fixture;
  await env.clearFirestore();
  const owner = db('uid-test');
  const orphan = documents.filter((d) => d.path.includes('/utan-dokument/'));
  assert.equal(orphan.length, 1, 'fixturen har en incheckning utan episod');
  // Migreringsmarkörens completedAt måste vara serverns tid (OMB-7) – klienten skriver den som appen gör, med serverTimestamp().
  const asClient = (data) => (data.legacyMigration ? { ...toClient(data), legacyMigration: { ...toClient(data.legacyMigration), completedAt: serverTimestamp() } } : toClient(data));
  for (const { path, data } of documents.filter((d) => !orphan.includes(d))) await assertSucceeds(setDoc(doc(owner, path), asClient(data)));
  // Verktygen kan lagra en föräldralös incheckning (rundturen bevarar den), men appen kan inte skapa en.
  await assertFails(setDoc(doc(owner, orphan[0].path), toClient(orphan[0].data)));
  await assertFails(setDoc(doc(db('annan'), documents[1].path), toClient(documents[1].data)));
});

test('många poster sparas i en batch (t.ex. importen från 3.x)', async () => {
  const store = db(OWNER);
  const batch = writeBatch(store);
  for (let i = 0; i < 100; i++) {
    batch.set(doc(store, 'users', OWNER, 'doses', `recept_levaxin-${i}_2026-09-${String((i % 28) + 1).padStart(2, '0')}_Förmiddag`), {
      date: '2026-09-21', slot: 'morning', name: 'Levaxin', dose: '50', unit: 'µg', status: 'taken', note: null,
    });
  }
  await assertSucceeds(batch.commit());
});

// Fältvalidering per samling (etapp 2): codecarnas typer, intervall och enum-listor (skill
// firestore-data-layer). Varje fall ändrar ett fält i ett giltigt dokument ur fixturen.

/** [collection]: fall som ska nekas, som [fältväg, värde, förklaring]. */
const invalid = {
  settings: [
    ['theme', 'mörkt', 'grupp som text'],
    ['theme.mode', 'sepia', 'okänt tema'],
    ['theme.lightStartHour', 24, 'timme över 23'],
    ['theme.darkStartHour', -1, 'negativ timme'],
    ['theme.isDarkTheme', 'ja', 'bool som text'],
    ['reminders.medsEnabled', 1, 'bool som tal'],
    ['reminders.medSlots.0.slot', 'asNeeded', 'vid behov påminner inte'],
    ['reminders.medSlots.1.time', '7:00', 'klockslag utan inledande nolla'],
    ['reminders.medSlots.2.enabled', 'yes', 'bool som text'],
    ['reminders.screeningOccasions.0.occasion', 'fika', 'okänt tillfälle'],
    ['reminders.screeningOccasions.3.time', '24:00', 'klockslag efter 23:59'],
    ['reminders.periodReminderTime', '25:00', 'klockslag'],
    ['profile.birthYear', '1971', 'år som text'],
    ['profile.sex', 'annat', 'okänt kön'],
    ['legacy', 'material-you', 'grupp som text'],
    ['legacy.dynamicColor', 'ja', 'bool som text'],
    ['legacy.sheetsConfig', 42, 'adress som tal'],
    ['legacy.sheetsConfig', text(LIMIT.long + 1), 'för lång adress'],
  ],
  options: [
    ['kind', 'plant', 'okänd lista'],
    ['favorite', 'ja', 'bool som text'],
    ['sortOrder', 1.5, 'decimaltal'],
    ['archived', 1, 'bool som tal'],
    ['name', text(LIMIT.short + 1), 'för långt namn'],
  ],
  prescriptions: [
    ['strength', 500, 'styrkan är text'],
    ['strength', text(LIMIT.short + 1), 'för lång styrka'],
    ['form', 'spray', 'okänd form'],
    ['form', 1, 'form som tal'],
    ['dose', 50, 'dosen är text'],
    ['unit', 3, 'enheten är text'],
    ['slots', ['brunch'], 'okänd tidpunkt'],
    ['slots', 'morning', 'tidpunkter som text'],
    ['schedule', 'daily', 'schema som text'],
    ['schedule.repeat', 'monthly', 'okänd upprepning'],
    ['schedule.days', [0], 'veckodag 0 (ISO 1–7)'],
    ['schedule.days', [8], 'veckodag 8'],
    ['schedule.intervalDays', -1, 'negativt intervall'],
    ['period.start', '2026-13-01', 'månad 13'],
    ['period.end', 20261231, 'datum som tal'],
    ['boosts', Array.from({ length: 51 }, (_, i) => ({ id: `b${i}`, start: '2026-01-01', end: null, dose: '1', unit: 'mg' })), '51 höjningar'],
    ['boosts.0', { id: 'b0', start: '2026-01-01', dose: '1', unit: 'mg' }, 'höjning utan end'],
    ['boosts', ['b0'], 'höjning som text'],
    ['boosts.0.start', 'igår', 'ogiltigt datum'],
    ['boosts.0.end', 20260914, 'datum som tal'],
    ['boosts.0.start', '2026-02-30x', 'datum med svans'],
    ['boosts.0.dose', 25, 'höjningen är text'],
    ['boosts.1.unit', ['mg'], 'enheten är text'],
    ['boosts.0.id', null, 'höjning utan id'],
    ['boosts.0.unit', null, 'höjning utan enhet'],
    ['boosts.0.dose', ['1'], 'höjningen som lista'],
    ['boosts', [{ id: ['b'], start: null, end: null, dose: ['1'], unit: ['mg'] }], 'id, höjning och enhet som listor'],
    ['boosts', [{ id: 1, start: null, end: null, dose: 2, unit: 3 }], 'id, höjning och enhet som tal'],
    ['boosts.0', { id: 'b0', end: null, dose: '1', unit: 'mg' }, 'höjning utan start'],
    ['boosts.0.end', true, 'datum som bool'],
    ['boosts.0.start', '2026-09-01 2026-09-01', 'två datum i start'],
    ['boosts.1.start', 'null 2026-10-01', 'två värden i start när end är null'],
    ['boosts.1.end', '', 'tomt slutdatum'],
    ['active', 'true', 'bool som text'],
    ['createdAt', '2026-01-01', 'tidsstämpel som text'],
    ['note', text(LIMIT.long + 1), 'för lång anteckning'],
  ],
  prnMedicines: [
    ['slot', 'brunch', 'okänd tidpunkt'],
    ['minHoursBetween', -1, 'negativ kylperiod'],
    ['maxPerDay', 'fyra', 'tal som text'],
    ['dispensingTime', 30, 'fritext som tal'],
    ['favorite', 'ja', 'bool som text'],
    ['dose', 500, 'dosen är text'],
    ['strength', ['500 mg'], 'styrkan är text'],
    ['strength', text(LIMIT.short + 1), 'för lång styrka'],
    ['form', 'tablett', 'okänd form (svenska namnet)'],
  ],
  doses: [
    ['status', 'lost', 'okänd status'],
    ['slot', 'brunch', 'okänd tidpunkt'],
    ['date', '21/9', 'datumformat'],
    ['plannedTime', '10.00', 'klockslagsformat'],
    ['takenAt', '08:12', 'tidsstämpel som text'],
    ['prescriptionId', 5, 'id som tal'],
    ['prnId', true, 'id som bool'],
    ['dose', 50, 'dosen är text'],
    ['strength', 500, 'styrkan är text'],
    ['strength', text(LIMIT.short + 1), 'för lång styrka'],
    ['createdAt', 'igår', 'tidsstämpel som text'],
  ],
  screenings: [
    ['energy', 11, 'energi över 10'],
    ['energy', -1, 'energi under 0'],
    ['energy', 5.5, 'energi som decimaltal'],
    ['stress', 11, 'stress över 10'],
    ['occasion', 'fika', 'okänt tillfälle'],
    ['customText', text(LIMIT.short + 1), 'för lång fritext'],
    ['time', '24:00', 'klockslag efter 23:59'],
    ['symptoms', ['huvudvärk'], 'symptom som text'],
    ['symptoms', 'huvudvärk', 'symptomlista som text'],
    ['symptoms', [{ optionId: 'x', score: 11, customText: null }], 'poäng över 10'],
    ['symptoms', [{ optionId: 'x', score: -1, customText: null }], 'poäng under 0'],
    ['symptoms', [{ optionId: 'x', score: 2.5, customText: null }], 'poäng som decimaltal'],
    ['symptoms', [{ score: 3, customText: null }], 'optionId saknas'],
    ['symptoms', [{ optionId: 'x', customText: null }], 'poäng saknas'],
    ['symptoms', [{ optionId: 'x', score: '3', customText: null }], 'poäng som text'],
    ['legacySomatic', -1, '3.x-somatiska under 0'],
    ['legacySomatic', '3', '3.x-somatiska som text'],
    ['symptoms', [{ optionId: 7, score: 3, customText: null }], 'optionId som tal'],
    ['symptoms', [{ optionId: 'x', score: 3, customText: 5 }], 'fritext som tal'],
    ['symptoms', Array.from({ length: 10 }, (_, i) => ({ optionId: `s${i}`, score: i === 9 ? 11 : 1, customText: null })), 'tionde symptomet ogiltigt'],
    ['symptoms', Array.from({ length: 51 }, (_, i) => ({ optionId: `s${i}`, score: 1, customText: null })), '51 symptom'],
    // Poängmönstret i isSymptom (#296) träffar texterna nedan; de nekas för att de är texter.
    ['symptoms', [{ optionId: 'x', score: 'null', customText: null }], 'poäng som texten null'],
    ['symptoms', [{ optionId: 'x', score: '10', customText: null }], 'poäng 10 som text'],
    ['symptoms', [{ optionId: 'x', score: true, customText: null }], 'poäng som bool'],
    ['symptoms', [{ optionId: 'x', score: [3], customText: null }], 'poäng som lista'],
    ['symptoms', [{ optionId: 'x', score: { v: 3 }, customText: null }], 'poäng som objekt'],
    ['symptoms', [{ optionId: 'x', score: 100, customText: null }], 'poäng 100'],
    ['symptoms', [{ optionId: 'x', score: 10.5, customText: null }], 'poäng strax över 10 som decimaltal'],
    ['symptoms', [{ optionId: 'x', score: 3 }], 'fritext saknas'],
    ['symptoms', [{ optionId: 'x', score: 3, customText: true }], 'fritext som bool'],
    ['symptoms', [{ optionId: 'x', score: 3, customText: ['t'] }], 'fritext som lista'],
    ['symptoms', [{ optionId: null, score: 3, customText: null }], 'optionId null'],
    ['symptoms', [{ optionId: ['x'], score: 3, customText: null }], 'optionId som lista'],
    ['symptoms', [null], 'symptom null'],
  ],
  activities: [
    ['energy', 11, 'energi över 10'],
    ['energy', -11, 'energi under −10'],
    ['stress', -1, 'stress under 0'],
    ['minutes', -5, 'negativ tidsåtgång'],
    ['recovering', 'ja', 'bool som text'],
    ['drain', 1, 'bool som tal'],
    ['optionId', 3, 'id som tal'],
    ['symptoms.1.score', 11, 'poäng över 10 i andra symptomet'],
    ['symptoms.1.score', '5', 'poäng som text i andra symptomet'],
    ['symptoms.1.score', 5.5, 'poäng som decimaltal i andra symptomet'],
    ['symptoms.1.customText', 7, 'fritext som tal i andra symptomet'],
  ],
  events: [
    ['severity', 11, 'svårighetsgrad över 10'],
    ['severity', -1, 'svårighetsgrad under 0'],
    ['durationMinutes', -1, 'negativ varaktighet'],
    ['triggers', 3, 'fritext som tal'],
    ['actions', text(LIMIT.long + 1), 'för lång fritext'],
    ['date', '2026-9-21', 'datum utan inledande nolla'],
  ],
  illnessEpisodes: [
    ['type', 3, 'typ som tal'],
    ['start', '2026-9-10', 'datumformat'],
    ['end', 'pågående', 'datum som text'],
    ['createdAt', 'x', 'tidsstämpel som text'],
  ],
  checkins: [
    ['severity', 11, 'svårighetsgrad över 10'],
    ['severity', -1, 'svårighetsgrad under 0'],
    ['symptoms.0.score', 1.5, 'poäng som decimaltal'],
    ['createdAt', '2026-09-11', 'tidsstämpel som text'],
  ],
};

test('varje samling har fall som ska nekas, och dess giltiga dokument godtas', () => {
  assert.deepEqual(Object.keys(invalid).sort(), COLLECTIONS.filter((c) => c.name !== 'users').map((c) => c.name).sort());
  assert.deepEqual(Object.keys(docPath).sort(), Object.keys(invalid).sort());
});

for (const [collection, cases] of Object.entries(invalid)) {
  test(`${collection}: giltigt dokument godtas, och varje ogiltigt fält nekas – vid create och vid update`, async () => {
    const ref = mine(...docPath[collection]);
    const valid = base(collection);
    await assertSucceeds(setDoc(ref, toClient(valid)));
    for (const [path, value, why] of cases) {
      const bad = toClient(withField(valid, path, value));
      await assertFails(updateDoc(ref, { [path.split('.')[0]]: bad[path.split('.')[0]] })).catch((e) => assert.fail(`update: ${collection}.${path} – ${why}: ${e.message}`));
      await deleteDoc(ref);
      await assertFails(setDoc(ref, bad)).catch((e) => assert.fail(`create: ${collection}.${path} – ${why}: ${e.message}`));
      await assertSucceeds(setDoc(ref, toClient(valid)));
    }
  });
}

test('gränsvärdena i intervallen godtas', async () => {
  const cases = [
    ['screenings', 'energy', 0], ['screenings', 'energy', 10], ['screenings', 'stress', 0], ['screenings', 'stress', 10],
    ['activities', 'energy', -10], ['activities', 'energy', 10], ['events', 'severity', 0], ['events', 'severity', 10],
    ['checkins', 'severity', 10], ['settings', 'theme.lightStartHour', 0], ['settings', 'theme.darkStartHour', 23],
    ['screenings', 'symptoms', Array.from({ length: 50 }, (_, i) => ({ optionId: `s${i}`, score: 10, customText: `Fritext ${i}` }))],
    ['activities', 'symptoms', Array.from({ length: 50 }, (_, i) => ({ optionId: `s${i}`, score: 0, customText: null }))],
    ['checkins', 'symptoms', Array.from({ length: 50 }, (_, i) => ({ optionId: `s${i}`, score: 5, customText: null }))],
    ['screenings', 'legacySomatic', 0], ['activities', 'legacySomatic', 40], ['checkins', 'legacySomatic', null],
    // Ett symptom utan poäng (från 3.x, DAT-6): score null godtas.
    ['screenings', 'symptoms', [{ optionId: 'x', score: null, customText: null }]],
    ['checkins', 'symptoms', [{ optionId: 'x', score: 3, customText: null }, { optionId: 'y', score: null, customText: null }]],
    // Varje poäng 0–10 och null, tomma texter och texten 'null' som id och fritext (poängmönstret i isSymptom, #296).
    ...[null, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map((score) => ['activities', 'symptoms', [{ optionId: 'x', score, customText: `Fritext ${score}` }]]),
    ['activities', 'symptoms', [{ optionId: '', score: null, customText: '' }, { optionId: 'null', score: 7, customText: 'null' }]],
    ['prescriptions', 'boosts', Array.from({ length: 50 }, (_, i) => ({ id: `b${i}`, start: '2026-01-01', end: null, dose: '1', unit: 'mg' }))],
    ['prescriptions', 'boosts', [{ id: '', start: null, end: '2026-02-28', dose: '', unit: '' }, { id: 'b', start: null, end: null, dose: '1', unit: 'mg' }]],
    // Texten 'null' i ett höjningsdatum släpps igenom av det billiga mönstret i isBoost (#294); codecen
    // läser den som inget datum, samma som null (DocumentRulesTest). DocumentRules är strängare.
    ['prescriptions', 'boosts.0.start', 'null'],
    ['prescriptions', 'schedule.days', [1, 2, 3, 4, 5, 6, 7]],
    ['prescriptions', 'slots', ['morning', 'midmorning', 'lunch', 'afternoon', 'evening', 'night', 'asNeeded']],
    ['doses', 'status', 'planned'], ['doses', 'slot', 'asNeeded'], ['activities', 'minutes', 0],
    ['settings', 'legacy', null], ['settings', 'legacy.dynamicColor', null], ['settings', 'legacy.sheetsConfig', null],
    ['settings', 'legacy.dynamicColor', false], ['settings', 'legacy.sheetsConfig', text(LIMIT.long)],
    ['prescriptions', 'note', text(LIMIT.long)], ['prescriptions', 'schedule', null],
    // Styrka och form (REC-1, FAV-1): ej angivna (tom text, null) och alla former godtas.
    ['prescriptions', 'strength', ''], ['prescriptions', 'strength', text(LIMIT.short)], ['prescriptions', 'form', null],
    ['prnMedicines', 'strength', ''], ['prnMedicines', 'form', null], ['doses', 'strength', text(LIMIT.short)], ['doses', 'strength', null],
    ...['tablet', 'capsule', 'liquid', 'powder', 'inhaler', 'drops', 'patch', 'other'].flatMap((form) => [['prescriptions', 'form', form], ['prnMedicines', 'form', form]]),
  ];
  for (const [collection, path, value] of cases) {
    await assertSucceeds(setDoc(mine(...docPath[collection]), toClient(withField(base(collection), path, value))), `${collection}.${path} = ${JSON.stringify(value)}`);
  }
});

test('inställningarna är ett enda dokument: settings/app', async () => {
  await assertSucceeds(setDoc(mine('settings', 'app'), toClient(base('settings'))));
  await assertFails(setDoc(mine('settings', 'annat'), toClient(base('settings'))));
  await assertFails(setDoc(mine('settings', 'annat'), {}));
  await assertSucceeds(getDoc(mine('settings', 'app')));
});

test('inställningarna skapas och ändras gruppvis med merge (SettingsRepository.update, DAT-11)', async () => {
  // Ny användare offline: dokumentet saknas och skapas med bara det ändrade fältet.
  await env.withSecurityRulesDisabled((ctx) => deleteDoc(doc(ctx.firestore(), 'users', OWNER, 'settings', 'app')));
  await assertSucceeds(setDoc(mine('settings', 'app'), { theme: { mode: 'dark' } }, { merge: true }));
  // En annan grupp läggs till utan att den första rörs; okända fält får stå kvar.
  await assertSucceeds(setDoc(mine('settings', 'app'), { profile: { birthYear: 1979 }, futureGroup: { x: 1 } }, { merge: true }));
  const stored = (await getDoc(mine('settings', 'app'))).data();
  assert.equal(stored.theme.mode, 'dark');
  assert.equal(stored.profile.birthYear, 1979);
  // Ett ogiltigt värde i en delvis grupp nekas fortfarande.
  await assertFails(setDoc(mine('settings', 'app'), { theme: { darkStartHour: 24 } }, { merge: true }));
});

test('en incheckning skrivs bara under en episod som finns – även en som skapas i samma batch', async () => {
  const checkin = toClient(base('checkins'));
  await assertFails(setDoc(mine('illnessEpisodes', 'finns-inte', 'checkins', 'c1'), checkin));
  const store = db(OWNER);
  const batch = writeBatch(store);
  batch.set(doc(store, 'users', OWNER, 'illnessEpisodes', 'ny'), toClient(base('illnessEpisodes')));
  batch.set(doc(store, 'users', OWNER, 'illnessEpisodes', 'ny', 'checkins', 'c1'), checkin);
  await assertSucceeds(batch.commit());
  // Raderas episoden kan dess incheckningar fortfarande raderas, men inte ändras.
  await assertSucceeds(deleteDoc(mine('illnessEpisodes', 'ny')));
  await assertFails(updateDoc(mine('illnessEpisodes', 'ny', 'checkins', 'c1'), { severity: 3 }));
  await assertSucceeds(deleteDoc(mine('illnessEpisodes', 'ny', 'checkins', 'c1')));
});

test('kaskadraderingen (SJ-9): incheckningarna i batchar om högst 500 före episoden, och sedan kan ingen ny skapas', async () => {
  // Som IllnessRepository.deleteEpisode: ägaren läser incheckningarna, raderar dem i bitar om 500 och episoden sist.
  const store = db(OWNER);
  const checkin = toClient(base('checkins'));
  await assertSucceeds(setDoc(mine('illnessEpisodes', 'lang'), toClient(base('illnessEpisodes'))));
  const ids = Array.from({ length: 501 }, (_, i) => `c${String(i).padStart(3, '0')}`);
  for (let i = 0; i < ids.length; i += 500) {
    const create = writeBatch(store);
    for (const id of ids.slice(i, i + 500)) create.set(doc(store, 'users', OWNER, 'illnessEpisodes', 'lang', 'checkins', id), checkin);
    await assertSucceeds(create.commit());
  }
  const checkins = collection(store, 'users', OWNER, 'illnessEpisodes', 'lang', 'checkins');
  const stored = (await assertSucceeds(getDocs(checkins))).docs.map((d) => d.id);
  assert.equal(stored.length, 501);

  for (let i = 0; i < stored.length; i += 500) {
    const remove = writeBatch(store);
    for (const id of stored.slice(i, i + 500)) remove.delete(doc(checkins, id));
    await assertSucceeds(remove.commit());
    // Avbryts raderingen här står episoden kvar med resten av sina incheckningar – inga föräldralösa.
    assert.ok((await getDoc(mine('illnessEpisodes', 'lang'))).exists());
  }
  assert.equal((await getDocs(checkins)).size, 0);
  await assertSucceeds(deleteDoc(mine('illnessEpisodes', 'lang')));
  assert.equal((await getDoc(mine('illnessEpisodes', 'lang'))).exists(), false);
  // En incheckning från en annan enhet efter raderingen nekas (existsAfter) – den kan inte bli föräldralös.
  await assertFails(setDoc(doc(checkins, 'sen'), checkin));
  // Någon annan får varken läsa listan eller radera i den.
  await assertFails(getDocs(collection(db('annan'), 'users', OWNER, 'illnessEpisodes', 'e1', 'checkins')));
  await assertFails(deleteDoc(doc(db('annan'), 'users', OWNER, 'illnessEpisodes', 'e1', 'checkins', 'checkins-1')));
});

test('importen från 3.x: incheckningar under högst 20 befintliga episoder per batch (existsAfter)', async () => {
  // Episoder som skapas i samma batch slås upp i batchen själv; för episoder som redan finns är
  // varje episod ett dokumentuppslag, och Firestore tillåter 20 per batch. Importen (etapp 3)
  // skriver därför episoderna med sina incheckningar, eller delar upp per högst 20 episoder.
  const store = db(OWNER);
  const together = writeBatch(store);
  for (let e = 0; e < 25; e++) {
    together.set(doc(store, 'users', OWNER, 'illnessEpisodes', `ny${e}`), toClient(base('illnessEpisodes')));
    for (let c = 0; c < 4; c++) together.set(doc(store, 'users', OWNER, 'illnessEpisodes', `ny${e}`, 'checkins', `c${c}`), toClient(base('checkins')));
  }
  await assertSucceeds(together.commit());
  const later = (episodes, id) => {
    const b = writeBatch(store);
    for (let e = 0; e < episodes; e++) b.set(doc(store, 'users', OWNER, 'illnessEpisodes', `ny${e}`, 'checkins', id), toClient(base('checkins')));
    return b.commit();
  };
  await assertSucceeds(later(20, 'senare'));
  await assertFails(later(21, 'senare2'));
});

test('ett redan lagrat felaktigt värde låser inte dokumentet – bara skrivna fält kontrolleras', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', OWNER, 'activities', 'gammal'), { energy: 99, stress: 'hög', note: 'Gammal' });
  });
  await assertSucceeds(updateDoc(mine('activities', 'gammal'), { note: 'Ny anteckning' }));
  await assertFails(updateDoc(mine('activities', 'gammal'), { energy: 98 }));
});

test('en uppdatering av ett nästlat fält (punktnotation) kontrollerar hela objektet – ingen genväg förbi valideringen', async () => {
  // Fynd från firebase-security-rules-auditor ("update bypass"): affectedKeys ger toppnivåfältet,
  // så `theme.mode` valideras som en del av `theme`.
  await assertSucceeds(setDoc(mine('settings', 'app'), toClient(base('settings'))));
  await assertFails(updateDoc(mine('settings', 'app'), { 'theme.mode': 'sepia' }));
  await assertFails(updateDoc(mine('settings', 'app'), { 'reminders.periodReminderTime': '9:00' }));
  await assertFails(updateDoc(mine('settings', 'app'), { 'profile.sex': 'annat' }));
  await assertFails(updateDoc(mine('settings', 'app'), { 'legacy.dynamicColor': 'ja' }));
  await assertFails(updateDoc(mine('settings', 'app'), { 'legacy.sheetsConfig': text(LIMIT.long + 1) }));
  await assertSucceeds(updateDoc(mine('settings', 'app'), { 'theme.mode': 'light', 'profile.birthYear': 1980 }));
  await assertSucceeds(setDoc(mine(...docPath.prescriptions), toClient(base('prescriptions'))));
  await assertFails(updateDoc(mine(...docPath.prescriptions), { 'schedule.repeat': 'monthly' }));
  await assertFails(updateDoc(mine(...docPath.prescriptions), { 'period.end': '31/12' }));
  await assertSucceeds(updateDoc(mine(...docPath.prescriptions), { 'period.end': null }));
});

test('ett recept med okänt schema (Schedule.Unknown från en nyare app) kan uppdateras men inte skapas på nytt', async () => {
  // Codecen skriver tillbaka ett okänt schema oförändrat (DAT-10). Rules kontrollerar bara fält som
  // ändras, så en skrivning som lämnar schemat orört går igenom – men ett nytt dokument med ett
  // schema som rules inte känner nekas tills schemaVersion höjs (skill firestore-data-layer).
  const unknown = { repeat: 'monthly', dayOfMonth: 15 };
  const ref = mine('prescriptions', 'nyare');
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'users', OWNER, 'prescriptions', 'nyare'), toClient(withField(base('prescriptions'), 'schedule', unknown)));
  });
  await assertSucceeds(updateDoc(ref, { active: true, note: 'Ändrad i 4.0' }));
  await assertSucceeds(setDoc(ref, toClient(withField(base('prescriptions'), 'schedule', unknown))), 'hela dokumentet skrivet med schemat orört');
  await assertFails(updateDoc(ref, { schedule: { ...unknown, dayOfMonth: 16 } }), 'ändrat okänt schema');
  await deleteDoc(ref);
  await assertFails(setDoc(ref, toClient(withField(base('prescriptions'), 'schedule', unknown))), 'återskapat');
  await assertFails(setDoc(mine('prescriptions', 'ny'), toClient(withField(base('prescriptions'), 'schedule', unknown))), 'nytt');
});

test('en okänd form (från en nyare app) kan stå kvar vid uppdatering men inte skapas eller ändras till', async () => {
  // Codecen skriver tillbaka en okänd form oförändrad (unknownForm, DAT-10) – som ett okänt schema.
  for (const collection of ['prescriptions', 'prnMedicines']) {
    const ref = mine(collection, 'nyare-form');
    const stored = toClient(withField(base(collection), 'form', 'spray'));
    await env.withSecurityRulesDisabled((ctx) => setDoc(doc(ctx.firestore(), 'users', OWNER, collection, 'nyare-form'), stored));
    await assertSucceeds(updateDoc(ref, { note: 'Ändrad i 4.0', strength: '0,5 mg/dos' }), `${collection}: formen orörd`);
    await assertSucceeds(setDoc(ref, stored), `${collection}: hela dokumentet skrivet med formen orörd`);
    await assertSucceeds(updateDoc(ref, { form: 'inhaler' }), `${collection}: en känd form ersätter den okända`);
    await assertFails(updateDoc(ref, { form: 'spray' }), `${collection}: tillbaka till en okänd form`);
    await deleteDoc(ref);
    await assertFails(setDoc(ref, stored), `${collection}: återskapad med okänd form`);
  }
});

/**
 * Dokument i värsta fall för uttrycksbudgeten (rules räknar högst 1 000 uttryck per skrivning): varje
 * fält satt och så långt som DocumentRules tillåter, listorna av objekt fulla och med de dyraste
 * elementen – höjningar med både start och slut, symptom med poäng och fritext. [v] (0 eller 1)
 * varierar värdena, så att en överskrivning ändrar varje fält (uppdateringen kontrollerar då allt).
 * Nyckeln är samlingen, `rule` dess valid…-funktion.
 */
const at = (v) => Timestamp.fromDate(new Date(Date.UTC(2026, 0, 1 + v)));
const post = (v) => ({ name: text(LIMIT.short - v), note: text(LIMIT.long - v), date: `2026-03-0${1 + v}`, time: `08:0${v}`, createdAt: at(v) });
const symptoms = (v) => Array.from({ length: documentRulesConstant('MAX_SYMPTOMS') }, (_, i) => ({ optionId: `s${i}-${v}`, score: 10 - v, customText: `Fritext ${i} ${v}` }));
const WORST = {
  prescriptions: {
    rule: 'validPrescription',
    build: (v) => ({
      name: text(LIMIT.short - v), strength: text(LIMIT.short - v), form: ['inhaler', 'tablet'][v], dose: text(LIMIT.short - v), unit: text(LIMIT.short - v),
      slots: ['morning', 'midmorning', 'lunch', 'afternoon', 'evening', 'night', 'asNeeded'].slice(v),
      schedule: { repeat: ['custom', 'interval'][v], days: [1, 2, 3, 4, 5, 6, 7].slice(v), intervalDays: 3 + v },
      period: { start: `2026-01-0${1 + v}`, end: `2026-12-3${v}` },
      boosts: Array.from({ length: documentRulesConstant('MAX_BOOSTS') }, (_, i) => ({ id: `b${i}-${v}`, start: `2026-02-0${1 + v}`, end: `2026-02-2${v}`, dose: `${1 + v}`, unit: `mg${v}` })),
      active: v === 0, createdAt: at(v), note: text(LIMIT.long - v),
    }),
  },
  screenings: {
    rule: 'validScreening',
    build: (v) => ({ ...post(v), occasion: ['breakfast', 'dinner'][v], customText: text(LIMIT.short - v), energy: 10 - v, stress: 10 - v, symptoms: symptoms(v), legacySomatic: 40 + v }),
  },
  activities: {
    rule: 'validActivity',
    build: (v) => ({
      ...post(v), optionId: text(LIMIT.short - v), customText: text(LIMIT.short - v), energy: -10 + v, stress: 10 - v, symptoms: symptoms(v),
      legacySomatic: 40 + v, recovering: v === 0, drain: v === 0, minutes: 600 + v,
    }),
  },
  checkins: {
    rule: 'validCheckin',
    build: (v) => ({ ...post(v), severity: 10 - v, symptoms: symptoms(v), legacySomatic: 40 + v }),
  },
};

/** Skapar värsta-fall-dokumentet i [collection] i [store] (episoden e1 finns för incheckningen) och skriver sedan över varje fält. */
const writeWorst = async (store, collection) => {
  const ref = doc(store, 'users', OWNER, ...docPath[collection]);
  await assertSucceeds(setDoc(ref, WORST[collection].build(0)), `${collection}: skapat`);
  await assertSucceeds(setDoc(ref, WORST[collection].build(1)), `${collection}: varje fält ändrat`);
};

test('dokument i värsta fall – alla höjningar med start och slut, alla symptom med poäng och fritext, varje fält fullt – ryms i uttrycksbudgeten (#294)', async () => {
  // Före #294 nekades redan ett recept med tio höjningar med slutdatum (PERMISSION_DENIED: "maximum of
  // 1000 expressions"), fast DocumentRules och receptformuläret tillät dem – även i 3.x-flytten.
  for (const collection of Object.keys(WORST)) await writeWorst(db(OWNER), collection);
});

/** Reserven i uttrycksbudgeten för varje värsta-fall-dokument, i fältkontroller (se testet nedan) – samma krav för alla. */
const BUDGET_RESERVE_FIELDS = 5;

test(`uttrycksbudgeten har en reserv på minst ${BUDGET_RESERVE_FIELDS} fältkontroller för varje värsta-fall-dokument`, async () => {
  // Varje valid…-funktion med reserven i extra fältkontroller (som ett nytt kort textfält var):
  // ryms värsta-fall-dokumentet fortfarande har nästa fält plats. Blir testet rött har samlingen vuxit in
  // i reserven – gör kontrollerna billigare innan fältet läggs till, sänk aldrig bara reserven.
  // Mätt i emulatorn efter #296 (extra fältkontroller som ryms, inte en till): recept 5 (före #294 inga),
  // screening 8, aktivitet 5, incheckning 10 (före #296 4, 2 och 7 – isSymptom gjordes billigare som isBoost).
  // Recept och aktivitet ligger på kravet: ett nytt fält där kräver billigare kontroller först.
  const padding = (n) => " && (!('name' in w) || nullOrShort(d.get('name', null)))".repeat(n);
  let rules = readRepoFile('firestore.rules');
  for (const { rule } of Object.values(WORST)) {
    const end = new RegExp(`(function ${rule}\\(d\\) \\{[\\s\\S]*?);\\n    \\}`);
    assert.match(rules, end, rule);
    rules = rules.replace(end, `$1${padding(BUDGET_RESERVE_FIELDS)};\n    }`);
  }
  const padded = await rulesTestEnvironment({ rules, projectId: 'demo-dagboken-budget' });
  try {
    // Tomt först, så att skapandet mäts även när emulatorn återanvänds mellan körningar.
    await padded.clearFirestore();
    await padded.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'users', OWNER), user);
      await setDoc(doc(ctx.firestore(), 'users', OWNER, 'illnessEpisodes', 'e1'), { type: 'Förkylning' });
    });
    const store = padded.authenticatedContext(OWNER).firestore();
    for (const collection of Object.keys(WORST)) await writeWorst(store, collection);
  } finally {
    await padded.cleanup();
  }
});
