import assert from 'node:assert/strict';
import { test } from 'node:test';
import { crc32, deflateRawSync } from 'node:zlib';
import { dateOf, findListUrl, format, select } from '../lib/medicines.mjs';
import { readFirstSheet, unzip } from '../lib/xlsx.mjs';

/** Ett minimalt zip-arkiv – första filen lagrad, resten deflate – som Excel skriver dem. */
function zip(files) {
  const locals = [];
  const central = [];
  let offset = 0;
  Object.entries(files).forEach(([name, text], i) => {
    const raw = Buffer.from(text, 'utf8');
    const method = i === 0 ? 0 : 8;
    const data = method === 0 ? raw : deflateRawSync(raw);
    const nameBuf = Buffer.from(name, 'utf8');
    const head = Buffer.alloc(30);
    head.writeUInt32LE(0x04034b50, 0);
    head.writeUInt16LE(method, 8);
    head.writeUInt32LE(crc32(raw), 14);
    head.writeUInt32LE(data.length, 18);
    head.writeUInt32LE(raw.length, 22);
    head.writeUInt16LE(nameBuf.length, 26);
    const dir = Buffer.alloc(46);
    dir.writeUInt32LE(0x02014b50, 0);
    dir.writeUInt16LE(method, 10);
    dir.writeUInt32LE(crc32(raw), 16);
    dir.writeUInt32LE(data.length, 20);
    dir.writeUInt32LE(raw.length, 24);
    dir.writeUInt16LE(nameBuf.length, 28);
    dir.writeUInt32LE(offset, 42);
    locals.push(head, nameBuf, data);
    central.push(dir, nameBuf);
    offset += head.length + nameBuf.length + data.length;
  });
  const dirBuf = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(Object.keys(files).length, 8);
  end.writeUInt16LE(Object.keys(files).length, 10);
  end.writeUInt32LE(dirBuf.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, dirBuf, end]);
}

const HEADER = ['Namn', 'Styrka', 'Form', 'ATC-kod', 'H/V', 'Försäljningsstatus'];

/** Ett blad som Läkemedelsverkets: namnrymdsprefix x:, textceller t="str", glesa celler. */
function sheet(rows) {
  const cell = (v, c, r) => (v === '' ? '' : `<x:c r="${String.fromCharCode(65 + c)}${r}" t="str"><x:v>${v.replace(/&/g, '&amp;').replace(/</g, '&lt;')}</x:v></x:c>`);
  const body = rows.map((row, r) => `<x:row r="${r + 1}">${row.map((v, c) => cell(v, c, r + 1)).join('')}</x:row>`).join('');
  return `<?xml version="1.0" encoding="utf-8"?><x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><x:sheetData>${body}</x:sheetData></x:worksheet>`;
}

const ROWS = [
  HEADER,
  ['Alvedon', '500 mg', 'Filmdragerad tablett', 'N02BE01', 'HUM', 'Finns till försäljning'],
  ['Alvedon', '60 mg', 'Suppositorium', 'N02BE01', 'HUM', 'Finns till försäljning'],
  ['Alvedon', '500 mg', 'Filmdragerad tablett', 'N02BE01', 'HUM', 'Finns till försäljning'], // parallellimport
  ['Alvedon', '1 g', 'Filmdragerad tablett', 'N02BE01', 'HUM', 'Finns inte till försäljning'],
  ['Metacam', '1,5 mg/ml', 'Oral suspension', 'QM01AC06', 'VET', 'Finns till försäljning'],
  ['Ägg & Co', '', 'Kräm', '', 'HUM', 'Finns till försäljning'],
  ['Abboticin', '250 mg', 'Filmdragerad\ntablett', 'J01FA01', 'HUM', 'Finns till försäljning'],
  ['', '5 mg', 'Tablett', '', 'HUM', 'Finns till försäljning'],
];

test('första bladet läses ur zip med prefix, entiteter och glesa celler', () => {
  const buf = zip({ '[Content_Types].xml': '<Types/>', 'xl/worksheets/sheet1.xml': sheet(ROWS), 'xl/worksheets/sheet2.xml': sheet([['Filter']]) });
  assert.deepEqual([...unzip(buf).keys()], ['[Content_Types].xml', 'xl/worksheets/sheet1.xml', 'xl/worksheets/sheet2.xml']);
  const rows = readFirstSheet(buf);
  assert.equal(rows.length, ROWS.length);
  assert.deepEqual(rows[0], HEADER);
  assert.deepEqual(rows[6], ['Ägg & Co', '', 'Kräm', '', 'HUM', 'Finns till försäljning']);
});

test('delade strängar och inlineStr läses också', () => {
  const xml = '<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>1</v></c><c r="C1" t="inlineStr"><is><t>Kapsel, hård</t></is></c></row></sheetData></worksheet>';
  const shared = '<sst><si><t>noll</t></si><si><r><t>Ip</t></r><r><t>ren</t></r></si></sst>';
  assert.deepEqual(readFirstSheet(zip({ 'xl/sharedStrings.xml': shared, 'xl/worksheets/sheet1.xml': xml })), [['Ipren', '', 'Kapsel, hård']]);
});

test('bara humanläkemedel som säljs, utan dubbletter, i svensk ordning', () => {
  assert.deepEqual(select(ROWS), [
    { name: 'Abboticin', strength: '250 mg', form: 'Filmdragerad tablett' },
    { name: 'Alvedon', strength: '60 mg', form: 'Suppositorium' },
    { name: 'Alvedon', strength: '500 mg', form: 'Filmdragerad tablett' },
    { name: 'Ägg & Co', strength: '', form: 'Kräm' },
  ]);
});

test('en saknad kolumn stoppar bygget i stället för att skriva en tom lista', () => {
  assert.throws(() => select([['Namn', 'Styrka', 'Form', 'H/V']]), /Försäljningsstatus/);
});

test('filen har källa, datum och en tabbseparerad rad per läkemedel', () => {
  const text = format(select(ROWS), '2026-10-04');
  const lines = text.trimEnd().split('\n');
  assert.match(lines[0], /^# .*Läkemedelsverket, CC BY 4\.0/);
  assert.equal(lines[1], '# updated 2026-10-04');
  assert.equal(lines[2], 'Abboticin\t250 mg\tFilmdragerad tablett');
  assert.equal(lines.at(-1), 'Ägg & Co\t\tKräm');
});

test('adressen och datumet hittas på sidan med öppna data', () => {
  const html = '<a href="/globalassets/excel/Forvaringstemperatur-och-hallbarhet-2026-10-04.xlsx">x</a> <a href="/globalassets/excel/Lakemedelsprodukter-2026-10-04.xlsx">Alla</a>';
  assert.equal(findListUrl(html), 'https://www.lakemedelsverket.se/globalassets/excel/Lakemedelsprodukter-2026-10-04.xlsx');
  assert.equal(findListUrl('<p>inget</p>'), null);
  assert.equal(dateOf('https://x/Lakemedelsprodukter-2026-10-04.xlsx'), '2026-10-04');
  assert.equal(dateOf('lista.xlsx'), null);
});
