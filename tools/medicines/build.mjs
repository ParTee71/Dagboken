#!/usr/bin/env node
// Bygger appens läkemedelslista (REC-14) från Läkemedelsverkets öppna data:
//   node tools/medicines/build.mjs              # hämtar den senaste listan
//   node tools/medicines/build.mjs fil.xlsx     # från en nedladdad fil
// Skriver app/src/main/assets/medicines.tsv. Körs vid release (skill release), aldrig i CI.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dateOf, download, format, select } from './lib/medicines.mjs';
import { readFirstSheet } from './lib/xlsx.mjs';

const OUT = fileURLToPath(new URL('../../app/src/main/assets/medicines.tsv', import.meta.url));

const input = process.argv[2];
const { name, buf } = input ? { name: input, buf: await readFile(input) } : await download();
const date = dateOf(name);
if (!date) throw new Error(`Inget datum i ${name}`);
const entries = select(readFirstSheet(buf));
if (entries.length < 1000) throw new Error(`Bara ${entries.length} läkemedel – avbryter hellre än skriver en trasig lista`);
await writeFile(OUT, format(entries, date));
console.log(`${entries.length} läkemedel (${date}) → ${OUT}`);
