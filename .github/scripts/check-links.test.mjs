// Körs med: node --test '.github/scripts/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { check, mentions, relativeLinks, tableNames } from './check-links.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

/** Minimalt repo med en skill, en agent och CLAUDE.md som listar dem. */
function makeRepo(extra = {}) {
  const dir = mkdtempSync(path.join(tmpdir(), 'links-'));
  const files = {
    'CLAUDE.md': '## Agenter\n| Agent | När |\n|---|---|\n| `granskare` | x |\n\n## Skills\n| Skill | När |\n|---|---|\n| `ci-budget` | x |\n',
    '.claude/skills/ci-budget/SKILL.md': 'Se [README](../../../README.md).',
    '.claude/agents/granskare.md': 'Agent.',
    'README.md': 'Läs [CLAUDE.md](CLAUDE.md) och skill `ci-budget`.',
    ...extra,
  };
  for (const [rel, content] of Object.entries(files)) {
    mkdirSync(path.dirname(path.join(dir, rel)), { recursive: true });
    writeFileSync(path.join(dir, rel), content);
  }
  return dir;
}

test('repots dokumentation har inga trasiga länkar eller namn', () => {
  assert.deepEqual(check(repoRoot), []);
});

test('ett korrekt minirepo ger inga fel', () => {
  assert.deepEqual(check(makeRepo()), []);
});

test('trasig relativ länk hittas, men inte webbadresser, ankare eller kodblock', () => {
  assert.deepEqual(relativeLinks('[a](x.md#rubrik) [b](https://x.se) [c](#ankare)\n```\n[d](kod.md)\n```'), ['x.md']);
  const problems = check(makeRepo({ 'ARKITEKTUR.md': 'Se [plan](PLAN.md).' }));
  assert.deepEqual(problems, ['ARKITEKTUR.md: länken PLAN.md pekar på något som inte finns']);
});

test('nämnd skill eller agent som inte finns hittas', () => {
  const problems = check(makeRepo({ 'ARKITEKTUR.md': 'Se skill `finns-inte` och agenten `spoke`.' }));
  assert.equal(problems.length, 2);
});

test('skill- och agentnamn känns igen i projektets skrivsätt', () => {
  const { skills, agents } = mentions('→ Skill: **data-safety-backup**. → Skills: **a-b**, **c-d**, **e-f**. (skill `ui-style`). Kör agenten **granskare**.');
  assert.deepEqual([...skills].sort(), ['a-b', 'c-d', 'data-safety-backup', 'e-f', 'ui-style']);
  assert.deepEqual([...agents], ['granskare']);
});

test('CLAUDE.md-tabellerna måste lista exakt de skills och agenter som finns', () => {
  const dir = makeRepo({ '.claude/skills/ny-skill/SKILL.md': 'x', '.claude/agents/ny.md': 'x' });
  assert.deepEqual(check(dir).sort(), [
    'CLAUDE.md: agenten `ny` saknas i tabellen "Agenter"',
    'CLAUDE.md: skill `ny-skill` saknas i tabellen "Skills"',
  ]);
  assert.deepEqual([...tableNames('## Skills\n| `a` · `b` | x |', 'Skills')], ['a', 'b']);
});

test('framåtreferens i allowlistan godtas', () => {
  const dir = makeRepo({
    'ARKITEKTUR.md': 'Se [plan](PLAN.md).',
    '.github/scripts/link-allowlist.txt': '# x\nARKITEKTUR.md:PLAN.md – #99\n',
  });
  assert.deepEqual(check(dir), []);
});
