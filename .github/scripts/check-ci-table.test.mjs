// Körs med: node --test '.github/scripts/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { check, compare, parseSkillTable, parseWorkflowFilters } from './check-ci-table.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

const table = `
<!-- ci-table:start -->
| Filter | Sökvägar | Körs |
|---|---|---|
| \`core\` | \`core/**\` | x |
| \`app\` | \`app/**\`, \`.claude/skills/shared-ui-components/**\` | y |
| – | allt annat (\`*.md\`) | bara changes |
<!-- ci-table:end -->`;

const workflow = `
      - uses: dorny/paths-filter@v3
        with:
          filters: |
            core:
              - 'core/**'
            app:
              - 'app/**'
              - '.claude/skills/shared-ui-components/**'

      - name: Nästa steg`;

test('tabellen i ci-budget och filtren i android.yml är lika', () => {
  assert.deepEqual(check(repoRoot), []);
});

test('tolkar tabell och workflow till samma filter', () => {
  const expected = { core: ['core/**'], app: ['app/**', '.claude/skills/shared-ui-components/**'] };
  assert.deepEqual(parseSkillTable(table), expected);
  assert.deepEqual(parseWorkflowFilters(workflow), expected);
});

test('en sökväg bara i tabellen fäller kontrollen', () => {
  const t = parseSkillTable(table.replace('`core/**`', '`core/**`, `ny/**`'));
  const problems = compare(t, parseWorkflowFilters(workflow));
  assert.equal(problems.length, 1);
  assert.match(problems[0], /core/);
});

test('ett filter bara i workflowen fäller kontrollen', () => {
  const w = parseWorkflowFilters(workflow.replace("            app:", "            tools:\n              - 'tools/**'\n            app:"));
  assert.deepEqual(compare(parseSkillTable(table), w), ['filtret `tools` finns i .github/workflows/android.yml men inte i tabellen']);
});

test('saknade markeringar är ett fel, inte en tom tabell', () => {
  assert.throws(() => parseSkillTable('| `core` | `core/**` | x |'), /ci-table/);
});
