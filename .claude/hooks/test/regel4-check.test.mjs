// Test för regel4-check-hooken. Körs med: node --test '.claude/hooks/test/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, copyFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { scopesFor, parsePatterns, parseSections, parseAllowlist, findViolations, findRepoRoot } from '../regel4-check.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '../../..');
const hook = path.join(repoRoot, '.claude/hooks/regel4-check.mjs');
const patternsRel = 'app/src/test/resources/ui-forbidden.txt';
const allowRel = 'app/src/test/resources/ui-allowlist.txt';
const examplesRel = 'app/src/test/resources/ui-forbidden-examples.txt';
const pkg = 'app/src/main/kotlin/se/partee71/dagboken';

const readPatterns = () => readFileSync(path.join(repoRoot, patternsRel), 'utf8');

/** Kortkomponenten som mönsterfilen anvisar i stället för `Card(` – hooken ska tipsa om den. */
const cardReplacement = () => parsePatterns(readPatterns()).feature.find((r) => r.regex.test('Card(')).replacement;

/** Tillfälligt repo (i en egen föräldrakatalog) med de riktiga mönster- och undantagsfilerna. */
function makeProject(allowlist) {
  const parent = mkdtempSync(path.join(tmpdir(), 'regel4-'));
  const dir = path.join(parent, 'Dagboken');
  mkdirSync(path.join(dir, 'app/src/test/resources'), { recursive: true });
  copyFileSync(path.join(repoRoot, patternsRel), path.join(dir, patternsRel));
  if (allowlist !== undefined) writeFileSync(path.join(dir, allowRel), allowlist);
  else copyFileSync(path.join(repoRoot, allowRel), path.join(dir, allowRel));
  return { dir, parent };
}

function writeKt(dir, rel, content) {
  const abs = path.join(dir, rel);
  mkdirSync(path.dirname(abs), { recursive: true });
  writeFileSync(abs, content);
  return abs;
}

function runHook(projectDir, filePath, tool = 'Write') {
  return spawnSync('node', [hook], {
    input: JSON.stringify({ tool_name: tool, tool_input: { file_path: filePath }, cwd: projectDir }),
    env: { ...process.env, CLAUDE_PROJECT_DIR: projectDir },
    encoding: 'utf8',
  });
}

const featureCard = `package x\n\n@Composable\nfun DoseRow() {\n    Card(modifier = Modifier) { Text("Levaxin") }\n}\n`;
const featureFileRel = `${pkg}/ui/today/DoseRow.kt`;

test('stoppar Card( i feature-kod med exit 2 och tipsar om den delade kortkomponenten', () => {
  const { dir } = makeProject();
  const r = runHook(dir, writeKt(dir, featureFileRel, featureCard));
  assert.equal(r.status, 2);
  assert.match(r.stderr, /DoseRow\.kt:5/);
  assert.ok(cardReplacement(), 'ui-forbidden.txt har en ersättning för Card(');
  assert.ok(r.stderr.includes(cardReplacement()), r.stderr);
});

test('fungerar när sessionens projektrot är en föräldrakatalog med flera repon', () => {
  const { dir, parent } = makeProject();
  const r = runHook(parent, writeKt(dir, featureFileRel, featureCard));
  assert.equal(r.status, 2, 'hooken måste hitta repot från filen, inte från projektroten');
  assert.match(r.stderr, new RegExp(`${pkg}/ui/today/DoseRow\\.kt:5`));
});

test('släpper igenom samma rad i ui/components', () => {
  const { dir } = makeProject();
  const r = runHook(dir, writeKt(dir, `${pkg}/ui/components/AppCard.kt`, featureCard));
  assert.equal(r.status, 0, r.stderr);
  assert.equal(r.stderr, '');
});

/** Delade exempel – samma fil körs av UiConsistencyTest, så hook och bygge tolkar lika. */
const readExamples = () => parseSections(readFileSync(path.join(repoRoot, examplesRel), 'utf8'));

// Feature-filen som exempelfilens [allowlist]-rader gäller (samma som UiConsistencyTest) –
// läses ur exempelfilen, så att hook och bygge alltid testar mot samma sökväg.
const featureFile = parseAllowlist(readExamples().allowlist[0].split('\t')[0])[0].file;
const componentFile = `${pkg}/ui/components/X.kt`;

test('delade exempel: tillåtna rader släpps i feature-kod', () => {
  const scopes = parsePatterns(readPatterns());
  const ok = readExamples().ok;
  assert.ok(ok.length >= 20);
  for (const line of ok) assert.deepEqual(findViolations(featureFile, line, scopes, []), [], line);
});

test('delade exempel: hårdkodat utseende, råa M3-komponenter, egna ramelement och Expressive-opt-in stoppas', () => {
  const scopes = parsePatterns(readPatterns());
  const bad = readExamples().stopp;
  assert.ok(bad.length >= 20);
  for (const line of bad) assert.ok(findViolations(featureFile, line, scopes, []).length > 0, line);
});

test('delade exempel: delade komponenter får använda M3 och Expressive', () => {
  const scopes = parsePatterns(readPatterns());
  for (const line of readExamples()['komponent-ok']) {
    assert.deepEqual(findViolations(componentFile, line, scopes, []), [], line);
  }
});

test('delade exempel: ett undantag gäller bara sin fil och sin symbol', () => {
  const scopes = parsePatterns(readPatterns());
  for (const example of readExamples().allowlist) {
    const [allowLine, code, expected] = example.split('\t');
    const hits = findViolations(featureFile, code, scopes, parseAllowlist(allowLine));
    assert.equal(hits.length === 0 ? 'ok' : 'stopp', expected, example);
  }
});

test('delade exempel: allowlist-rad utan symbol eller motivering avvisas', () => {
  for (const line of readExamples()['allowlist-fel']) {
    assert.throws(() => parseAllowlist(line), /ui-allowlist\.txt rad 1/, line);
  }
});

test('Firestore-typer stoppas överallt utom i data/firestore – även i di/ och ui/', () => {
  const { dir } = makeProject();
  const src = 'class X(val db: FirebaseFirestore)\n';
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/data/repository/DoseRepository.kt`, src), 'Edit').status, 2);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/di/FirestoreModule.kt`, src), 'Edit').status, 2);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/ui/components/Bad.kt`, src), 'Edit').status, 2);
  assert.equal(runHook(dir, writeKt(dir, `${pkg}/data/firestore/FirestoreModule.kt`, src), 'Edit').status, 0);
});

test('allowlist-rad släpper igenom just den filen och symbolen via hooken', () => {
  const { dir } = makeProject(`# test\n${featureFileRel}:Card( – test\n`);
  assert.equal(runHook(dir, writeKt(dir, featureFileRel, featureCard)).status, 0);
});

test('trasig allowlist stoppar hooken med radnummer', () => {
  const { dir } = makeProject(`# test\n${featureFileRel} – saknar symbol\n`);
  const r = runHook(dir, writeKt(dir, featureFileRel, featureCard));
  assert.equal(r.status, 2);
  assert.match(r.stderr, /ui-allowlist\.txt rad 2/);
});

test('delade exempel: scope per sökväg', () => {
  const cases = readExamples().scope;
  assert.ok(cases.length >= 10);
  for (const line of cases) {
    const [file, expected = ''] = line.split('\t');
    assert.deepEqual(scopesFor(file), expected ? expected.split(',') : [], file);
  }
});

test('filer utanför repo, utan mönsterfil eller av annan typ ignoreras', () => {
  const { dir, parent } = makeProject();
  assert.equal(runHook(dir, writeKt(dir, 'docs/Card.kt', featureCard)).status, 0);
  const outside = writeKt(parent, `annat-repo/${pkg}/ui/today/X.kt`, featureCard);
  assert.equal(findRepoRoot(outside), null);
  assert.equal(runHook(parent, outside).status, 0);
});

test('tom eller trasig indata ger exit 0', () => {
  assert.equal(spawnSync('node', [hook], { input: 'inte json', encoding: 'utf8' }).status, 0);
});

test('mönsterfilen har alla scope, giltiga regexar och en ersättning per rad', () => {
  const scopes = parsePatterns(readPatterns());
  assert.deepEqual(Object.keys(scopes).sort(), ['expressive', 'feature', 'firestore']);
  assert.ok(scopes.feature.length >= 30);
  assert.ok(scopes.firestore.length >= 3);
  for (const rule of Object.values(scopes).flat()) assert.ok(rule.replacement, rule.source);
});

test('hook-kommandona i settings.json hittar skripten både i repot och från en föräldrakatalog', () => {
  const settings = JSON.parse(readFileSync(path.join(repoRoot, '.claude/settings.json'), 'utf8'));
  const postCmd = settings.hooks.PostToolUse[0].hooks[0].command;
  const startCmd = settings.hooks.SessionStart[0].hooks[0].command;

  const { dir, parent } = makeProject();
  mkdirSync(path.join(dir, '.claude/hooks'), { recursive: true });
  for (const f of ['regel4-check.mjs', 'session-start.sh']) {
    copyFileSync(path.join(repoRoot, '.claude/hooks', f), path.join(dir, '.claude/hooks', f));
  }
  const file = writeKt(dir, featureFileRel, featureCard);
  const input = JSON.stringify({ tool_name: 'Write', tool_input: { file_path: file } });

  for (const projectDir of [dir, parent]) {
    const post = spawnSync('bash', ['-c', postCmd], {
      input, env: { ...process.env, CLAUDE_PROJECT_DIR: projectDir }, encoding: 'utf8',
    });
    assert.equal(post.status, 2, `PostToolUse med projektrot ${projectDir}: ${post.stderr}`);

    const start = spawnSync('bash', ['-c', startCmd], {
      env: { ...process.env, CLAUDE_PROJECT_DIR: projectDir, CLAUDE_CODE_REMOTE: 'true', FIREBASE_SERVICE_ACCOUNT: '' },
      encoding: 'utf8',
    });
    assert.equal(start.status, 0, start.stderr);
    assert.match(start.stdout, /FIREBASE_SERVICE_ACCOUNT: saknas/);
    assert.doesNotMatch(start.stdout, /eyJ|BEGIN PRIVATE KEY/, 'nyckelinnehåll får aldrig skrivas ut');
  }

  const none = spawnSync('bash', ['-c', postCmd], {
    input, env: { ...process.env, CLAUDE_PROJECT_DIR: mkdtempSync(path.join(tmpdir(), 'tom-')) }, encoding: 'utf8',
  });
  assert.equal(none.status, 0, 'utan hook i projektet ska kommandot vara tyst');
});
