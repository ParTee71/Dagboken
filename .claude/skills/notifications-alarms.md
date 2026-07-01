---
name: notifications-alarms
description: Dagbokens påminnelse- och larmsystem (medicin + screening). Ladda denna ALLTID när du rör notifikationer, larm, schemaläggning eller bakgrundsväckning. Trigger-ord: notifikation, notification, påminnelse, larm, alarm, AlarmManager, AlarmScheduler, NotificationHelper, NotificationChannel, PendingIntent, BroadcastReceiver, BootReceiver, MedAlarmReceiver, ScreeningReminderReceiver, SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS, BOOT_COMPLETED, exakt larm, Doze, schemalägg.
---

# Notifikationer & larm

Androids mest felbenägna yta — exakta larm, körningstillstånd och väckning efter
omstart skiljer sig kraftigt mellan API-nivåer. Projektet kör **minSdk 30, targetSdk 35**,
så alla moderna restriktioner gäller. Krav: **NOT-1…8**.

## Var koden bor

| Fil | Ansvar |
|---|---|
| `notifications/AlarmScheduler.kt` | `@Singleton`, schemalägger/avbryter medicin- och screeninglarm. Single source för all larmlogik. |
| `notifications/NotificationHelper.kt` | Skapar kanaler (`CHANNEL_MEDS` default, `CHANNEL_SCREENING` low) och postar notiser. |
| `notifications/MedAlarmReceiver.kt` · `ScreeningReminderReceiver.kt` | `BroadcastReceiver` som tar emot larm och postar notis. |
| `notifications/BootReceiver.kt` | Återskapar alla larm efter omstart (`ACTION_BOOT_COMPLETED`). |

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
Varje ändring som påverkar tider/på-av (inställningar, recept, favoriter) ska följas av
`rescheduleAll()` (eller riktad `scheduleX/cancelAllX`). Lämna aldrig gamla larm kvar.

### Vilka larm som skapas
Endast aktiverade händelser schemaläggs (`if (config.enabled)`). Endast ej tagna/ej
skippade mediciner ska generera notis (NOT-3) — den logiken hör hemma i receivern.
Screeninglarm som passerat dagens tid rullar till nästa dag (NOT-5) — se
`screeningAlarmTriggerMs`/`medAlarmTriggerMs`.

## Tester (regel 2)

- **Trigger-tidsberäkningen är ren och injicerbar** (`now: LocalDateTime = now()`).
  Enhetstesta `screeningAlarmTriggerMs`/`medAlarmTriggerMs`: framtida tid idag, passerad
  tid → nästa dag, midnattsvridning (`00:00 − 15 min = 23:45`), lead-minuter.
- Enhetstesta `AlarmScheduler` med en mockad `AlarmManager`/`PreferencesRepository`:
  rätt antal larm vid `enabled`/`disabled`, att `cancelAll*` anropas före omschemaläggning.
- Receiver-/tillståndsbeteende är svårt att enhetstesta — håll logiken i scheduler/helper
  och testa den.

## Vanliga fallgropar

- Glömt `...AndAllowWhileIdle` → larm tappas i Doze.
- Glömt `FLAG_IMMUTABLE` → krasch på API 31+.
- Antar att `POST_NOTIFICATIONS` finns → notis försvinner tyst.
- Schemalägger nytt utan att avboka gammalt → dubblerade/föräldralösa larm.
- Hårdkodad notistext i stället för `strings.xml` (se skill `localization-strings` om den finns; UI-text ska vara svensk).
