// Exportens JSON som klientens värden, som appen skriver dem genom rules (lib/serialize.mjs
// åt andra hållet): `{ __ts }` blir en Timestamp och en skyddad `{ __map }` sin egen map.
// Delas av rules-testet och konverterarens test.
import { Timestamp } from 'firebase/firestore';

export const toClient = (value) => {
  if (Array.isArray(value)) return value.map(toClient);
  if (value && typeof value === 'object') {
    const keys = Object.keys(value);
    if (keys.length === 1 && keys[0] === '__ts') return timestamp(value.__ts);
    const map = keys.length === 1 && keys[0] === '__map' ? value.__map : value;
    return Object.fromEntries(Object.entries(map).map(([k, v]) => [k, keys[0] === '__map' ? v : toClient(v)]));
  }
  return value;
};

/** `2026-09-21T08:12:45.000001000Z` → Timestamp med alla decimaler (som appen skriver, inte avrundat till millisekunder via Date). */
function timestamp(iso) {
  const [whole, fraction = ''] = iso.replace(/Z$/, '').split('.');
  return new Timestamp(Date.parse(`${whole}Z`) / 1000, Number(fraction.padEnd(9, '0')));
}
