// Läser första bladet i en .xlsx utan beroenden: zip-katalogen, inflate och cellernas XML.
// Räcker för Läkemedelsverkets listor (textceller, ev. delade strängar); inte en allmän Excel-läsare:
// bara lagrade och deflate-komprimerade filer, inte zip64 (arkiv över 4 GB eller 65 535 filer).
import { inflateRawSync } from 'node:zlib';

/** Filerna i ett zip-arkiv som namn → Buffer (bara lagrade och deflate-komprimerade). */
export function unzip(buf) {
  const eocd = buf.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  if (eocd < 0) throw new Error('Inte ett zip-arkiv');
  const count = buf.readUInt16LE(eocd + 10);
  let at = buf.readUInt32LE(eocd + 16);
  const files = new Map();
  for (let i = 0; i < count; i++) {
    if (buf.readUInt32LE(at) !== 0x02014b50) throw new Error('Trasig zip-katalog');
    const method = buf.readUInt16LE(at + 10);
    const size = buf.readUInt32LE(at + 20);
    const nameLen = buf.readUInt16LE(at + 28);
    const extraLen = buf.readUInt16LE(at + 30);
    const commentLen = buf.readUInt16LE(at + 32);
    const local = buf.readUInt32LE(at + 42);
    const name = buf.toString('utf8', at + 46, at + 46 + nameLen);
    const dataAt = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const data = buf.subarray(dataAt, dataAt + size);
    if (method === 0) files.set(name, data);
    else if (method === 8) files.set(name, inflateRawSync(data));
    else throw new Error(`Okänd komprimering ${method} i ${name}`);
    at += 46 + nameLen + extraLen + commentLen;
  }
  return files;
}

const unescape = (s) =>
  s.replace(/&(lt|gt|quot|apos|amp|#(\d+)|#x([0-9a-f]+));/gi, (_, name, dec, hex) =>
    dec ? String.fromCodePoint(Number(dec)) : hex ? String.fromCodePoint(parseInt(hex, 16)) : { lt: '<', gt: '>', quot: '"', apos: "'", amp: '&' }[name]);

/** Texten i alla <t>-element (med eller utan namnrymdsprefix) i ett XML-utsnitt. */
const texts = (xml) => [...xml.matchAll(/<(?:\w+:)?t(?:\s[^>]*)?>([\s\S]*?)<\/(?:\w+:)?t>/g)].map((m) => unescape(m[1])).join('');

const column = (ref) => [...ref.replace(/\d+$/, '')].reduce((n, c) => n * 26 + c.charCodeAt(0) - 64, 0) - 1;

/** Första bladets rader som listor av strängar; tomma celler blir ''. */
export function readFirstSheet(buf) {
  const files = unzip(buf);
  const shared = files.has('xl/sharedStrings.xml')
    ? [...files.get('xl/sharedStrings.xml').toString('utf8').matchAll(/<(?:\w+:)?si>([\s\S]*?)<\/(?:\w+:)?si>/g)].map((m) => texts(m[1]))
    : [];
  const sheet = [...files.keys()].filter((n) => /^xl\/worksheets\/sheet\d+\.xml$/.test(n)).sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))[0];
  if (!sheet) throw new Error('Inget blad i arbetsboken');
  const xml = files.get(sheet).toString('utf8');
  return [...xml.matchAll(/<(?:\w+:)?row\b[^>]*>([\s\S]*?)<\/(?:\w+:)?row>/g)].map(([, row]) => {
    const cells = [];
    for (const [, attrs, body = ''] of row.matchAll(/<(?:\w+:)?c\b([^>]*?)(?:\/>|>([\s\S]*?)<\/(?:\w+:)?c>)/g)) {
      const ref = /\br="([A-Z]+\d+)"/.exec(attrs)?.[1];
      const type = /\bt="(\w+)"/.exec(attrs)?.[1];
      const v = /<(?:\w+:)?v>([\s\S]*?)<\/(?:\w+:)?v>/.exec(body)?.[1];
      const value = type === 's' ? shared[Number(v)] ?? '' : type === 'inlineStr' ? texts(body) : v === undefined ? '' : unescape(v);
      cells[ref ? column(ref) : cells.length] = value;
    }
    return Array.from(cells, (c) => c ?? '');
  });
}
