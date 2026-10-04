#!/usr/bin/env node
// Skriver en service-account-nyckel från miljövariabeln --env till --out, så att Firebase-verktyget
// kan läsa den (GOOGLE_APPLICATION_CREDENTIALS). Används av .github/actions/deploy-rules.
// Stoppar om nyckeln hör till ett annat projekt än --project. Värdet skrivs aldrig ut.
import { writeFileSync } from 'node:fs';
import { serviceAccountFor } from './lib/credentials.mjs';
import { runCli } from './lib/cli.mjs';

await runCli({
  usage: `Användning: node tools/db/write-credentials.mjs --env <VARIABEL> --project <projekt-ID> --out <fil>`,
  options: { env: { type: 'string' }, project: { type: 'string' }, out: { type: 'string' } },
  main: async ({ env, project, out }) => {
    if (!env || !project || !out) throw new Error('--env, --project och --out krävs');
    const account = serviceAccountFor(process.env[env], env, project);
    writeFileSync(out, JSON.stringify(account), { mode: 0o600 });
    console.log(`Nyckeln för ${project} är skriven.`);
  },
});
