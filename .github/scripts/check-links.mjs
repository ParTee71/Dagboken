#!/usr/bin/env node
// Dokumentkontroll: relativa länkar och nämnda skills/agenter i dokumentationen finns, och
// tabellerna i .claude/README.md listar exakt de skills och agenter som finns i .claude/.
// Framåtreferenser till sådant som byggs senare står i link-allowlist.txt (töms allteftersom).
// Pluginens skills och agenter (`android:<namn>`, pluginen android@claude-android) står i
// plugin-skills.txt och plugin-agents.txt – pluginen finns inte i CI, så listorna i repot är facit.
// Kör: node .github/scripts/check-links.mjs  (testas av check-links.test.mjs)

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ALLOWLIST = '.github/scripts/link-allowlist.txt';
/** Översikten över skills och agenter (CLAUDE.md länkar hit, så att den filen hålls kort). */
export const TABLES = '.claude/README.md';
/** Pluginens skillnamn utan prefix, en per rad (pluginen android@claude-android). */
export const PLUGIN_SKILLS = '.github/scripts/plugin-skills.txt';
/** Pluginens agentnamn utan prefix, en per rad (samma format som plugin-skills.txt). */
export const PLUGIN_AGENTS = '.github/scripts/plugin-agents.txt';
export const PLUGIN_PREFIX = 'android:';

/** Dokumenten som kontrolleras: rotens md-filer och allt i .claude/ (utom lokala inställningar). */
export function docFiles(root) {
  const files = ['CLAUDE.md', 'README.md', 'ARKITEKTUR.md', 'KRAVLISTA.md'].filter((f) => existsSync(path.join(root, f)));
  const walk = (dir) => {
    for (const entry of readdirSync(path.join(root, dir))) {
      const rel = `${dir}/${entry}`;
      if (statSync(path.join(root, rel)).isDirectory()) walk(rel);
      else if (rel.endsWith('.md')) files.push(rel);
    }
  };
  if (existsSync(path.join(root, '.claude'))) walk('.claude');
  return files;
}

/** Relativa länkmål i markdown (utan ankare), utom kodblock. */
export function relativeLinks(markdown) {
  const text = markdown.replace(/```[\s\S]*?```/g, '');
  return [...text.matchAll(/\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g)]
    .map((m) => m[1])
    .filter((t) => !/^(https?:|mailto:|#)/.test(t))
    .map((t) => decodeURI(t.split('#')[0]))
    .filter(Boolean);
}

/**
 * Skill- och agentnamn som nämns i löptext: "skill `x`", "Skill: **x**", "Skills: **a**, **b**",
 * "agenten `x`". Pluginskills behåller prefixet ("skill **android:mockup**" → `android:mockup`).
 */
export function mentions(markdown) {
  const skills = new Set();
  const agents = new Set();
  const name = String.raw`(?:\*\*|\`)((?:android:)?(?:[a-z0-9]+(?:-[a-z0-9]+)+|[a-z]{3,}))(?:\*\*|\`)`;
  for (const m of markdown.matchAll(new RegExp(String.raw`\b[Ss]kill:?\s+${name}`, 'g'))) skills.add(m[1]);
  for (const m of markdown.matchAll(/\b[Ss]kills:\s+((?:\*\*(?:android:)?[a-z0-9-]+\*\*(?:,\s*|\s+och\s+)?)+)/g)) {
    for (const n of m[1].matchAll(/\*\*((?:android:)?[a-z0-9-]+)\*\*/g)) skills.add(n[1]);
  }
  for (const m of markdown.matchAll(new RegExp(String.raw`\b[Aa]genten\s+${name}`, 'g'))) agents.add(m[1]);
  return { skills, agents };
}

/** Förstakolumnens namn i översiktstabellen under rubriken (t.ex. "## Skills"). */
export function tableNames(markdown, heading) {
  const section = markdown.split(/\n(?=## )/).find((s) => s.startsWith(`## ${heading}`));
  if (!section) return new Set();
  const names = new Set();
  for (const row of section.split('\n').filter((l) => l.startsWith('|'))) {
    for (const m of (row.split('|')[1] ?? '').matchAll(/`([a-z0-9-]+)`/g)) names.add(m[1]);
  }
  return names;
}

/** Namnen i plugin-skills.txt (kommentarer och tomma rader hoppas över). */
export function parsePluginSkills(text) {
  return new Set(text.split('\n').map((l) => l.trim()).filter((l) => l && !l.startsWith('#')));
}

function parseAllowlist(text) {
  return new Set(
    text.split('\n').filter((l) => l.trim() && !l.startsWith('#')).map((l) => l.split(/\s+[–-]\s+/)[0].trim()),
  );
}

export function check(root) {
  const allow = existsSync(path.join(root, ALLOWLIST)) ? parseAllowlist(readFileSync(path.join(root, ALLOWLIST), 'utf8')) : new Set();
  const readList = (file) => (existsSync(path.join(root, file)) ? parsePluginSkills(readFileSync(path.join(root, file), 'utf8')) : new Set());
  const plugin = readList(PLUGIN_SKILLS);
  const pluginAgents = readList(PLUGIN_AGENTS);
  const skillExists = (n) => existsSync(path.join(root, '.claude/skills', n, 'SKILL.md'));
  const mentionedSkillExists = (n) => (n.startsWith(PLUGIN_PREFIX) ? plugin.has(n.slice(PLUGIN_PREFIX.length)) : skillExists(n));
  const agentExists = (n) => existsSync(path.join(root, '.claude/agents', `${n}.md`));
  const mentionedAgentExists = (n) => (n.startsWith(PLUGIN_PREFIX) ? pluginAgents.has(n.slice(PLUGIN_PREFIX.length)) : agentExists(n));
  const problems = [];
  const report = (file, target, what) => {
    if (!allow.has(`${file}:${target}`)) problems.push(`${file}: ${what}`);
  };

  for (const file of docFiles(root)) {
    const text = readFileSync(path.join(root, file), 'utf8');
    for (const target of relativeLinks(text)) {
      if (!existsSync(path.resolve(root, path.dirname(file), target))) report(file, target, `länken ${target} pekar på något som inte finns`);
    }
    const { skills, agents } = mentions(text);
    for (const s of skills) {
      if (mentionedSkillExists(s)) continue;
      report(file, s, s.startsWith(PLUGIN_PREFIX) ? `skill \`${s}\` finns inte i ${PLUGIN_SKILLS}` : `skill \`${s}\` finns inte i .claude/skills/`);
    }
    for (const a of agents) {
      if (mentionedAgentExists(a)) continue;
      report(file, a, a.startsWith(PLUGIN_PREFIX) ? `agenten \`${a}\` finns inte i ${PLUGIN_AGENTS}` : `agenten \`${a}\` finns inte i .claude/agents/`);
    }
  }

  const tables = existsSync(path.join(root, TABLES)) ? readFileSync(path.join(root, TABLES), 'utf8') : '';
  const listedSkills = tableNames(tables, 'Skills');
  const listedAgents = tableNames(tables, 'Agenter');
  const actualSkills = readdirSync(path.join(root, '.claude/skills')).filter(skillExists);
  const actualAgents = readdirSync(path.join(root, '.claude/agents')).filter((f) => f.endsWith('.md')).map((f) => f.slice(0, -3));
  for (const s of actualSkills) if (!listedSkills.has(s)) problems.push(`${TABLES}: skill \`${s}\` saknas i tabellen "Skills"`);
  for (const s of listedSkills) if (!actualSkills.includes(s)) problems.push(`${TABLES}: tabellen "Skills" nämner \`${s}\`, som inte finns`);
  for (const a of actualAgents) if (!listedAgents.has(a)) problems.push(`${TABLES}: agenten \`${a}\` saknas i tabellen "Agenter"`);
  for (const a of listedAgents) if (!actualAgents.includes(a)) problems.push(`${TABLES}: tabellen "Agenter" nämner \`${a}\`, som inte finns`);
  return problems;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const problems = check(process.cwd());
  if (problems.length) {
    console.error(`Dokumentationen pekar på sådant som inte finns:\n  ${problems.join('\n  ')}`);
    process.exit(1);
  }
}
