// Körs med: node --test '.github/scripts/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PLUGIN_AGENTS, PLUGIN_SKILLS, check, mentions, parsePluginSkills, relativeLinks, tableNames } from './check-links.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

/** Minimalt repo med en skill, en agent och .claude/README.md som listar dem. */
function makeRepo(extra = {}) {
  const dir = mkdtempSync(path.join(tmpdir(), 'links-'));
  const files = {
    'CLAUDE.md': 'Översikt: [.claude/README.md](.claude/README.md).',
    '.claude/README.md': '## Agenter\n| Agent | När |\n|---|---|\n| `granskare` | x |\n\n## Skills\n| Skill | När |\n|---|---|\n| `ci-budget` | x |\n',
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

test('översiktstabellerna måste lista exakt de skills och agenter som finns', () => {
  const dir = makeRepo({ '.claude/skills/ny-skill/SKILL.md': 'x', '.claude/agents/ny.md': 'x' });
  assert.deepEqual(check(dir).sort(), [
    '.claude/README.md: agenten `ny` saknas i tabellen "Agenter"',
    '.claude/README.md: skill `ny-skill` saknas i tabellen "Skills"',
  ]);
  assert.deepEqual([...tableNames('## Skills\n| `a` · `b` | x |', 'Skills')], ['a', 'b']);
});

test('saknas översikten fälls varje skill och agent', () => {
  const dir = makeRepo();
  rmSync(path.join(dir, '.claude/README.md'));
  assert.deepEqual(check(dir).sort(), [
    '.claude/README.md: agenten `granskare` saknas i tabellen "Agenter"',
    '.claude/README.md: skill `ci-budget` saknas i tabellen "Skills"',
    'CLAUDE.md: länken .claude/README.md pekar på något som inte finns',
  ]);
});

test('framåtreferens i allowlistan godtas', () => {
  const dir = makeRepo({
    'ARKITEKTUR.md': 'Se [plan](PLAN.md).',
    '.github/scripts/link-allowlist.txt': '# x\nARKITEKTUR.md:PLAN.md – #99\n',
  });
  assert.deepEqual(check(dir), []);
});

test('pluginskills med prefixet android: känns igen och kontrolleras mot listan i repot', () => {
  const { skills } = mentions('Skill **android:mockup** och skill `android:release`. → Skills: **android:refine-issue**, **ui-style**.');
  assert.deepEqual([...skills].sort(), ['android:mockup', 'android:refine-issue', 'android:release', 'ui-style'].sort());
  const dir = makeRepo({
    '.github/scripts/plugin-skills.txt': '# kommentar\nmockup\nrelease\n',
    'ARKITEKTUR.md': 'Se skill **android:mockup**, skill `android:release` och skill `android:finns-inte`.',
  });
  assert.deepEqual(check(dir), ['ARKITEKTUR.md: skill `android:finns-inte` finns inte i .github/scripts/plugin-skills.txt']);
});

test('utan listan godtas ingen pluginskill, och en repo-skill räknas inte som pluginens', () => {
  const dir = makeRepo({ 'ARKITEKTUR.md': 'Skill `android:mockup` och skill `android:ci-budget`.' });
  assert.equal(check(dir).length, 2);
});

// Pluginen klonas inte i CI – där hoppas testet över; lokalt hålls listan i takt med klonen.
const pluginDir = '/home/user/claude-android/plugins/android/skills';
test('plugin-skills.txt listar exakt pluginens skills', { skip: !existsSync(pluginDir) && 'pluginen är inte klonad' }, () => {
  const listed = [...parsePluginSkills(readFileSync(path.join(repoRoot, PLUGIN_SKILLS), 'utf8'))].sort();
  const actual = readdirSync(pluginDir).filter((n) => existsSync(path.join(pluginDir, n, 'SKILL.md'))).sort();
  assert.deepEqual(listed, actual);
});

test('pluginagenter med prefixet android: kontrolleras mot plugin-agents.txt', () => {
  const dir = makeRepo({
    '.github/scripts/plugin-agents.txt': '# kommentar\ngranskare\n',
    'ARKITEKTUR.md': 'Kör agenten `android:granskare` och agenten **android:finns-inte**.',
  });
  assert.deepEqual(check(dir), ['ARKITEKTUR.md: agenten `android:finns-inte` finns inte i .github/scripts/plugin-agents.txt']);
});

const pluginAgentsDir = '/home/user/claude-android/plugins/android/agents';
test('plugin-agents.txt listar exakt pluginens agenter', { skip: !existsSync(pluginAgentsDir) && 'pluginen är inte klonad' }, () => {
  const listed = [...parsePluginSkills(readFileSync(path.join(repoRoot, PLUGIN_AGENTS), 'utf8'))].sort();
  const actual = readdirSync(pluginAgentsDir).filter((f) => f.endsWith('.md')).map((f) => f.slice(0, -3)).sort();
  assert.deepEqual(listed, actual);
});
