#!/usr/bin/env node
// Dokumentkontroll: relativa länkar och nämnda skills/agenter i dokumentationen finns, och
// tabellerna i CLAUDE.md listar exakt de skills och agenter som finns i .claude/.
// Framåtreferenser till sådant som byggs senare står i link-allowlist.txt (töms allteftersom).
// Kör: node .github/scripts/check-links.mjs  (testas av check-links.test.mjs)

import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const ALLOWLIST = '.github/scripts/link-allowlist.txt';

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

/** Skill- och agentnamn som nämns i löptext: "skill `x`", "Skill: **x**", "Skills: **a**, **b**", "agenten `x`". */
export function mentions(markdown) {
  const skills = new Set();
  const agents = new Set();
  const name = String.raw`(?:\*\*|\`)([a-z0-9]+(?:-[a-z0-9]+)+|[a-z]{3,})(?:\*\*|\`)`;
  for (const m of markdown.matchAll(new RegExp(String.raw`\b[Ss]kill:?\s+${name}`, 'g'))) skills.add(m[1]);
  for (const m of markdown.matchAll(/\b[Ss]kills:\s+((?:\*\*[a-z0-9-]+\*\*(?:,\s*|\s+och\s+)?)+)/g)) {
    for (const n of m[1].matchAll(/\*\*([a-z0-9-]+)\*\*/g)) skills.add(n[1]);
  }
  for (const m of markdown.matchAll(new RegExp(String.raw`\b[Aa]genten\s+${name}`, 'g'))) agents.add(m[1]);
  return { skills, agents };
}

/** Förstakolumnens namn i CLAUDE.md-tabellen under rubriken (t.ex. "## Skills"). */
export function tableNames(markdown, heading) {
  const section = markdown.split(/\n(?=## )/).find((s) => s.startsWith(`## ${heading}`));
  if (!section) return new Set();
  const names = new Set();
  for (const row of section.split('\n').filter((l) => l.startsWith('|'))) {
    for (const m of (row.split('|')[1] ?? '').matchAll(/`([a-z0-9-]+)`/g)) names.add(m[1]);
  }
  return names;
}

function parseAllowlist(text) {
  return new Set(
    text.split('\n').filter((l) => l.trim() && !l.startsWith('#')).map((l) => l.split(/\s+[–-]\s+/)[0].trim()),
  );
}

export function check(root) {
  const allow = existsSync(path.join(root, ALLOWLIST)) ? parseAllowlist(readFileSync(path.join(root, ALLOWLIST), 'utf8')) : new Set();
  const skillExists = (n) => existsSync(path.join(root, '.claude/skills', n, 'SKILL.md'));
  const agentExists = (n) => existsSync(path.join(root, '.claude/agents', `${n}.md`));
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
    for (const s of skills) if (!skillExists(s)) report(file, s, `skill \`${s}\` finns inte i .claude/skills/`);
    for (const a of agents) if (!agentExists(a)) report(file, a, `agenten \`${a}\` finns inte i .claude/agents/`);
  }

  const claude = readFileSync(path.join(root, 'CLAUDE.md'), 'utf8');
  const listedSkills = tableNames(claude, 'Skills');
  const listedAgents = tableNames(claude, 'Agenter');
  const actualSkills = readdirSync(path.join(root, '.claude/skills')).filter(skillExists);
  const actualAgents = readdirSync(path.join(root, '.claude/agents')).filter((f) => f.endsWith('.md')).map((f) => f.slice(0, -3));
  for (const s of actualSkills) if (!listedSkills.has(s)) problems.push(`CLAUDE.md: skill \`${s}\` saknas i tabellen "Skills"`);
  for (const s of listedSkills) if (!actualSkills.includes(s)) problems.push(`CLAUDE.md: tabellen "Skills" nämner \`${s}\`, som inte finns`);
  for (const a of actualAgents) if (!listedAgents.has(a)) problems.push(`CLAUDE.md: agenten \`${a}\` saknas i tabellen "Agenter"`);
  for (const a of listedAgents) if (!actualAgents.includes(a)) problems.push(`CLAUDE.md: tabellen "Agenter" nämner \`${a}\`, som inte finns`);
  return problems;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const problems = check(process.cwd());
  if (problems.length) {
    console.error(`Dokumentationen pekar på sådant som inte finns:\n  ${problems.join('\n  ')}`);
    process.exit(1);
  }
}
