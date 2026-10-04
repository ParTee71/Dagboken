#!/usr/bin/env node
// Migrerar användarnas data till en ny schemaVersion (BCK-15, skill data-safety-backup). Speglar
// SchemaMigrator i :core. Skriver bara dokument som stegen ändrar, och stämplar användaren sist.
import { openFirestore } from './lib/admin.mjs';
import { printSummary, runCli } from './lib/cli.mjs';
import { migrateUsers } from './lib/migrate.mjs';

await runCli({
  usage: `Användning: node tools/db/migrate.mjs --to <N> [--user <uid>] [--dry-run]

Lyfter varje användare under version N steg för steg och stämplar den sedan med N.
Kör när appen inte används – ett dokument som ett steg ändrar kan annars skriva över en
ändring som gjorts under körningen. Oförändrade dokument skrivs inte.`,
  options: { to: { type: 'string' }, user: { type: 'string' }, 'dry-run': { type: 'boolean' } },
  main: async ({ to, user, 'dry-run': dryRun }) => {
    if (!to) throw new Error('--to saknas');
    const { fel, ...counts } = await migrateUsers(openFirestore({ write: !dryRun }), Number(to), { user, dryRun });
    printSummary(dryRun ? 'Torrkörning – skulle migrera:' : 'Migrerade:', counts);
    if (fel) {
      console.error('Kunde inte migreras:');
      for (const line of fel) console.error(`  ${line}`);
      process.exitCode = 1;
    }
  },
});
