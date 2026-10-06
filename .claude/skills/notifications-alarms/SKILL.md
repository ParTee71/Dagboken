---
name: notifications-alarms
description: Dagbokens påminnelse- och larmsystem (medicin, mående och periodslut) – i 4.0 med schemat ur Firestore-cachen. Ladda denna ALLTID när du rör notifikationer, larm, schemaläggning, bakgrundsväckning eller "Markera tagen" från notisen. Trigger-ord: notifikation, notification, påminnelse, larm, alarm, AlarmManager, AlarmScheduler, NotificationHelper, NotificationChannel, PendingIntent, BroadcastReceiver, BootReceiver, MedAlarmReceiver, ScreeningReminderReceiver, SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS, BOOT_COMPLETED, exakt larm, Doze, schemalägg.
---

# Notifikationer & larm

Androids mest felbenägna yta — exakta larm, körningstillstånd och väckning efter
omstart skiljer sig kraftigt mellan API-nivåer. Projektet kör **minSdk 30, targetSdk 35**,
så alla moderna restriktioner gäller. Krav: **NOT-serien (§10)**, särskilt NOT-1–8 och NOT-14;
TP-8, TP-9.

## 4.0: schemat ligger i Firestore-cachen

I 3.x läste larmen Room och DataStore (`PreferencesRepository`). I 4.0 (ARKITEKTUR.md →
Konsekvenser) läser de **samma repositories som UI:t**, ur Firestore-cachen (offline först):

- påminnelseinställningarna i dokumentet `settings` (medicinpåminnelser i sex tillfällen,
  måendepåminnelser i fyra, periodpåminnelse), recepten i `prescriptions` och dagens doser i
  `doses` (skill `firestore-data-layer`). Ingen egen Firestore-kod i en receiver.
- **"Markera tagen"** från notisen går via `DoseRepository.markTaken`: en skrivning i cachen direkt,
  offline först som avbockningen i appen – väntar aldrig på servern. Notisen stängs när det lyckats; bara
  vid ett lokalt fel (t.ex. utloggad) står den kvar med en rad om det.
- **Omschemaläggning** sker vid ändrade inställningar eller recept, när cachen fått ny data
  från servern (synk från en annan enhet eller migreringen), vid omstart och vid appuppdatering
  (NOT-14). Risk och hantering: ARKITEKTUR.md → Risker ("Larm tystnar när schemat ligger i cachen").
- Utloggad (skill `firebase-auth`) finns inget schema att läsa – alla larm avbokas och notiserna
  stängs (AUTH-6). Bara **bekräftat** utloggat läge avbokar: hinner inloggningen inte läsas in vid en
  kallstart, eller är cachen tom, står larmen kvar (en utlöst påminnelse lägger nästa larm på
  klockslaget i sina extras).

## Var koden bor

Paketet `reminders/` i appen (ARKITEKTUR.md → Lager och moduler); tidsberäkningen och urvalet i `:core/engine`:

| Del | Ansvar |
|---|---|
| `:core` `AlarmTimes` | `nextDailyAt`, `medAlarmTime`/`nextMedAlarm` (15 min före, `00:00 − 15 = 23:45`), `medReminderDate` – ren, med `now: Instant` och `TimeZone` som parametrar. |
| `:core` `ReminderPlan` | `Reminder` (`Med(slot)`, `Mood(occasion)`, `PeriodEnd`), `reminderAlarms`/`nextAlarm` ur `ReminderSettings` (`medSlots`, `screeningOccasions`, `periodReminderTime`), `slotDoses` (NOT-3/17), `moodReminderDue` (NOT-19). |
| `AlarmScheduler` | `@Singleton`, enda stället för larmlogik: `rescheduleAll` (avbokar allt, lägger de påslagna), `rescheduleNext` (en utlöst påminnelse), `cancelAll`. Unik `requestCode` per `Reminder`. |
| `ReminderContent` | Vad en påminnelse visar och "Markera tagen" – via `SettingsRepository`, `PrescriptionRepository`, `DoseRepository`, `ScreeningRepository`. |
| `NotificationHelper` | Kanalerna (`CHANNEL_MEDS` default, `CHANNEL_SCREENING` low – "Måendepåminnelser") och notiserna, privata med offentlig version. `markTakenFailed` bygger från grundtexten (idempotent); `dismiss` stänger måendepåminnelsen när "Logga nu" öppnat appen. |
| `ReminderActions` + mottagarna | `MedAlarmReceiver`, `ScreeningReminderReceiver`, `PeriodReminderReceiver`, `MedActionReceiver`, `BootReceiver` är tunna; vad de gör står i `ReminderActions` (testbart utan Hilt), körs av `ReceiverWork` (`goAsync`, nästa larm först, sedan notisen, under systemets tidsgräns). |
| `AlarmLedger` | Senast schemalagd och senast utlöst per påminnelse (NOT-14), enhetslokalt, utan innehåll. |
| `ReminderSync` | Startas från `MainActivity`; lägger om larmen när påminnelseinställningarna ändras i cachen (recept och doser påverkar inte larmen, bara notisens innehåll), avbokar vid utloggning. |
| `ReminderIntents` / `ReminderLaunch` | Actions och extras (en gång); vad en tryckt notis öppnar (bara kända värden tolkas). |
| `ReminderAccess` | Behörighetsläget och genvägarna till systeminställningarna (NOT-16). |

Behörigheter i `AndroidManifest.xml`: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`RECEIVE_BOOT_COMPLETED`. Alla mottagare `exported="false"`.

## Icke-förhandlingsbara regler

### Exakta larm (Android 12+ / API 31)
Använd alltid projektets `scheduleExact()`-mönster — kolla rättigheten och fall tillbaka
på inexakt larm i stället för att krascha:
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)   // NOT-8 fallback
} else {
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
}
```
`...AndAllowWhileIdle` krävs för att larmet ska gå igenom i **Doze**. Schemalägg aldrig
ett `setExact` utan idle-varianten för en påminnelse.

### Notifikationstillstånd (Android 13+ / API 33)
`POST_NOTIFICATIONS` är ett **runtime-tillstånd** från API 33. Notiser visas tyst om det
saknas. Begär det från UI (inte från en receiver) och hantera nekande utan att krascha.

### Notifikationskanaler (NOT-1)
Kanaler måste skapas (`NotificationHelper.createChannels`) **innan** första notisen och
är idempotenta. Två kanaler med rätt importance: `CHANNEL_MEDS` = `IMPORTANCE_DEFAULT`,
`CHANNEL_SCREENING` = `IMPORTANCE_LOW`. Ändra inte importance utan att uppdatera NOT-1.

### PendingIntent-flaggor
`FLAG_IMMUTABLE` är **obligatorisk** (API 31+). Använd `FLAG_UPDATE_CURRENT or
FLAG_IMMUTABLE` vid schemaläggning och `FLAG_NO_CREATE or FLAG_IMMUTABLE` vid avbokning.
Håll `requestCode` unik per larm (`AlarmScheduler.requestCode(reminder)`) — kolliderande
koder skriver över varandra.

### Återskapa efter omstart (NOT-6)
Larm överlever **inte** omstart. `BootReceiver` (`@AndroidEntryPoint`) anropar
`AlarmScheduler.rescheduleAll()` via `ReceiverWork` (`goAsync()` + coroutine) så att det hinner klart.
`BootReceiver.RESCHEDULE_ACTIONS` och manifestets intent-filter är samma lista (testat).

### Schemalägg om vid ändring (NOT-7)
Varje ändring som påverkar tider/på-av (inställningar, recept, vid behov-mediciner) – och ny
data från servern – ska följas av `rescheduleAll()` – `ReminderSync` gör det medan appen kör.
Lämna aldrig gamla larm kvar.

### Vilka larm som skapas
Endast aktiverade rader schemaläggs (`reminderAlarms`). Endast ej tagna/ej skippade
mediciner ska generera notis (NOT-3) — `slotDoses`, läst när larmet går. Ett larm som passerat
dagens tid rullar till nästa dag (NOT-5) — `nextDailyAt`/`nextMedAlarm`.
Undantag (NOT-14): ett larm som är högst `AlarmScheduler.LATE_GRACE` (30 min) sent ligger kvar på dagens tid
vid `rescheduleAll` – bara om just den tiden redan var schemalagd och påminnelsen inte utlösts. Det avgörs ur
`AlarmLedger` (senast schemalagd och senast utlöst per påminnelse, enhetslokalt i SharedPreferences som är undantagna
från backup; bara nyckel och epoch-millisekunder, aldrig Firestore), så att det håller också efter att processen dött
eller en omstart. En nyss flyttad eller påslagen tid går till i morgon (NOT-5).
Efter omstart/appuppdatering (`BootReceiver.CLEARS_ALARMS`) med oläsbara inställningar lägger
`rescheduleAll(alarmsCleared = true)` om de senast schemalagda tiderna ur `AlarmLedger` (`restoredAlarms`, NOT-6) –
utan det vore kedjan borta tills appen öppnas. `ReminderSync` skickar sina redan lästa inställningar
(`rescheduleAll(known = …)`); standardvärdena bekräftas ändå med en läsning (så ser ett dokument ut som saknas i
cachen). Utloggning tömmer också `AlarmLedger`. `ReceiverWork` fångar undantag per steg (loggar bara typen) så att
notissteget alltid körs – också ett `CancellationException` inifrån steget (t.ex. en avbruten Task); bara när
mottagarens egen coroutine är avbruten kastas det vidare (`ensureActive`); `SecurityException` vid exakt larm ger ett inexakt, och ledgern skrivs först när larmet satts.
`AlarmScheduler`s lås är ett vanligt JVM-lås kring AlarmManager-anropen och `AlarmLedger` – aldrig en läsning
eller suspension – så att `rescheduleNext` (nästa larm i en mottagare) aldrig väntar ut en omschemaläggning, och så
att mottagaren kan registrera larmet som utlöst synkront i `onReceive` (`markFired`) innan något suspenderar:
en `rescheduleAll` som hinner före mottagarens coroutine lägger då aldrig det passerade larmet igen. Mottagarnas tidsbudget (`ReceiverWork`, `ReceiverBudgetTest`): nästa larm först,
sedan notisen på reserverad tid; läsningarna har gemensamma tak (`ReminderContent.READ_WAIT`).

## Tester (regel 2)

- **Tidsberäkningen är ren** (`AlarmTimesTest`, `ReminderPlanTest` i `:core`): framtida tid idag,
  passerad tid → nästa dag, midnattsvridning (`00:00 − 15 min = 23:45`), förvarning, sommartid.
- Schemaläggaren mot Robolectrics `ShadowAlarmManager` och repositories på `FakeCollection`
  (`ReminderFixture`, `AlarmSchedulerTest`): rätt larm vid på/av, allt avbokas före omschemaläggning,
  en ändring i cachen ger ny schemaläggning, utloggad → inga larm, tom cache → larmen står kvar.
- "Markera tagen" och utlösta larm genom `ReminderActions` (`ReminderActionsTest`) – lyckat stänger
  notisen, misslyckat lämnar den; bara status och tagningstid på befintliga doser (`ReminderContentTest`).
- Mottagarna själva är tunna (Hilt) — håll logiken i `ReminderActions`/scheduler/helper och testa den.

## Vanliga fallgropar

- Glömt `...AndAllowWhileIdle` → larm tappas i Doze.
- Glömt `FLAG_IMMUTABLE` → krasch på API 31+.
- Antar att `POST_NOTIFICATIONS` finns → notis försvinner tyst.
- Schemalägger nytt utan att avboka gammalt → dubblerade/föräldralösa larm.
- Hårdkodad notistext i stället för `strings.xml` (UI-text ska vara svensk).
- Hälsoinnehåll synligt på låsskärmen – notiser är privata (skill `data-privacy-security`).
- Egen Firestore-läsning i en receiver i stället för repositoryt – eller att vänta på servern.
