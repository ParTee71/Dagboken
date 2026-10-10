// Repots filer för tester som jämför tools/db med appen – ingen emulator behövs.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');

/** En fil i repot, relativt repots rot. */
export const readRepoFile = (rel) => readFileSync(path.join(repoRoot, rel), 'utf8');

const SCHEMA = 'core/src/main/kotlin/se/partee71/dagboken/core/schema';

/** Heltalskonstanten `const val [name] = n` i Kotlin-filen [file] under :core/schema. */
const kotlinConstant = (file, name) => Number(readRepoFile(`${SCHEMA}/${file}`).match(new RegExp(`const val ${name} = (\\d+)`))[1]);

/** Textgränserna i :core (`TextLimits.SHORT`/`LONG`) – samma tak som rules (schema.test.mjs). */
export const textLimits = () => ({ short: kotlinConstant('TextLimits.kt', 'SHORT'), long: kotlinConstant('TextLimits.kt', 'LONG') });

/** Ett heltal ur `DocumentRules` i :core (`MAX_BOOSTS` …) – konverterarens gränser, samma som rules. */
export const documentRulesConstant = (name) => kotlinConstant('DocumentRules.kt', name);
