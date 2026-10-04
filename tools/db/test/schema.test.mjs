// Schemaversionen finns på två ställen: Schema.kt i appen och lib/schema.mjs här. De ska vara lika.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CURRENT_VERSION, FIRST_VERSION, versionOf } from '../lib/schema.mjs';
import { STEPS } from '../lib/migrate.mjs';
import { readRepoFile } from './helpers/repo.mjs';

const schemaDir = 'core/src/main/kotlin/se/partee71/dagboken/core/schema';
const schemaKt = readRepoFile(`${schemaDir}/Schema.kt`);
const migratorKt = readRepoFile(`${schemaDir}/SchemaMigrator.kt`);
const constant = (name) => Number(schemaKt.match(new RegExp(`const val ${name} = (\\d+)`))[1]);

test('CURRENT_VERSION och FIRST_VERSION är desamma som i Schema.kt och rules', () => {
  assert.equal(CURRENT_VERSION, 1, '4.0 börjar på version 1 (BCK-15)');
  assert.equal(CURRENT_VERSION, constant('CURRENT_VERSION'));
  assert.equal(FIRST_VERSION, constant('FIRST_VERSION'));
  const rules = readRepoFile('firestore.rules');
  const max = Number(rules.match(/function maxSchemaVersion\(\) \{\s*return (\d+);/)[1]);
  assert.equal(max, CURRENT_VERSION, 'maxSchemaVersion() i firestore.rules');
});

test('migreringsstegen speglar SchemaMigrator', () => {
  // Stegen i SchemaMigrator skrivs `<från> to Step { … }`.
  const kotlinSteps = [...migratorKt.matchAll(/^\s*(\d+) to Step\b/gm)].map((m) => m[1]);
  assert.deepEqual(Object.keys(STEPS), kotlinSteps);
  for (let v = FIRST_VERSION; v < CURRENT_VERSION; v++) assert.ok(STEPS[v], `steg saknas för version ${v}`);
});

test('versionOf: saknad eller trasig version (under den första) är den första, som Schema.versionOf', () => {
  assert.equal(versionOf({}), FIRST_VERSION);
  assert.equal(versionOf({ schemaVersion: 0 }), FIRST_VERSION);
  assert.equal(versionOf({ schemaVersion: -2 }), FIRST_VERSION);
  assert.equal(versionOf({ schemaVersion: '1' }), FIRST_VERSION);
  assert.equal(versionOf({ schemaVersion: CURRENT_VERSION + 1 }), CURRENT_VERSION + 1);
  assert.ok(schemaKt.includes('coerceAtLeast(FIRST_VERSION)'), 'Schema.versionOf ska ha samma golv');
});

test('textgränserna i rules är desamma som TextLimits i :core', () => {
  const limitsKt = readRepoFile(`${schemaDir}/TextLimits.kt`);
  const kotlin = (name) => Number(limitsKt.match(new RegExp(`const val ${name} = (\\d+)`))[1]);
  const rules = readRepoFile('firestore.rules');
  const rule = (name) => Number(rules.match(new RegExp(`function ${name}\\(\\) \\{ return (\\d+); \\}`))[1]);
  assert.equal(rule('maxShort'), kotlin('SHORT'));
  assert.equal(rule('maxLong'), kotlin('LONG'));
});
