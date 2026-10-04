// Dataformatets version – speglar Schema.kt i :core (test/schema.test.mjs håller dem lika).
export const CURRENT_VERSION = 1;

/** Version för en användare utan schemaVersion – det första formatet, aldrig "senaste". */
export const FIRST_VERSION = 1;

/** En användares version ur dess data; saknas den eller är den under den första gäller den första (BCK-15). */
export const versionOf = (user) => {
  const version = user?.schemaVersion;
  return Number.isInteger(version) && version >= FIRST_VERSION ? version : FIRST_VERSION;
};
