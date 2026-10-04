#!/usr/bin/env node
// Sökvägarna som startar instrumenttesterna – filtret `instrumented` i android.yml, en källa för
// både PR-flödet och release.yml, som hoppar över testerna när koden redan testats grönt i en PR.
// Skriver git-pathspecs (`:(glob)…`), en per rad. Kör: node .github/scripts/instrumented-paths.mjs

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { WORKFLOW, parseWorkflowFilters } from './check-ci-table.mjs';

/** Filtret `instrumented` som git-pathspecs. */
export function instrumentedPathspecs(yaml) {
  const paths = parseWorkflowFilters(yaml).instrumented;
  if (!paths?.length) throw new Error(`${WORKFLOW}: filtret instrumented saknas`);
  return paths.map((p) => `:(glob)${p}`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  for (const spec of instrumentedPathspecs(readFileSync(WORKFLOW, 'utf8'))) console.log(spec);
}
