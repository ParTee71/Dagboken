// Nycklar från miljön: rå JSON eller base64, rätt projekt, och värdet syns aldrig i ett fel.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { defaultProjectId, parseServiceAccount, serviceAccountFor } from '../lib/credentials.mjs';
import { readRepoFile } from './helpers/repo.mjs';
import { runScript } from './helpers/run.mjs';

/** Projektet i den incheckade app/google-services.json – det tools/db och rules-deployen använder. */
const PROJECT = JSON.parse(readRepoFile('app/google-services.json')).project_info.project_id;

// Syntetisk nyckel – inget riktigt konto.
const key = { type: 'service_account', project_id: PROJECT, private_key: 'HEMLIG-TESTNYCKEL', client_email: `x@${PROJECT}.iam.gserviceaccount.com` };
const raw = JSON.stringify(key, null, 2);

test('standardprojektet är project_id ur google-services.json; FIREBASE_PROJECT_ID går före', (t) => {
  assert.equal(PROJECT, 'dagboken-711d2');
  assert.equal(defaultProjectId({}), PROJECT);
  assert.equal(defaultProjectId({ FIREBASE_PROJECT_ID: 'annat-projekt' }), 'annat-projekt');
  const dir = mkdtempSync(path.join(tmpdir(), 'tools-db-gs-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const broken = path.join(dir, 'google-services.json');
  writeFileSync(broken, '{"project_info": {}}');
  assert.throws(() => defaultProjectId({}, broken), /FIREBASE_PROJECT_ID/);
  assert.throws(() => defaultProjectId({}, path.join(dir, 'saknas.json')), /FIREBASE_PROJECT_ID/);
});

test('rules-deployen har samma standardprojekt som appen', () => {
  const action = readRepoFile('.github/actions/deploy-rules/action.yml');
  assert.match(action, new RegExp(`project:[\\s\\S]*?default: ${PROJECT}\\b`));
});

test('rå JSON och base64 ger samma nyckel', () => {
  assert.deepEqual(parseServiceAccount(raw, 'NYCKEL'), key);
  assert.deepEqual(parseServiceAccount(Buffer.from(raw).toString('base64'), 'NYCKEL'), key);
  assert.deepEqual(parseServiceAccount(`\n  ${raw}\n`, 'NYCKEL'), key, 'blanksteg runt det inklistrade');
});

test('tom, trasig eller fel sorts nyckel stoppar utan att visa värdet', () => {
  assert.throws(() => parseServiceAccount('', 'NYCKEL'), /NYCKEL saknas/);
  for (const bad of ['{"private_key":"HEMLIG-TESTNYCKEL"', JSON.stringify({ ...key, type: 'authorized_user' })]) {
    assert.throws(() => parseServiceAccount(bad, 'NYCKEL'), (error) => {
      assert.match(error.message, /^NYCKEL /);
      assert.doesNotMatch(error.message, /HEMLIG/);
      return true;
    });
  }
});

test('en nyckel för ett annat projekt stoppas – aldrig deploy till fel projekt', () => {
  assert.equal(serviceAccountFor(raw, 'NYCKEL', PROJECT).project_id, PROJECT);
  assert.throws(() => serviceAccountFor(JSON.stringify({ ...key, project_id: 'reseapoteket' }), 'NYCKEL', PROJECT), new RegExp(`reseapoteket, inte ${PROJECT}`));
});

test('tools/db öppnar inte databasen med en nyckel för ett annat projekt', () => {
  // Utan NODE_TEST_CONTEXT och emulator: kontrollen sker innan något anslutningsförsök.
  const env = { PATH: process.env.PATH, FIREBASE_SERVICE_ACCOUNT: JSON.stringify({ ...key, project_id: 'reseapoteket' }) };
  const result = runScript('stats.mjs', [], { env });
  assert.equal(result.status, 1);
  assert.match(result.stderr, new RegExp(`reseapoteket, inte ${PROJECT}`));
  assert.doesNotMatch(result.stdout + result.stderr, /HEMLIG-TESTNYCKEL/);
});

test('FIREBASE_PROJECT_ID styr vilket projekt nyckeln måste höra till', () => {
  const env = { PATH: process.env.PATH, FIREBASE_PROJECT_ID: 'annat-projekt', FIREBASE_SERVICE_ACCOUNT: raw };
  const result = runScript('stats.mjs', [], { env });
  assert.equal(result.status, 1);
  assert.match(result.stderr, new RegExp(`${PROJECT}, inte annat-projekt`));
  assert.doesNotMatch(result.stdout + result.stderr, /HEMLIG-TESTNYCKEL/);
});

test('write-credentials.mjs skriver nyckeln bara för rätt projekt, läsbar bara för ägaren', (t) => {
  const dir = mkdtempSync(path.join(tmpdir(), 'tools-db-key-'));
  t.after(() => rmSync(dir, { recursive: true, force: true }));
  const out = path.join(dir, 'key.json');
  const env = { PATH: process.env.PATH, NYCKEL: Buffer.from(raw).toString('base64') };

  const ok = runScript('write-credentials.mjs', ['--env', 'NYCKEL', '--project', PROJECT, '--out', out], { env });
  assert.equal(ok.status, 0, ok.stderr);
  assert.deepEqual(JSON.parse(readFileSync(out, 'utf8')), key);
  assert.equal(statSync(out).mode & 0o777, 0o600);
  assert.doesNotMatch(ok.stdout + ok.stderr, /HEMLIG-TESTNYCKEL/);

  const other = path.join(dir, 'other.json');
  const wrong = runScript('write-credentials.mjs', ['--env', 'NYCKEL', '--project', 'reseapoteket', '--out', other], { env });
  assert.equal(wrong.status, 1);
  assert.match(wrong.stderr, /fel nyckel/);
  assert.doesNotMatch(wrong.stdout + wrong.stderr, /HEMLIG-TESTNYCKEL/);
  assert.throws(() => statSync(other), 'ingen fil för fel projekt');
});
