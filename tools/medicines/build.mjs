#!/usr/bin/env node
// Bygger appens läkemedelslista (REC-6) från Läkemedelsverkets öppna data:
//   node tools/medicines/build.mjs              # hämtar dagens lista
//   node tools/medicines/build.mjs fil.xlsx     # från en nedladdad fil
// Skriver app/src/main/assets/medicines.tsv. Körs vid release (skill release), aldrig i CI.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dateOf, findListUrl, format, select, SOURCE_PAGE } from './lib/medicines.mjs';
import { readFirstSheet } from './lib/xlsx.mjs';

const OUT = fileURLToPath(new URL('../../app/src/main/assets/medicines.tsv', import.meta.url));

async function download() {
  const page = await fetch(SOURCE_PAGE);
  if (!page.ok) throw new Error(`${SOURCE_PAGE}: HTTP ${page.status}`);
  const url = findListUrl(await page.text());
  if (!url) throw new Error('Hittade ingen lista Lakemedelsprodukter-ÅÅÅÅ-MM-DD.xlsx på sidan');
  const file = await fetch(url);
  if (!file.ok) throw new Error(`${url}: HTTP ${file.status}`);
  return { name: url, buf: Buffer.from(await file.arrayBuffer()) };
}

const input = process.argv[2];
const { name, buf } = input ? { name: input, buf: await readFile(input) } : await download();
const date = dateOf(name);
if (!date) throw new Error(`Inget datum i ${name}`);
const entries = select(readFirstSheet(buf));
if (entries.length < 1000) throw new Error(`Bara ${entries.length} läkemedel – avbryter hellre än skriver en trasig lista`);
await writeFile(OUT, format(entries, date));
console.log(`${entries.length} läkemedel (${date}) → ${OUT}`);
