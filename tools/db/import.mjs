#!/usr/bin/env node
// Återställer en export (BCK-12, BCK-16, skill db-access). Skriver – kör alltid --dry-run först.
import { readFileSync } from 'node:fs';
import { openFirestore } from './lib/admin.mjs';
import { importData, parseExport } from './lib/backup.mjs';
import { printSummary, runCli } from './lib/cli.mjs';

await runCli({
  usage: `Användning: node tools/db/import.mjs --in <fil.json> [--user <uid>] [--replace | --update] [--dry-run]

Skriver dokumenten i filen exakt som de är (befintliga dokument med samma sökväg ersätts),
men först när hela filen kontrollerats. --replace tar dessutom bort dokument under filens
användare som inte finns i filen, så att användarens data blir exakt som i backupen.
--update läser en ändringsfil (updates, t.ex. från :core:matchMedicines) och skriver bara
dess fält i dokument som finns – andra fält står kvar, och ett dokument som saknas (raderat
sedan exporten) hoppas över i stället för att skapas.
Vägrar användare med nyare schemaVersion än verktyget. --dry-run visar bara antal per samling.
Kör när appen inte används – importen skriver över dokument (med --update: fälten) som ändrats under tiden.`,
  options: {
    in: { type: 'string' },
    user: { type: 'string' },
    replace: { type: 'boolean' },
    update: { type: 'boolean' },
    'dry-run': { type: 'boolean' },
  },
  main: async ({ in: file, user, replace, update, 'dry-run': dryRun }) => {
    if (!file) throw new Error('--in saknas');
    const data = parseExport(readFileSync(file, 'utf8'));
    // Torrkörning utan --replace/--update behöver ingen databas; med dem läses den bara.
    const db = dryRun && !replace && !update ? null : openFirestore({ write: !dryRun });
    const { written, removed, skipped } = await importData(db, data, { user, dryRun, replace, update });
    printSummary(dryRun ? 'Torrkörning – skulle skriva:' : 'Skrev:', written);
    if (replace) printSummary(dryRun ? 'Skulle ta bort:' : 'Tog bort:', removed);
    if (update) printSummary(dryRun ? 'Skulle hoppa över (saknas):' : 'Hoppade över (saknas):', skipped);
  },
});
