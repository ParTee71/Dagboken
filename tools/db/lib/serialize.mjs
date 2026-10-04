// Firestore-värden ⇄ JSON, för både export och import (skill data-safety-backup).
// Timestamp ⇄ { "__ts": "…Z" } med alla nio decimaler, så att rundturen blir exakt. En egen
// map som råkar ha nyckeln "__ts" eller "__map" som enda nyckel skyddas som { "__map": … }.
// Obs: JavaScript skiljer inte heltal från flyttal, så 2.0 kommer tillbaka som 2 –
// codecarna i :core läser båda. Tal som JSON inte kan bära exakt (heltal över 2^53, NaN,
// oändligt) stoppar exporten i stället för att tyst ändras. Felmeddelanden innehåller
// aldrig värdena (hälsodata) – anroparen lägger till dokumentets sökväg.
import { Timestamp } from 'firebase-admin/firestore';

const TS = '__ts';
const MAP = '__map';

function tsToIso(ts) {
  const whole = new Date(ts.seconds * 1000).toISOString().slice(0, 19);
  return `${whole}.${String(ts.nanoseconds).padStart(9, '0')}Z`;
}

function isoToTs(iso) {
  const match = typeof iso === 'string' ? /^(.{19})(?:\.(\d{1,9}))?Z$/.exec(iso) : null;
  const seconds = match ? Date.parse(`${match[1]}Z`) / 1000 : NaN;
  if (!Number.isFinite(seconds)) throw new Error('ogiltig tidsstämpel');
  return new Timestamp(seconds, Number((match[2] ?? '').padEnd(9, '0')));
}

const isPlainObject = (v) => v !== null && typeof v === 'object' && Object.getPrototypeOf(v) === Object.prototype;
const onlyKey = (obj) => {
  const keys = Object.keys(obj);
  return keys.length === 1 ? keys[0] : null;
};

/** Firestore-data → JSON-säkert värde. Okända typer stoppar exporten i stället för att tappas. */
export function toJson(value) {
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || (Number.isInteger(value) && !Number.isSafeInteger(value))) {
      throw new Error('ett tal kan inte exporteras exakt');
    }
    return value;
  }
  if (value === null || ['string', 'boolean'].includes(typeof value)) return value;
  if (value instanceof Timestamp) return { [TS]: tsToIso(value) };
  if (Array.isArray(value)) return value.map(toJson);
  if (isPlainObject(value)) {
    const json = Object.fromEntries(Object.entries(value).map(([k, v]) => [k, toJson(v)]));
    return [TS, MAP].includes(onlyKey(value)) ? { [MAP]: json } : json;
  }
  throw new Error(`värdetypen ${value?.constructor?.name ?? typeof value} stöds inte`);
}

/** JSON → Firestore-data. */
export function fromJson(value) {
  if (Array.isArray(value)) return value.map(fromJson);
  if (isPlainObject(value)) {
    const key = onlyKey(value);
    if (key === TS) return isoToTs(value[TS]);
    const map = key === MAP && isPlainObject(value[MAP]) ? value[MAP] : value;
    return Object.fromEntries(Object.entries(map).map(([k, v]) => [k, fromJson(v)]));
  }
  return value;
}
