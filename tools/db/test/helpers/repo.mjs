// Repots filer för tester som jämför tools/db med appen – ingen emulator behövs.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');

/** En fil i repot, relativt repots rot. */
export const readRepoFile = (rel) => readFileSync(path.join(repoRoot, rel), 'utf8');
