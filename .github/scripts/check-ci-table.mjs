#!/usr/bin/env node
// Dokumentkontroll (regel 5): tabellen i skill ci-budget (mellan ci-table-markeringarna)
// ska ha exakt samma filter och sökvägar som dorny/paths-filter i android.yml.
// Kör: node .github/scripts/check-ci-table.mjs  (testas av check-ci-table.test.mjs)

import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const SKILL = '.claude/skills/ci-budget/SKILL.md';
export const WORKFLOW = '.github/workflows/android.yml';

/** { filter: [sökvägar] } ur tabellen mellan <!-- ci-table:start --> och <!-- ci-table:end -->. */
export function parseSkillTable(markdown) {
  const match = markdown.match(/<!-- ci-table:start -->([\s\S]*?)<!-- ci-table:end -->/);
  if (!match) throw new Error(`${SKILL}: markeringarna ci-table:start/end saknas`);
  const filters = {};
  for (const row of match[1].split('\n')) {
    const cells = row.split('|').map((c) => c.trim());
    const name = cells[1]?.match(/^`([\w-]+)`$/)?.[1];
    if (!name) continue; // rubrik, avdelare och raden "–"
    filters[name] = [...cells[2].matchAll(/`([^`]+)`/g)].map((m) => m[1]);
  }
  return filters;
}

/** { filter: [sökvägar] } ur blocket `filters: |` i workflowen. */
export function parseWorkflowFilters(yaml) {
  const lines = yaml.split('\n');
  const start = lines.findIndex((l) => /^\s*filters:\s*\|\s*$/.test(l));
  if (start < 0) throw new Error(`${WORKFLOW}: blocket "filters: |" saknas`);
  const indent = (l) => l.match(/^\s*/)[0].length;
  const baseIndent = indent(lines[start]);
  const filters = {};
  let current = null;
  for (const line of lines.slice(start + 1)) {
    if (!line.trim()) continue;
    if (indent(line) <= baseIndent) break;
    const name = line.match(/^\s*([\w-]+):\s*$/);
    const item = line.match(/^\s*-\s*['"]?([^'"]+?)['"]?\s*$/);
    if (name) filters[(current = name[1])] = [];
    else if (item && current) filters[current].push(item[1]);
  }
  return filters;
}

/** Skillnader mellan tabell och workflow, som läsbara rader. Tom lista = lika. */
export function compare(table, workflow) {
  const problems = [];
  for (const name of new Set([...Object.keys(table), ...Object.keys(workflow)])) {
    if (!(name in workflow)) { problems.push(`filtret \`${name}\` står i tabellen men inte i ${WORKFLOW}`); continue; }
    if (!(name in table)) { problems.push(`filtret \`${name}\` finns i ${WORKFLOW} men inte i tabellen`); continue; }
    const a = [...table[name]].sort().join(', ');
    const b = [...workflow[name]].sort().join(', ');
    if (a !== b) problems.push(`\`${name}\`: tabellen har [${a}], workflowen har [${b}]`);
  }
  return problems;
}

export function check(root) {
  return compare(
    parseSkillTable(readFileSync(path.join(root, SKILL), 'utf8')),
    parseWorkflowFilters(readFileSync(path.join(root, WORKFLOW), 'utf8')),
  );
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const problems = check(process.cwd());
  if (problems.length) {
    console.error(`CI-tabellen i ${SKILL} och filtren i ${WORKFLOW} skiljer sig – ändra båda i samma PR:\n  ${problems.join('\n  ')}`);
    process.exit(1);
  }
}
