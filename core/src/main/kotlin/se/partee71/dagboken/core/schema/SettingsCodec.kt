package se.partee71.dagboken.core.schema

import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.LegacySettings
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
import se.partee71.dagboken.core.model.WireEnum

/**
 * `settings/app` (DAT-11): grupperna `theme`, `reminders`, `profile` och `legacy` som nästlade
 * objekt, så att en merge-skrivning av en grupp lämnar okända fält i de andra orörda. Påminnelseraderna
 * lagras med sin nyckel (`slot`, `occasion`) och läses på nyckeln, inte på position – en rad som
 * saknas får sitt standardvärde (en codec för båda radtyperna: [ReminderRows]).
 */
object SettingsCodec : DocCodec<Settings> {
    const val THEME = "theme"
    const val REMINDERS = "reminders"
    const val PROFILE = "profile"
    const val LEGACY = "legacy"

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
                MED_SLOTS to slotRows.encode(r.medSlots),
                SCREENING_OCCASIONS to occasionRows.encode(r.screeningOccasions),
                "periodReminderTime" to r.periodReminderTime.encodeTime(),
            )
        },
        PROFILE to value.profile.let { mapOf("birthYear" to it.birthYear, "sex" to it.sex.encodeWire()) },
        LEGACY to value.legacy.let { mapOf(DYNAMIC_COLOR to it.dynamicColor, SHEETS_CONFIG to it.sheetsConfig) },
    )

    override fun decode(id: String, map: Doc): Settings {
        val theme = asDoc(map[THEME])
        val reminders = asDoc(map[REMINDERS])
        val profile = asDoc(map[PROFILE])
        val legacy = asDoc(map[LEGACY])
        val defaults = ReminderSettings()
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
                medSlots = slotRows.decode(reminders.docs(MED_SLOTS)),
                screeningOccasions = occasionRows.decode(reminders.docs(SCREENING_OCCASIONS)),
                periodReminderTime = reminders.localTime("periodReminderTime") ?: defaults.periodReminderTime,
            ),
            profile = Profile(birthYear = profile.intOrNull("birthYear"), sex = profile.wire("sex", Sex.UNSPECIFIED)),
            legacy = LegacySettings(dynamicColor = legacy.boolOrNull(DYNAMIC_COLOR), sheetsConfig = legacy.stringOrNull(SHEETS_CONFIG)),
        )
    }

    private const val MED_SLOTS = "medSlots"
    private const val SCREENING_OCCASIONS = "screeningOccasions"
    private const val DYNAMIC_COLOR = "dynamicColor"
    private const val SHEETS_CONFIG = "sheetsConfig"

    private val slotRows = ReminderRows(
        key = "slot",
        keys = Slot.SCHEDULED,
        default = { SlotReminder(it) },
        row = ::SlotReminder,
        parts = { Triple(it.slot, it.enabled, it.time) },
    )

    private val occasionRows = ReminderRows(
        key = "occasion",
        keys = Occasion.entries,
        default = { OccasionReminder(it) },
        row = ::OccasionReminder,
        parts = { Triple(it.occasion, it.enabled, it.time) },
    )
}

/**
 * En lista påminnelserader `{<key>, enabled, time}` – en rad per nyckel i [keys], i den ordningen.
 * Raderna läses på nyckeln, inte på position; en rad som saknas eller har en okänd nyckel (från en
 * nyare app) hoppas över och nyckeln får [default], och ett trasigt fält i en rad får standardvärdet
 * för just det fältet. Delas av `medSlots` och `screeningOccasions` (regel 4).
 */
private class ReminderRows<K : WireEnum, R>(
    private val key: String,
    private val keys: List<K>,
    private val default: (K) -> R,
    private val row: (K, Boolean, LocalTime) -> R,
    private val parts: (R) -> Triple<K, Boolean, LocalTime>,
) {
    fun encode(rows: List<R>): List<Doc> = rows.map(parts).map { (k, enabled, time) ->
        mapOf(key to k.encodeWire(), ENABLED to enabled, TIME to time.encodeTime())
    }

    fun decode(stored: List<Doc>): List<R> = keys.map { k ->
        val (_, enabled, time) = parts(default(k))
        val found = stored.firstOrNull { it[key] == k.wire }
        row(k, found?.bool(ENABLED, enabled) ?: enabled, found?.localTime(TIME) ?: time)
    }

    private companion object {
        const val ENABLED = "enabled"
    }
}
