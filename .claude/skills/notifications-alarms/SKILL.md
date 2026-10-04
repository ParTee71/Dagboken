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
- **"Markera tagen"** från notisen skriver dosens `status`/`takenAt` till cachen via repositoryt
  och synkas när nätet finns – den väntar aldrig på servern.
- **Omschemaläggning** sker vid ändrade inställningar eller recept, när cachen fått ny data
  från servern (synk från en annan enhet eller migreringen), vid omstart och vid appuppdatering
  (NOT-14). Risk och hantering: ARKITEKTUR.md → Risker ("Larm tystnar när schemat ligger i cachen").
- Utloggad (skill `firebase-auth`) finns inget schema att läsa – inga larm.

## Var koden bor

Paketet `reminders/` i appen (ARKITEKTUR.md → Lager och moduler) med samma ansvarsfördelning som
3.x `notifications/` – portas i etapp 6:

| Del | Ansvar |
|---|---|
| Schemaläggaren (3.x `AlarmScheduler`) | `@Singleton`, schemalägger/avbryter medicin-, mående- och periodlarm. Single source för all larmlogik. |
| Notishjälparen (3.x `NotificationHelper`) | Skapar kanaler (`CHANNEL_MEDS` default, `CHANNEL_SCREENING` low) och postar notiser. |
| Larmmottagare (3.x `MedAlarmReceiver`, `ScreeningReminderReceiver`) | `BroadcastReceiver` som tar emot larm och postar notis. |
| Omstartsmottagare (3.x `BootReceiver`) | Återskapar alla larm efter `BOOT_COMPLETED` och `MY_PACKAGE_REPLACED` (NOT-14). |

Tidsberäkningar som går att göra rent (nästa utlösning, periodslut, NOT-12) ligger i `:core`.

Behörigheter i `AndroidManifest.xml`: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`RECEIVE_BOOT_COMPLETED`.

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
Håll `requestCode` unik per larm (jfr `REQUEST_CODE_MED_BASE + slot`) — kolliderande
koder skriver över varandra.

### Återskapa efter omstart (NOT-6)
Larm överlever **inte** omstart. `BootReceiver` (`@AndroidEntryPoint`) måste anropa
`alarmScheduler.rescheduleAll()` via `goAsync()` + coroutine så att det hinner klart.

### Schemalägg om vid ändring (NOT-7)
Varje ändring som påverkar tider/på-av (inställningar, recept, vid behov-mediciner) – och ny
data från servern – ska följas av `rescheduleAll()` (eller riktad `scheduleX/cancelAllX`).
Lämna aldrig gamla larm kvar.

### Vilka larm som skapas
Endast aktiverade händelser schemaläggs (`if (config.enabled)`). Endast ej tagna/ej
skippade mediciner ska generera notis (NOT-3) — den logiken hör hemma i receivern.
Screeninglarm som passerat dagens tid rullar till nästa dag (NOT-5) — se
`screeningAlarmTriggerMs`/`medAlarmTriggerMs`.

## Tester (regel 2)

- **Trigger-tidsberäkningen är ren och injicerbar** (`now: LocalDateTime = now()`).
  Enhetstesta `screeningAlarmTriggerMs`/`medAlarmTriggerMs`: framtida tid idag, passerad
  tid → nästa dag, midnattsvridning (`00:00 − 15 min = 23:45`), lead-minuter.
- Enhetstesta schemaläggaren med en mockad `AlarmManager` och repositories byggda på
  `FakeCollection` (skill `testing-strategy`): rätt antal larm vid `enabled`/`disabled`, att
  `cancelAll*` anropas före omschemaläggning, och att en ändring i cachen ger ny schemaläggning.
- "Markera tagen" testas mot `FakeCollection`: dosens `status` blir `taken` och `takenAt` sätts.
- Receiver-/tillståndsbeteende är svårt att enhetstesta — håll logiken i scheduler/helper
  och testa den.

## Vanliga fallgropar

- Glömt `...AndAllowWhileIdle` → larm tappas i Doze.
- Glömt `FLAG_IMMUTABLE` → krasch på API 31+.
- Antar att `POST_NOTIFICATIONS` finns → notis försvinner tyst.
- Schemalägger nytt utan att avboka gammalt → dubblerade/föräldralösa larm.
- Hårdkodad notistext i stället för `strings.xml` (UI-text ska vara svensk).
- Hälsoinnehåll synligt på låsskärmen – notiser är privata (skill `data-privacy-security`).
- Egen Firestore-läsning i en receiver i stället för repositoryt – eller att vänta på servern.
