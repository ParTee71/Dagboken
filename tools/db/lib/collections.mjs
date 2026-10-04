// Alla Firestore-samlingar – enda listan som export, import och query går igenom
// (skill data-safety-backup). Speglar Paths.kt i appen och ARKITEKTUR.md → Datamodell;
// test/collections.test.mjs håller dem lika. {uid} = användare (dokumentets ägare),
// {eid} = sjukdomsepisod.

export const COLLECTIONS = [
  { name: 'users', path: 'users' },
  { name: 'settings', path: 'users/{uid}/settings' },
  { name: 'options', path: 'users/{uid}/options' },
  { name: 'prescriptions', path: 'users/{uid}/prescriptions' },
  { name: 'prnMedicines', path: 'users/{uid}/prnMedicines' },
  { name: 'doses', path: 'users/{uid}/doses' },
  { name: 'screenings', path: 'users/{uid}/screenings' },
  { name: 'activities', path: 'users/{uid}/activities' },
  { name: 'events', path: 'users/{uid}/events' },
  { name: 'illnessEpisodes', path: 'users/{uid}/illnessEpisodes' },
  { name: 'checkins', path: 'users/{uid}/illnessEpisodes/{eid}/checkins' },
];
