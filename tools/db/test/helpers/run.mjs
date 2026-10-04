// Kör ett skript i tools/db som barnprocess – samma sätt som backup.yml och sessionen gör.
// Barnet ärver NODE_TEST_CONTEXT och är därmed i testläge (bara emulatorn).
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const toolsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

/** Timeout, så att ett barn som inte når emulatorn fäller testet i stället för att hänga CI. */
const TIMEOUT_MS = 30_000;

/** [env] ersätter hela miljön, t.ex. för att köra utan nyckel. */
export function runScript(script, args = [], { env } = {}) {
  const result = spawnSync(process.execPath, [path.join(toolsDir, script), ...args], { encoding: 'utf8', timeout: TIMEOUT_MS, env });
  if (result.error) throw result.error;
  return result;
}
