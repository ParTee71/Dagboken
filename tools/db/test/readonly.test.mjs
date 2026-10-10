// Läsverktygen (skill db-access) skriver aldrig – kontrollerat i källkoden, så att en framtida
// ändring fångas i CI. Ingen emulator behövs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readRepoFile } from './helpers/repo.mjs';
import { runScript } from './helpers/run.mjs';

const READ_ONLY = ['query.mjs', 'get.mjs', 'stats.mjs', 'compare.mjs', 'lib/query.mjs', 'lib/compare.mjs'];

test('query, get, stats och compare innehåller inga skrivande anrop', () => {
  for (const file of READ_ONLY) {
    const source = readRepoFile(`tools/db/${file}`);
    for (const call of ['.set(', '.update(', '.delete(', '.create(', '.batch(', 'recursiveDelete', 'importData', 'migrateUsers', 'write: true']) {
      assert.ok(!source.includes(call), `${file} innehåller ${call}`);
    }
  }
});

test('utan nyckel: svenskt fel som hänvisar till README, exitkod 1 och inget nätverksanrop', () => {
  const result = runScript('stats.mjs', [], { env: { PATH: process.env.PATH } });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /^Fel: FIREBASE_SERVICE_ACCOUNT saknas.*README → Utveckling via Claude/);
  assert.match(result.stderr, /Klistra aldrig in nyckeln i chatten/);
});
