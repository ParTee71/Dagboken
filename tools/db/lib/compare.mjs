// Jämförelse av två exporter dokument för dokument – grinden OMB-4 (compare.mjs) och rundturstesterna.
import { isDeepStrictEqual } from 'node:util';

/** Dokumenten sorterade på sökväg – exporternas ordning och `exportedAt` spelar ingen roll i en jämförelse. */
export const byPath = (docs) => [...docs].sort((a, b) => a.path.localeCompare(b.path));

/**
 * Skillnaderna mellan två exporters `documents`, per dokumentsökväg och toppnivåfält i `data`. Ger bara sökvägar
 * och fältnamn – aldrig värden (hälsodata): `{ onlyA, onlyB, changed: [{ path, fields }], same }`.
 */
export function compareDocuments(a, b) {
  const index = (docs) => new Map(docs.map((d) => [d.path, d.data ?? {}]));
  const left = index(a);
  const right = index(b);
  const onlyA = [...left.keys()].filter((p) => !right.has(p)).sort();
  const onlyB = [...right.keys()].filter((p) => !left.has(p)).sort();
  const changed = [];
  let same = 0;
  for (const p of [...left.keys()].filter((k) => right.has(k)).sort()) {
    const x = left.get(p);
    const y = right.get(p);
    const fields = [...new Set([...Object.keys(x), ...Object.keys(y)])]
      .filter((f) => !(f in x && f in y && isDeepStrictEqual(x[f], y[f])))
      .sort();
    if (fields.length === 0) same++;
    else changed.push({ path: p, fields });
  }
  return { onlyA, onlyB, changed, same };
}

/** Sant när exporterna har samma dokument med samma fält och värden. */
export const isIdentical = ({ onlyA, onlyB, changed }) => onlyA.length + onlyB.length + changed.length === 0;
