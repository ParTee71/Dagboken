package se.partee71.dagboken.core.schema

import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SlotReminder
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings

/**
 * `settings/app` (DAT-11): grupperna `theme`, `reminders` och `profile` som nästlade objekt, så att
 * en merge-skrivning av en grupp lämnar okända fält i de andra orörda. Påminnelseraderna lagras
 * med sin nyckel (`slot`, `occasion`) och läses på nyckeln, inte på position – en rad som saknas
 * får sitt standardvärde.
 */
object SettingsCodec : DocCodec<Settings> {
    const val THEME = "theme"
    const val REMINDERS = "reminders"
    const val PROFILE = "profile"

    override fun encode(value: Settings): Doc = mapOf(
        THEME to value.theme.let {
            mapOf(
                "mode" to it.mode.encodeWire(),
                "lightStartHour" to it.lightStartHour,
                "darkStartHour" to it.darkStartHour,
                "isDarkTheme" to it.isDarkTheme,
            )
        },
        REMINDERS to value.reminders.let { r ->
            mapOf(
                "medsEnabled" to r.medsEnabled,
                "medSlots" to r.medSlots.map { mapOf("slot" to it.slot.encodeWire(), ENABLED to it.enabled, TIME to it.time.encodeTime()) },
                "screeningOccasions" to r.screeningOccasions.map {
                    mapOf("occasion" to it.occasion.encodeWire(), ENABLED to it.enabled, TIME to it.time.encodeTime())
                },
                "periodReminderTime" to r.periodReminderTime.encodeTime(),
            )
        },
        PROFILE to value.profile.let { mapOf("birthYear" to it.birthYear, "sex" to it.sex.encodeWire()) },
    )

    override fun decode(id: String, map: Doc): Settings {
        val theme = asDoc(map[THEME])
        val reminders = asDoc(map[REMINDERS])
        val profile = asDoc(map[PROFILE])
        val defaults = ReminderSettings()
        val medSlots = reminders.docs("medSlots")
        val occasions = reminders.docs("screeningOccasions")
        return Settings(
            id = id,
            theme = ThemeSettings(
                mode = theme.wire("mode", ThemeMode.AUTO),
                lightStartHour = theme.int("lightStartHour", ThemeSettings().lightStartHour),
                darkStartHour = theme.int("darkStartHour", ThemeSettings().darkStartHour),
                isDarkTheme = theme.bool("isDarkTheme", ThemeSettings().isDarkTheme),
            ),
            reminders = ReminderSettings(
                medsEnabled = reminders.bool("medsEnabled", defaults.medsEnabled),
                medSlots = Slot.SCHEDULED.map { slot ->
                    val row = medSlots.firstOrNull { it.wireOrNull<Slot>("slot") == slot }
                    val default = SlotReminder(slot)
                    SlotReminder(slot, row?.bool(ENABLED, default.enabled) ?: default.enabled, row?.localTime(TIME) ?: default.time)
                },
                screeningOccasions = Occasion.entries.map { occasion ->
                    val row = occasions.firstOrNull { it.wireOrNull<Occasion>("occasion") == occasion }
                    val default = OccasionReminder(occasion)
                    OccasionReminder(occasion, row?.bool(ENABLED, default.enabled) ?: default.enabled, row?.localTime(TIME) ?: default.time)
                },
                periodReminderTime = reminders.localTime("periodReminderTime") ?: defaults.periodReminderTime,
            ),
            profile = Profile(birthYear = profile.intOrNull("birthYear"), sex = profile.wire("sex", Sex.UNSPECIFIED)),
        )
    }

    private const val ENABLED = "enabled"
}
