// Gemensam kommandoradstolkning för skripten i tools/db: --help och svenska felmeddelanden.
import { parseArgs } from 'node:util';

/**
 * Tolkar argumenten enligt [options] (node:util parseArgs) och kör [main]. Fel skrivs som
 * en rad utan stackspår och ger exitkod 1 – inga dokument eller nycklar i utskriften.
 */
export async function runCli({ usage, options, positionals = false, main }) {
  let values;
  let args;
  try {
    ({ values, positionals: args } = parseArgs({
      options: { ...options, help: { type: 'boolean', short: 'h' } },
      allowPositionals: positionals,
      strict: true,
    }));
  } catch (error) {
    console.error(`Fel: ${error.message}\n\n${usage}`);
    process.exit(1);
  }
  if (values.help) {
    console.log(usage);
    return;
  }
  try {
    await main(values, args);
  } catch (error) {
    console.error(`Fel: ${error.message}`);
    process.exit(1);
  }
}

/** Skriver antal dokument per samling. */
export function printSummary(title, counts) {
  console.log(title);
  for (const [name, count] of Object.entries(counts)) console.log(`  ${name}: ${count}`);
}

const MAX_CELL = 40;

/** Rader som en enkel tabell: en kolumn per fält, värden som kompakt JSON utom text. */
export function printTable(rows) {
  if (rows.length === 0) {
    console.log('(inga dokument)');
    return;
  }
  const columns = [...new Set(rows.flatMap((r) => Object.keys(r)))];
  const text = (v) => (v === undefined ? '' : typeof v === 'string' ? v : JSON.stringify(v)).replace(/\s*\n\s*/g, ' ⏎ ');
  const widths = columns.map((c) => Math.min(MAX_CELL, Math.max(c.length, ...rows.map((r) => text(r[c]).length))));
  // En avkortad cell slutar med "…", så att ett värde aldrig ser komplett ut när det inte är det.
  const fit = (v, width) => (v.length > width ? `${v.slice(0, width - 1)}…` : v.padEnd(width));
  const line = (values) => values.map((v, i) => fit(String(v), widths[i])).join('  ').trimEnd();
  console.log(line(columns));
  console.log(line(widths.map((w) => '-'.repeat(w))));
  for (const row of rows) console.log(line(columns.map((c) => text(row[c]))));
  console.log(`(${rows.length} dokument)`);
}
