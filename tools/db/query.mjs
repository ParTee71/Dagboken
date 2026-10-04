#!/usr/bin/env node
// Listar dokument i en samling (skill db-access). Läser bara.
import { openFirestore } from './lib/admin.mjs';
import { printTable, runCli } from './lib/cli.mjs';
import { parseWhere, queryCollection } from './lib/query.mjs';

await runCli({
  usage: `Användning: node tools/db/query.mjs <samling> [--user <uid>] [--where fält=värde]
       [--limit 50] [--fields a,b] [--json]

<samling> tolkas relativt användaren: settings, options, prescriptions, prnMedicines, doses,
screenings, activities, events, illnessEpisodes, illnessEpisodes/<eid>/checkins. "users"
listar alla användare nyckeln ser. Finns bara en användare väljs den automatiskt.
--where kan anges flera gånger (likhet).`,
  positionals: true,
  options: {
    user: { type: 'string' },
    where: { type: 'string', multiple: true },
    limit: { type: 'string', default: '50' },
    fields: { type: 'string' },
    json: { type: 'boolean' },
  },
  main: async ({ user, where = [], limit, fields, json }, [collection]) => {
    if (!collection) throw new Error('<samling> saknas');
    const rows = await queryCollection(openFirestore(), collection, {
      user,
      where: where.map(parseWhere),
      limit: Number(limit),
      fields: fields?.split(',').map((f) => f.trim()).filter(Boolean),
    });
    if (json) console.log(JSON.stringify(rows, null, 2));
    else printTable(rows);
  },
});
