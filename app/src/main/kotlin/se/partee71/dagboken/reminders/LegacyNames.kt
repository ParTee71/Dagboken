package se.partee71.dagboken.reminders

// Klassnamnen från före paketbytet (#418) – kan tas bort när ingen telefon har en version före 4.12.4.
// Larm i AlarmManager och notisknappar ("Markera tagen") som lades före uppdateringen pekar explicit på de gamla
// komponentnamnen; systemet behåller dem vid uppdateringen. Utan de här namnen i manifestet når de ingenting.
// Varje klass är bara ett namn: allt beteende, och Hilts injektion, ärvs från mottagaren i se.kodhast.

/** Gammalt namn på [se.kodhast.dagboken.reminders.MedAlarmReceiver]. */
class MedAlarmReceiver : se.kodhast.dagboken.reminders.MedAlarmReceiver()

/** Gammalt namn på [se.kodhast.dagboken.reminders.ScreeningReminderReceiver]. */
class ScreeningReminderReceiver : se.kodhast.dagboken.reminders.ScreeningReminderReceiver()

/** Gammalt namn på [se.kodhast.dagboken.reminders.PeriodReminderReceiver]. */
class PeriodReminderReceiver : se.kodhast.dagboken.reminders.PeriodReminderReceiver()

/** Gammalt namn på [se.kodhast.dagboken.reminders.MedActionReceiver]. */
class MedActionReceiver : se.kodhast.dagboken.reminders.MedActionReceiver()
