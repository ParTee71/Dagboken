// BCK-16: i testläge nås bara emulatorn med ett demo-projekt – aldrig den riktiga databasen.
import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { openFirestore } from '../lib/admin.mjs';

const saved = { ...process.env };
afterEach(() => {
  for (const key of ['FIRESTORE_EMULATOR_HOST', 'GCLOUD_PROJECT', 'NODE_TEST_CONTEXT']) {
    if (key in saved) process.env[key] = saved[key];
    else delete process.env[key];
  }
});

test('testläge utan emulator vägras', () => {
  delete process.env.FIRESTORE_EMULATOR_HOST;
  assert.throws(() => openFirestore({ test: true, projectId: 'demo-dagboken' }), /Testläge/);
});

test('testläge mot ett projekt som inte är demo vägras, även med emulator', () => {
  process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';
  assert.throws(() => openFirestore({ test: true, projectId: 'dagboken-711d2' }), /Testläge/);
});

test('testläge mot en emulator som inte är lokal vägras', () => {
  process.env.FIRESTORE_EMULATOR_HOST = 'firestore.example.com:8080';
  assert.throws(() => openFirestore({ test: true, projectId: 'demo-dagboken' }), /Testläge/);
});

test('under node --test gäller spärren även om testläge inte begärts', () => {
  process.env.NODE_TEST_CONTEXT = 'child';
  delete process.env.FIRESTORE_EMULATOR_HOST;
  assert.throws(() => openFirestore({ projectId: 'dagboken-711d2' }), /Testläge/);
});
