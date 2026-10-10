#!/usr/bin/env node
// Jämför två exporter fältvis per dokumentsökväg – grinden OMB-4 (ARKITEKTUR.md → Migrering, punkt 3). Läser bara
// filerna, ingen databas. Skriver antal, sökvägar och fältnamn – aldrig värden (hälsodata).
import { readFileSync } from 'node:fs';
import { parseExport } from './lib/backup.mjs';
import { runCli } from './lib/cli.mjs';
import { compareDocuments, isIdentical } from './lib/compare.mjs';

const documentsOf = (file, flag) => {
  const { documents } = parseExport(readFileSync(file, 'utf8'));
  if (!Array.isArray(documents)) throw new Error(`${flag}: filen saknar documents (inte en export från tools/db)`);
  return documents;
};

await runCli({
  usage: `Användning: node tools/db/compare.mjs --a <export.json> --b <export.json>

Jämför exporternas documents per dokumentsökväg och fält; exportedAt och ordningen spelar ingen roll.
Visar antal, sökvägar som bara finns i en av filerna och namnen på fält som skiljer – aldrig värden.
Exitkod 0 = identiska, 1 = skillnader eller fel.`,
  options: { a: { type: 'string' }, b: { type: 'string' } },
  main: async ({ a, b }) => {
    if (!a) throw new Error('--a saknas');
    if (!b) throw new Error('--b saknas');
    const left = documentsOf(a, '--a');
    const right = documentsOf(b, '--b');
    const diff = compareDocuments(left, right);
    console.log(`Dokument: A ${left.length}, B ${right.length}, lika ${diff.same}`);
    if (diff.onlyA.length) console.log(`Bara i A (${diff.onlyA.length}):\n${diff.onlyA.map((p) => `  ${p}`).join('\n')}`);
    if (diff.onlyB.length) console.log(`Bara i B (${diff.onlyB.length}):\n${diff.onlyB.map((p) => `  ${p}`).join('\n')}`);
    if (diff.changed.length) {
      console.log(`Skilda fält (${diff.changed.length} dokument):`);
      for (const { path, fields } of diff.changed) console.log(`  ${path}: ${fields.join(', ')}`);
    }
    if (isIdentical(diff)) console.log('Inga skillnader.');
    else process.exitCode = 1;
  },
});
