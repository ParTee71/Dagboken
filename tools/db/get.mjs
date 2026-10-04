#!/usr/bin/env node
// Visar ett dokument som JSON (skill db-access). Läser bara.
import { openFirestore } from './lib/admin.mjs';
import { runCli } from './lib/cli.mjs';
import { getDocument } from './lib/query.mjs';

await runCli({
  usage: `Användning: node tools/db/get.mjs <sökväg> [--user <uid>]

<sökväg> är full (users/<uid>/doses/<id>) eller relativ användaren (doses/<id>).`,
  positionals: true,
  options: { user: { type: 'string' } },
  main: async ({ user }, [docPath]) => {
    if (!docPath) throw new Error('<sökväg> saknas');
    console.log(JSON.stringify(await getDocument(openFirestore(), docPath, { user }), null, 2));
  },
});
