// Repots filer för tester som jämför tools/db med appen – ingen emulator behövs.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');

/** En fil i repot, relativt repots rot. */
export const readRepoFile = (rel) => readFileSync(path.join(repoRoot, rel), 'utf8');

/** Textgränserna i :core (`TextLimits.SHORT`/`LONG`) – samma tak som rules (schema.test.mjs). */
export const textLimits = () => {
  const kotlin = readRepoFile('core/src/main/kotlin/se/partee71/dagboken/core/schema/TextLimits.kt');
  const value = (name) => Number(kotlin.match(new RegExp(`const val ${name} = (\\d+)`))[1]);
  return { short: value('SHORT'), long: value('LONG') };
};
