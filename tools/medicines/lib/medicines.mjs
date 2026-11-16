// Läkemedelsverkets lista över läkemedel → appens bantade fil (REC-14). Bara humanläkemedel som
// finns till försäljning; namn, styrka och form, utan dubbletter, i svensk ordning.

export const SOURCE_PAGE = 'https://www.lakemedelsverket.se/sv/om-webbplatsen/oppna-data';
export const LICENSE = 'Läkemedelsverket, CC BY 4.0';

const COLUMNS = { name: 'Namn', strength: 'Styrka', form: 'Form', species: 'H/V', sale: 'Försäljningsstatus' };

/** Adressen till dagens Excel-fil, hittad på sidan med öppna data. */
export function findListUrl(html) {
  const path = /\/globalassets\/excel\/Lakemedelsprodukter-\d{4}-\d{2}-\d{2}\.xlsx/.exec(html)?.[0];
  return path ? new URL(path, SOURCE_PAGE).href : null;
}

/** Adressen filen ligger på för ett visst datum – den Läkemedelsverket länkade till fram till 2026-10. */
export const listUrlFor = (date) => new URL(`/globalassets/excel/Lakemedelsprodukter-${date}.xlsx`, SOURCE_PAGE).href;

/** Adresserna att pröva när sidan saknar länk: dagens datum i svensk tid först, sedan bakåt. */
export function candidateUrls(now, days = 7) {
  const [y, m, d] = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Europe/Stockholm' }).format(now).split('-').map(Number);
  return Array.from({ length: days }, (_, i) =>
    listUrlFor(new Date(Date.UTC(y, m - 1, d - i)).toISOString().slice(0, 10)));
}

/**
 * Hämtar den senaste Excel-filen. Länken på sidan går först; sedan prövas adressen dag för dag bakåt,
 * eftersom bara den senaste filen ligger kvar. En hämtning i taget.
 */
export async function download(fetchFn = fetch, now = new Date()) {
  const page = await fetchFn(SOURCE_PAGE);
  if (!page.ok) throw new Error(`${SOURCE_PAGE}: HTTP ${page.status}`);
  const linked = findListUrl(await page.text());
  const urls = linked ? [linked] : candidateUrls(now);
  for (const url of urls) {
    const file = await fetchFn(url);
    if (file.ok) return { name: url, buf: Buffer.from(await file.arrayBuffer()) };
    if (file.status !== 404 || linked) throw new Error(`${url}: HTTP ${file.status}`);
  }
  throw new Error(`Hittade ingen lista Lakemedelsprodukter-ÅÅÅÅ-MM-DD.xlsx – ingen länk på ${SOURCE_PAGE}, och 404 på:\n${urls.join('\n')}`);
}

/** Datumet i bladets namn eller filnamnet, "2026-10-04". */
export const dateOf = (text) => /\d{4}-\d{2}-\d{2}/.exec(text ?? '')?.[0] ?? null;

const clean = (s) => String(s ?? '').replace(/[\t\r\n]+/g, ' ').replace(/\s+/g, ' ').trim();

/** Raderna (första raden rubriker) som unika läkemedel, sorterade. Okända rubriker är ett fel. */
export function select(rows) {
  const [header, ...body] = rows;
  const ix = Object.fromEntries(Object.entries(COLUMNS).map(([key, title]) => {
    const i = header.indexOf(title);
    if (i < 0) throw new Error(`Kolumnen "${title}" saknas – har Läkemedelsverket ändrat filen?`);
    return [key, i];
  }));
  const seen = new Map();
  for (const row of body) {
    if (row[ix.species] !== 'HUM' || row[ix.sale] !== 'Finns till försäljning') continue;
    const entry = { name: clean(row[ix.name]), strength: clean(row[ix.strength]), form: clean(row[ix.form]) };
    if (!entry.name || !entry.form) continue;
    seen.set(`${entry.name}\t${entry.strength}\t${entry.form}`.toLowerCase(), entry);
  }
  const order = new Intl.Collator('sv', { numeric: true, sensitivity: 'base' });
  return [...seen.values()].sort((a, b) =>
    order.compare(a.name, b.name) || order.compare(a.strength, b.strength) || order.compare(a.form, b.form));
}

/** Filen appen läser (`MedicineCatalog.parse`): kommentarer, sedan `namn\tstyrka\tform` per rad. */
export function format(entries, date) {
  return [
    `# Läkemedel som säljs i Sverige – ${LICENSE} (${SOURCE_PAGE})`,
    `# updated ${date}`,
    ...entries.map((e) => `${e.name}\t${e.strength}\t${e.form}`),
  ].join('\n') + '\n';
}
