// Repots filer för tester som jämför tools/db med appen – ingen emulator behövs.
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../../..');

/** En fil i repot, relativt repots rot. */
export const readRepoFile = (rel) => readFileSync(path.join(repoRoot, rel), 'utf8');

const CORE = 'core/src/main/kotlin/se/partee71/dagboken/core';

/** Heltalskonstanten `const val [name] = n` i Kotlin-filen [file] under :core. */
const kotlinConstant = (file, name) => Number(readRepoFile(`${CORE}/${file}`).match(new RegExp(`const val ${name} = (\\d+)`))[1]);

/** Textgränserna i :core (`TextLimits.SHORT`/`LONG`) – samma tak som rules (schema.test.mjs). */
export const textLimits = () => ({ short: kotlinConstant('schema/TextLimits.kt', 'SHORT'), long: kotlinConstant('schema/TextLimits.kt', 'LONG') });

/** Ett heltal ur `DocumentRules` i :core (`MAX_BOOSTS` …) – konverterarens gränser, samma som rules. */
export const documentRulesConstant = (name) => kotlinConstant('schema/DocumentRules.kt', name);

/** Ett heltal ur `FeelingCatalog` i :core (`MAX_FEELINGS`) – `maxFeelings()` i rules (DAT-14). */
export const feelingCatalogConstant = (name) => kotlinConstant('model/Feelings.kt', name);
