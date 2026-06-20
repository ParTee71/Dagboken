# Dagboken – Android

Hälsodagbok för att logga aktiviteter, daglig screening (energi, stress, symptom) och mediciner, med diagram, påminnelser och molnbackup via Google Drive.

**Kravspecifikation:** [KRAVLISTA.md](KRAVLISTA.md)

---

## Kom igång

### Förutsättningar

- Android Studio Hedgehog (2023.1) eller senare
- JDK 17
- Android SDK API 35 (compileSdk), minSdk 30

### Bygg och kör

```bash
git clone https://github.com/partee71/dagboken-android.git
cd dagboken-android
./gradlew :app:assembleDebug
```

Öppna projektet i Android Studio och kör på en enhet eller emulator (API 30+).

### Google Services

Appen kräver en `google-services.json` från Firebase Console (Firebase Auth + Google Sign-In). Placera filen i `app/`. Filen är git-ignorerad och delas inte i repot.

---

## Releasebygge och signering

Releasebygget kräver en keystore och lösenord via `local.properties` (git-ignorerad) eller miljövariabler:

**local.properties:**
```properties
signing.storePassword=<lösenord>
signing.keyAlias=dagboken
signing.keyPassword=<lösenord>
```

**Miljövariabler (CI):**
```
SIGNING_STORE_PASSWORD
SIGNING_KEY_ALIAS
SIGNING_KEY_PASSWORD
```

Keystorefilen `dagboken.jks` placeras i `app/` och är git-ignorerad.

---

## Arkitektur

```
app/
├── data/
│   ├── auth/          FirebaseAuthRepository – Google Sign-In via Credential Manager
│   ├── datastore/     PreferencesRepository – inställningar, teman, screeningtider
│   ├── migration/     DriveBackupRepository – import/export mot Google Drive
│   ├── repository/    AktiviteterRepository, MedicinerRepository – Room-abstraktioner
│   └── room/          Room-databas, DAOs, entiteter
├── di/                Hilt-moduler (AppModule, DatabaseModule, …)
├── domain/
│   ├── model/         Domänmodeller (Aktivitet, Medicin, Recept, Favorit, …)
│   └── usecase/       EnsureTodayEntriesUseCase, DosLimitUseCase, …
├── ui/
│   ├── aktiviteter/   Loggning av aktiviteter och screening
│   ├── diagram/       Trenddiagram (LineChartCanvas)
│   ├── home/          Hemskärm med dagens mediciner och screeningstatus
│   ├── mediciner/     Mediciner, recept, favoriter och vid-behov-doser
│   ├── migration/     Importguide från Google Drive-backup
│   ├── settings/      Inställningar, tema, notiser, backup
│   └── theme/         Material 3-tema (färger, typografi, animationer)
└── worker/            BackupWorker – schemalagd Drive-backup
```

**Stack:** Kotlin · Jetpack Compose + Material 3 · MVVM · Hilt · Room · DataStore · Firebase Auth · Google Drive API · WorkManager

---

## CI

GitHub Actions kör vid push/PR mot `master`:
- `:app:compileDebugKotlin`
- `:app:compileDebugUnitTestKotlin`
- `:app:compileDebugAndroidTestKotlin`
- `:app:testDebugUnitTest`

Se [.github/workflows/android.yml](.github/workflows/android.yml).
