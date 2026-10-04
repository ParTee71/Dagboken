// Admin SDK mot Firestore – enda stället som initierar (skill db-access, data-safety-backup).
// Riktig databas: FIREBASE_SERVICE_ACCOUNT (service-account-JSON, rå eller base64; skrivningar tar
// FIREBASE_SERVICE_ACCOUNT_RW om den finns), som måste höra till FIREBASE_PROJECT_ID (standard:
// project_id ur app/google-services.json). Emulator: FIRESTORE_EMULATOR_HOST.
// Testläge (BCK-16): bara emulatorn och ett demo-projekt – aldrig den riktiga databasen.
import { cert, initializeApp } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { defaultProjectId, serviceAccountFor } from './credentials.mjs';

let appCount = 0;
const newApp = (options) => initializeApp(options, `tools-db-${++appCount}`);

/** Under `node --test` sätts NODE_TEST_CONTEXT; testhjälparen begär dessutom alltid testläge. */
const inTestMode = (requested) => requested || Boolean(process.env.NODE_TEST_CONTEXT);

/**
 * Firestore-instans. `test: true` (eller körning under node --test) godkänner bara
 * FIRESTORE_EMULATOR_HOST satt och ett projekt-ID som börjar med `demo-`.
 */
export function openFirestore({ test = false, write = false, projectId } = {}) {
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;
  if (inTestMode(test)) {
    const id = projectId ?? process.env.GCLOUD_PROJECT;
    const local = /^(localhost|127\.0\.0\.1|\[::1\]):\d+$/.test(emulator ?? '');
    if (!local || !id?.startsWith('demo-')) {
      throw new Error('Testläge: bara en lokal Firebase-emulator (FIRESTORE_EMULATOR_HOST) och ett demo-projekt tillåts.');
    }
    return getFirestore(newApp({ projectId: id }));
  }
  if (emulator) return getFirestore(newApp({ projectId: projectId ?? process.env.GCLOUD_PROJECT ?? 'demo-dagboken' }));

  const name = write && process.env.FIREBASE_SERVICE_ACCOUNT_RW ? 'FIREBASE_SERVICE_ACCOUNT_RW' : 'FIREBASE_SERVICE_ACCOUNT';
  if (!process.env[name]) {
    throw new Error(`${name} saknas – lägg in den som miljövariabel i Claude-miljöns inställningar och starta en ny session (README → Utveckling via Claude). Klistra aldrig in nyckeln i chatten.`);
  }
  const account = serviceAccountFor(process.env[name], name, defaultProjectId());
  return getFirestore(newApp({ credential: cert(account), projectId: account.project_id }));
}
