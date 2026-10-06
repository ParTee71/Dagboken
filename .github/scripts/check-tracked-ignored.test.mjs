// Körs med: node --test '.github/scripts/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { trackedIgnored } from './check-tracked-ignored.mjs';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');

test('inga spårade filer som .gitignore ignorerar', () => {
  assert.deepEqual(trackedIgnored(repoRoot), []);
});

test('hittar en spårad fil som .gitignore ignorerar', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'tracked-ignored-'));
  const git = (...args) => execFileSync('git', args, { cwd: dir, stdio: 'pipe' });
  git('init', '-q');
  fs.mkdirSync(path.join(dir, '.idea'));
  fs.writeFileSync(path.join(dir, '.idea', 'misc.xml'), '<x/>');
  fs.writeFileSync(path.join(dir, 'ok.txt'), 'ok');
  git('add', '.');
  fs.writeFileSync(path.join(dir, '.gitignore'), '.idea/\n');
  git('add', '.gitignore');
  assert.deepEqual(trackedIgnored(dir), ['.idea/misc.xml']);
  fs.rmSync(dir, { recursive: true, force: true });
});
