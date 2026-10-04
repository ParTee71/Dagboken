// Samlingslistan i tools/db = Paths.kt i appen = security rules (skill data-safety-backup, BCK-16).
// En samling som saknas här backas aldrig upp. Behöver ingen emulator.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { COLLECTIONS } from '../lib/collections.mjs';
import { readRepoFile as read } from './helpers/repo.mjs';
const pathsKt = read('app/src/main/kotlin/se/partee71/dagboken/data/firestore/Paths.kt');

/** Platshållare ({uid}, $episodeId …) jämförs bara till sin position. */
const normalize = (p) => p.replace(/\$\{?\w+\}?|\{\w+\}/g, '{}');

/** `const val NAMN = "värde"` i Paths.kt. */
const constants = Object.fromEntries([...pathsKt.matchAll(/const val (\w+) = "([^"]+)"/g)].map((m) => [m[1], m[2]]));

/** `fun namn(a: String, …) = <uttryck>` i Paths.kt – uttrycket är en sträng eller ett anrop. */
const functions = Object.fromEntries(
  [...pathsKt.matchAll(/fun (\w+)\(([^)]*)\) = (.+)$/gm)].map((m) => [
    m[1],
    { params: m[2].split(',').map((p) => p.split(':')[0].trim()).filter(Boolean), body: m[3].trim() },
  ]),
);

/**
 * Sökvägen en funktion i Paths.kt ger, med parametrarna som `{}` – så att testet förstår både
 * strängmallar (`"users/$uid/doses"`) och anrop till andra funktioner (`collection(uid, DOSES)`).
 */
function evaluate(expression, env) {
  const expr = expression.trim();
  if (expr.startsWith('"')) {
    return expr.slice(1, -1).replace(/\$\{([^}]+)\}|\$(\w+)/g, (_, inner, name) => evaluate(inner ?? name, env));
  }
  const call = /^(\w+)\((.*)\)$/.exec(expr);
  if (call) {
    const fn = functions[call[1]];
    if (!fn) throw new Error(`Paths.kt: okänd funktion ${call[1]}`);
    const args = call[2].split(',').map((a) => a.trim()).filter(Boolean).map((a) => evaluate(a, env));
    return evaluate(fn.body, Object.fromEntries(fn.params.map((p, i) => [p, args[i]])));
  }
  if (expr in env) return env[expr];
  if (expr in constants) return constants[expr];
  throw new Error(`Paths.kt: kan inte tolka ${expr}`);
}

test('samlingsnamnen är desamma som konstanterna i Paths.kt', () => {
  assert.deepEqual(Object.values(constants).sort(), COLLECTIONS.map((c) => c.name).sort());
});

test('sökvägarna är desamma som i Paths.kt', () => {
  const paths = Object.values(functions).map((fn) => evaluate(fn.body, Object.fromEntries(fn.params.map((p) => [p, '{}']))));
  // user(uid) pekar på ett dokument och generiska hjälpare (collection(uid, name)) på vad som
  // helst – en samlingssökväg slutar alltid med samlingens namn.
  const collectionPaths = paths.map(normalize).filter((p) => !p.endsWith('{}'));
  assert.ok(paths.map(normalize).includes('users/{}'), 'Paths.kt saknar user(uid)');
  const expected = COLLECTIONS.filter((c) => c.name !== 'users').map((c) => normalize(c.path));
  assert.deepEqual([...collectionPaths].sort(), [...expected].sort());
  for (const c of COLLECTIONS) assert.ok(c.path.endsWith(c.name), `${c.path} ska sluta med ${c.name}`);
});

test('samlingarna är de i ARKITEKTUR.md → Datamodell', () => {
  const model = read('ARKITEKTUR.md').split(/\n(?=## )/).find((s) => s.startsWith('## Datamodell'));
  assert.ok(model, 'ARKITEKTUR.md saknar avsnittet Datamodell');
  const documented = [...model.matchAll(/^\| `([\w/{}]+)`/gm)].map((m) => m[1].split('/').at(-1));
  assert.deepEqual([...documented].sort(), COLLECTIONS.filter((c) => c.name !== 'users').map((c) => c.name).sort());
});

test('varje samling har en egen regel under användaren, och inget annat är öppet', () => {
  const rules = read('firestore.rules');
  assert.match(rules, /match \/users\/\{uid\} \{/);
  // Ett jokertecken skulle släppa in samlingar som stoppar backupen.
  assert.doesNotMatch(rules, /match \/\{\w+\}\//, 'ingen match på godtyckliga samlingar');
  assert.doesNotMatch(rules, /\{\w+=\*\*\}/, 'inga rekursiva jokertecken');
  const matched = [...rules.matchAll(/match \/(\w+)\/\{\w+\}/g)].map((m) => m[1]).filter((name) => name !== 'databases');
  for (const c of COLLECTIONS.filter((c) => c.name !== 'users')) {
    assert.ok(c.path.startsWith('users/{uid}/'), `${c.path} ligger inte under en användare`);
    assert.ok(matched.includes(c.name), `${c.name} saknar regel i firestore.rules`);
  }
  assert.deepEqual(matched.sort(), COLLECTIONS.map((c) => c.name).sort(), 'rules och collections.mjs har samma samlingar');
});
