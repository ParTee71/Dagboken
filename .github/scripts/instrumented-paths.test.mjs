// Körs med: node --test '.github/scripts/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { instrumentedPathspecs } from './instrumented-paths.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

test('filtret instrumented blir git-pathspecs, och android.yml har det', () => {
  const yaml = `
          filters: |
            app:
              - 'app/**'
            instrumented:
              - 'app/src/**'
              - 'firestore.rules'
`;
  assert.deepEqual(instrumentedPathspecs(yaml), [':(glob)app/src/**', ':(glob)firestore.rules']);
  assert.throws(() => instrumentedPathspecs("filters: |\n  app:\n    - 'app/**'\n"), /instrumented saknas/);
  const real = instrumentedPathspecs(readFileSync(path.join(repoRoot, '.github/workflows/android.yml'), 'utf8'));
  assert.ok(real.includes(':(glob)app/src/**'));
});
