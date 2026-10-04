// Service-account-nycklar från miljön – enda tolkningen, delad av databasverktygen och
// rules-deployen (skill data-privacy-security). Värdet skrivs aldrig ut, inte ens i fel.
import { readFileSync } from 'node:fs';

/** Appens Firebase-konfiguration (incheckad, ingen hemlighet) – källan till standardprojektet. */
export const GOOGLE_SERVICES = new URL('../../../app/google-services.json', import.meta.url);

/**
 * Projektet som nycklarna måste höra till: FIREBASE_PROJECT_ID om den är satt, annars
 * `project_info.project_id` ur app/google-services.json – samma projekt som appen talar med.
 */
export function defaultProjectId(env = process.env, file = GOOGLE_SERVICES) {
  if (env.FIREBASE_PROJECT_ID) return env.FIREBASE_PROJECT_ID;
  let id;
  try {
    id = JSON.parse(readFileSync(file, 'utf8'))?.project_info?.project_id;
  } catch {
    id = undefined;
  }
  if (!id) throw new Error('Firebase-projektet är okänt – sätt FIREBASE_PROJECT_ID eller lägg tillbaka app/google-services.json.');
  return id;
}

/**
 * Nyckeln i [value] som objekt. Rå JSON (som den laddas ner från Google Cloud) och base64 av
 * den godtas båda, så att en nyckel kan läggas in från telefonen utan kodning.
 */
export function parseServiceAccount(value, name) {
  const trimmed = (value ?? '').trim();
  if (!trimmed) throw new Error(`${name} saknas.`);
  let account;
  try {
    const json = trimmed.startsWith('{') ? trimmed : Buffer.from(trimmed, 'base64').toString('utf8');
    account = JSON.parse(json);
  } catch {
    throw new Error(`${name} är varken JSON eller base64-kodad JSON.`);
  }
  if (account?.type !== 'service_account' || !account.project_id || !account.private_key) {
    throw new Error(`${name} är inte en service-account-nyckel.`);
  }
  return account;
}

/** Som [parseServiceAccount], men stoppar om nyckeln hör till ett annat projekt än [projectId]. */
export function serviceAccountFor(value, name, projectId) {
  const account = parseServiceAccount(value, name);
  if (account.project_id !== projectId) {
    throw new Error(`${name} hör till projektet ${account.project_id}, inte ${projectId} – fel nyckel.`);
  }
  return account;
}
