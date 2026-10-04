#!/usr/bin/env node
// Hälsokoll: antal dokument per samling och schemaVersion per användare (skill db-access). Läser bara.
import { openFirestore } from './lib/admin.mjs';
import { printSummary, runCli } from './lib/cli.mjs';
import { userStats } from './lib/query.mjs';

await runCli({
  usage: `Användning: node tools/db/stats.mjs [--user <uid>]

Visar per användare: schemaVersion och antal dokument per samling. Inget innehåll.`,
  options: { user: { type: 'string' } },
  main: async ({ user }) => {
    for (const { user: id, schemaVersion, counts } of await userStats(openFirestore(), { user })) {
      printSummary(`Användare ${id} (schemaVersion ${schemaVersion ?? 'saknar användardokument'}):`, counts);
    }
  },
});
