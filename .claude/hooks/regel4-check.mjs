#!/usr/bin/env node
// PostToolUse-hook (Edit|Write|MultiEdit): snabb kontroll av regel 4 direkt efter en
// filändring, utan Gradle. Med `--stop` (Stop-hook): samma kontroll av alla ändrade filer
// innan turen avslutas. Mönster och undantag läses från samma filer som
// UiConsistencyTest använder – hooken har inga egna mönster (skill shared-ui-components).
//
// PostToolUse: exit 0 = inget att anmärka (tyst), exit 2 = träff; stderr går till Claude.
// Stop: alltid exit 0; vid träff `{"decision":"block"}` på stdout så att Claude rättar först.

import { readFileSync, existsSync, readdirSync, realpathSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PATTERNS_FILE = 'app/src/test/resources/ui-forbidden.txt';
const ALLOWLIST_FILE = 'app/src/test/resources/ui-allowlist.txt';

/** Läser hook-JSON från stdin. */
function readInput() {
  try {
    const raw = readFileSync(0, 'utf8');
    return raw.trim() ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

/** Tolkar en sektionerad fil (`[namn]` följt av rader; tomma rader och `#`-rader hoppas över). */
export function parseSections(text) {
  const sections = {};
  let current = null;
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim() || line.startsWith('#')) continue;
    const section = line.match(/^\[([\w-]+)\]\s*$/);
    if (section) sections[(current = section[1])] = [];
    else if (current) sections[current].push(line);
  }
  return sections;
}

/** Tolkar ui-forbidden.txt till { feature: [...], firestore: [...], expressive: [...], vico: [...] }. */
export function parsePatterns(text) {
  return Object.fromEntries(
    Object.entries(parseSections(text)).map(([scope, lines]) => [
      scope,
      lines.map((line) => {
        const [pattern, replacement = ''] = line.split('\t');
        return { source: pattern, regex: new RegExp(pattern), replacement };
      }),
    ]),
  );
}

/**
 * Tolkar ui-allowlist.txt till [{ file, symbol }]. Format per rad:
 * `<sökväg>:<symbol> – <motivering>`. En rad utan symbol eller motivering är ett fel.
 */
export function parseAllowlist(text) {
  const entries = [];
  text.split(/\r?\n/).forEach((line, i) => {
    if (!line.trim() || line.startsWith('#')) return;
    const [location, ...reason] = line.split(/\s+[–-]\s+/);
    const idx = location.lastIndexOf(':');
    const file = idx > 0 ? location.slice(0, idx).trim() : '';
    const symbol = idx > 0 ? location.slice(idx + 1).trim() : '';
    if (!file || !symbol || !reason.join('').trim()) {
      throw new Error(`ui-allowlist.txt rad ${i + 1}: ogiltig rad, formatet är "<sökväg>:<symbol> – <motivering>"`);
    }
    entries.push({ file, symbol });
  });
  return entries;
}

/** Ett undantag gäller bara träffar vars matchade text och symbolen överlappar – inte hela raden. */
export function isAllowed(allowlist, file, match) {
  const m = match.trim();
  return allowlist.some((a) => a.file === file && (m.includes(a.symbol) || a.symbol.includes(m)));
}

/**
 * Vilka scope en repo-relativ sökväg hör till.
 * - `feature`: Kotlin i ett ui-paket under app/src/main utom ui/components, ui/diagram, ui/theme och
 *   ui/common (även filer direkt under ui/).
 * - `firestore`: all Kotlin under app/src/main utom data/firestore/.
 * - `expressive`: all Kotlin under app/src/main utom ui/theme/ och ui/components/.
 * - `vico`: all Kotlin under app/src/main utom ui/diagram/ (diagrambiblioteket bara i diagrammen).
 */
export function scopesFor(relPath) {
  const p = relPath.split(path.sep).join('/');
  if (!p.startsWith('app/src/main/') || !p.endsWith('.kt')) return [];
  const scopes = [];
  const ui = p.match(/\/ui\/(?:([^/]+)\/)?[^/]+\.kt$|\/ui\/([^/]+)\//);
  if (ui) {
    const sub = ui[1] ?? ui[2];
    if (!['components', 'diagram', 'theme', 'common'].includes(sub)) scopes.push('feature');
  }
  if (!/\/data\/firestore\//.test(p)) scopes.push('firestore');
  if (!/\/ui\/(theme|components)\//.test(p)) scopes.push('expressive');
  if (!/\/ui\/diagram\//.test(p)) scopes.push('vico');
  return scopes;
}

/** Repots rot = närmaste förälder till filen som har mönsterfilen. */
export function findRepoRoot(absFile) {
  let dir = path.dirname(absFile);
  for (;;) {
    if (existsSync(path.join(dir, PATTERNS_FILE))) return dir;
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

/** Hittar förbjudna mönster i en fils innehåll. */
export function findViolations(relPath, content, scopes, allowlist) {
  const active = scopesFor(relPath).filter((s) => scopes[s]);
  if (active.length === 0) return [];
  const normalized = relPath.split(path.sep).join('/');
  const hits = [];
  content.split(/\r?\n/).forEach((line, i) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('import ')) return;
    for (const scope of active) {
      for (const rule of scopes[scope]) {
        const m = line.match(rule.regex);
        if (!m) continue;
        if (!isAllowed(allowlist, normalized, m[0])) hits.push({ line: i + 1, match: m[0], replacement: rule.replacement, scope });
      }
    }
  });
  return hits;
}

const SCOPE_TEXT = {
  feature: 'feature-kod (ui/<feature>/)',
  firestore: 'kod utanför data/firestore/',
  expressive: 'kod utanför ui/theme/ och ui/components/',
  vico: 'kod utanför ui/diagram/',
};

/** Läser mönster och undantag för ett repo. Kastar vid ogiltig undantagsfil. */
function loadRules(root) {
  const scopes = parsePatterns(readFileSync(path.join(root, PATTERNS_FILE), 'utf8'));
  const allowPath = path.join(root, ALLOWLIST_FILE);
  const allowlist = existsSync(allowPath) ? parseAllowlist(readFileSync(allowPath, 'utf8')) : [];
  return { scopes, allowlist };
}

/** Träffar i en fil (sökväg relativ repots rot); tom lista för filer utanför regelns räckvidd. */
function checkFile(root, rel, rules) {
  const abs = path.join(root, rel);
  if (!rel.endsWith('.kt') || !existsSync(abs) || scopesFor(rel).length === 0) return [];
  return findViolations(rel, readFileSync(abs, 'utf8'), rules.scopes, rules.allowlist).map((h) => ({ ...h, rel }));
}

function formatHits(hits) {
  const where = [...new Set(hits.map((h) => SCOPE_TEXT[h.scope] ?? h.scope))].join(' och ');
  const lines = hits.map((h) => `  ${h.rel}:${h.line}  \`${h.match.trim()}\`  → använd ${h.replacement}`);
  return (
    `Regel 4 (enhetligt och utan dubbelkod): förbjudna mönster i ${where}.\n` +
    lines.join('\n') +
    '\nAnvänd den delade komponenten/ramen/byggstenen (skill shared-ui-components). ' +
    'Behövs en variant: utöka den delade delen med en parameter. ' +
    'Undantag bara via app/src/test/resources/ui-allowlist.txt med motivering och användarens ok.\n'
  );
}

/** Filer som ändrats mot origin/master: committade, ändrade i arbetsträdet och nya. */
export function changedFiles(root) {
  const git = (...args) => {
    const r = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
    return r.status === 0 ? r.stdout.split('\n').filter(Boolean) : [];
  };
  return [
    ...new Set([
      ...git('diff', '--name-only', '--diff-filter=d', 'origin/master...HEAD'),
      ...git('diff', '--name-only', '--diff-filter=d', 'HEAD'),
      ...git('ls-files', '--others', '--exclude-standard'),
    ]),
  ];
}

/** PostToolUse: kontrollerar filen som just ändrades. */
function afterEdit(input) {
  const filePath = input?.tool_input?.file_path;
  if (!filePath) return 0;

  const base = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const abs = path.isAbsolute(filePath) ? filePath : path.join(base, filePath);
  if (!abs.endsWith('.kt') || !existsSync(abs)) return 0;

  // Repots rot räknas från filen, inte från sessionens projektrot – en session kan ha
  // flera repon under en gemensam katalog.
  const root = findRepoRoot(abs);
  if (!root) return 0;
  const rel = path.relative(root, abs);
  if (scopesFor(rel).length === 0) return 0;

  let rules;
  try {
    rules = loadRules(root);
  } catch (e) {
    process.stderr.write(`Regel 4: ${e.message}\n`);
    return 2;
  }
  const hits = checkFile(root, rel, rules);
  if (hits.length === 0) return 0;
  process.stderr.write(formatHits(hits));
  return 2;
}

/** Detta repos rot: hooken ligger i <rot>/.claude/hooks/. */
const OWN_ROOT = realpathSync(path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..'));
const HOOK_REL = '.claude/hooks/regel4-check.mjs';

/**
 * Repona som Stop kontrollerar: projektroten om den är ett repo, annars varje repo direkt under
 * den (en session med flera repon under en gemensam katalog), annars repot där skriptet ligger.
 */
export function stopRoots(input) {
  const isRepo = (dir) => existsSync(path.join(dir, PATTERNS_FILE));
  const base = process.env.CLAUDE_PROJECT_DIR || input?.cwd;
  if (base && isRepo(base)) return [base];
  const children = base && existsSync(base)
    ? readdirSync(base, { withFileTypes: true }).filter((d) => d.isDirectory()).map((d) => path.join(base, d.name)).filter(isRepo)
    : [];
  if (children.length > 0) return children;
  return isRepo(OWN_ROOT) ? [OWN_ROOT] : [];
}

/**
 * Träffar i ett repo. Det egna repot kontrolleras här; ett annat repo med egen hook får
 * kontrollera sig självt med sina egna scope, och ett repo utan hook lämnas därhän.
 * Returnerar { hits } eller { error } (trasig undantagsfil eller grannrepots egen träff).
 */
function repoHits(root) {
  if (realpathSync(root) === OWN_ROOT) {
    try {
      const rules = loadRules(root);
      return { hits: changedFiles(root).flatMap((rel) => checkFile(root, rel, rules)) };
    } catch (e) {
      return { error: `Regel 4: ${e.message}` };
    }
  }
  const hook = path.join(root, HOOK_REL);
  if (!existsSync(hook)) return { hits: [] };
  const r = spawnSync('node', [hook, '--stop'], {
    input: '{}', env: { ...process.env, CLAUDE_PROJECT_DIR: root }, encoding: 'utf8', timeout: 15000,
  });
  if (!r.stdout?.trim()) return { hits: [] };
  try {
    const out = JSON.parse(r.stdout);
    return out.decision === 'block' ? { error: out.reason } : { hits: [] };
  } catch {
    return { hits: [] };
  }
}

/**
 * Stop: kontrollerar alla ändrade filer innan Claude avslutar turen, så att en träff som
 * PostToolUse-meddelandet inte ledde till en rättning inte följer med till PR:en.
 * Blockerar högst en gång i följd (`stop_hook_active`) – sedan avgör Claude själv.
 */
function beforeStop(input) {
  if (input?.stop_hook_active) return 0;
  const roots = stopRoots(input);
  const reasons = [];
  for (const root of roots) {
    // Med flera repon visas reponamnet före sökvägen, så att träffarna går att skilja åt.
    const prefix = roots.length > 1 ? `${path.basename(root)}/` : '';
    const { hits, error } = repoHits(root);
    if (error) reasons.push(roots.length > 1 ? `${path.basename(root)}: ${error}` : error);
    else if (hits.length > 0) reasons.push(formatHits(hits.map((h) => ({ ...h, rel: prefix + h.rel }))));
  }
  if (reasons.length > 0) process.stdout.write(JSON.stringify({ decision: 'block', reason: reasons.join('\n') }));
  return 0;
}

function main() {
  const input = readInput();
  return process.argv.includes('--stop') ? beforeStop(input) : afterEdit(input);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main());
}
