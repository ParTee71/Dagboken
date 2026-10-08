package se.partee71.dagboken.core.legacy

import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import se.partee71.dagboken.core.model.Activity
import se.partee71.dagboken.core.model.Boost
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.Dose
import se.partee71.dagboken.core.model.DoseStatus
import se.partee71.dagboken.core.model.Event
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.LegacySettings
import se.partee71.dagboken.core.model.Occasion
import se.partee71.dagboken.core.model.OccasionReminder
import se.partee71.dagboken.core.model.Option
import se.partee71.dagboken.core.model.OptionIds
import se.partee71.dagboken.core.model.OptionKind
import se.partee71.dagboken.core.model.Period
import se.partee71.dagboken.core.model.Prescription
import se.partee71.dagboken.core.model.PrnMedicine
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.ReminderSettings
import se.partee71.dagboken.core.model.Repeat
import se.partee71.dagboken.core.model.Schedule
import se.partee71.dagboken.core.model.Screening
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.SlotReminder
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.core.model.somatic
import se.partee71.dagboken.core.schema.ActivityCodec
import se.partee71.dagboken.core.schema.CheckinCodec
import se.partee71.dagboken.core.schema.CollectionNames
import se.partee71.dagboken.core.schema.Doc
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.DocumentRules
import se.partee71.dagboken.core.schema.DoseCodec
import se.partee71.dagboken.core.schema.EventCodec
import se.partee71.dagboken.core.schema.ExportFormat
import se.partee71.dagboken.core.schema.IllnessEpisodeCodec
import se.partee71.dagboken.core.schema.OptionCodec
import se.partee71.dagboken.core.schema.PrescriptionCodec
import se.partee71.dagboken.core.schema.PrnMedicineCodec
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.core.schema.ScreeningCodec
import se.partee71.dagboken.core.schema.SettingsCodec
import se.partee71.dagboken.core.schema.asDoc
import se.partee71.dagboken.core.schema.parseClock
import se.partee71.dagboken.core.schema.parseDate
import se.partee71.dagboken.core.schema.wireValue

/**
 * 3.x [BackupJson] (v1 och v2) → 4.0-dokument under `users/{uid}` (ARKITEKTUR.md → Fältparitet och
 * Migrering, punkt 1; OMB-3). **Enda** mappningen från 3.x: legacy-läsaren (OMB-2), legacyimporten
 * (BCK-14) och grinden OMB-4 kör alla den här.
 *
 * - Deterministisk: ingen klocka, dokumenten sorterade på sökväg, alternativ-id via [OptionIds.of].
 * - Id:n bevaras (DAT-13); receptdosernas `recept_…`-id:n oförändrade (DAT-8).
 * - Varje genererat dokument valideras mot [DocumentRules] (= `firestore.rules` och `TextLimits`); ryms
 *   något inte, eller saknar något sin plats, blir utfallet [ConversionResult.Stopped] med **alla** fel –
 *   inget värde kapas, avrundas eller hoppas över.
 * - Rapporten nämner sökvägar, fält, längder, tal och enum-namn – aldrig textinnehåll (hälsodata).
 */
object BackupJsonConverter {
    fun convert(backup: BackupJson, uid: String): ConversionResult = Conversion(backup, uid).run()

    /** Backupfilens `createdAt` som ögonblick för exportfilens `exportedAt`; saknas den: epoken (aldrig "nu"). */
    fun exportedAt(backup: BackupJson): Instant = sourceCreatedAt(backup) ?: Instant.fromEpochSeconds(0)

    /** Backupfilens `createdAt` som ögonblick (migreringsmarkörens `sourceCreatedAt`); `null` när den saknas eller är ogiltig. */
    fun sourceCreatedAt(backup: BackupJson): Instant? = LegacyTime.backupCreatedAt(backup.createdAt)
}

private class Conversion(private val backup: BackupJson, private val uid: String) {
    private val problems = mutableListOf<Problem>()
    private val warnings = mutableListOf<Warning>()
    private val options = OptionRegistry(backup, ::warn)
    private val notes = NoteIndex(backup.notes, ::warn)
    private val documents = mutableListOf<ExportFormat.Document>()
    private val paths = mutableSetOf<String>()

    /** Måendepåminnelsernas tider ur backupen (eller standardtiderna) – styr [Occasion.derive] (DAT-12). */
    private val occasionReminders: List<OccasionReminder> = occasionRows(backup.screeningEventConfigs)
    private val reminderTimes: Map<Occasion, LocalTime> = occasionReminders.associate { it.occasion to it.time }

    fun run(): ConversionResult {
        val settings = settings()
        val prescriptions = backup.medicinRecipes.map(::prescription)
        val prnMedicines = backup.medicinFavoriter.map(::prnMedicine)
        val doses = backup.mediciner.map(::dose)
        val (screeningRows, activityRows) = backup.aktiviteter.partition { it.type.ifBlank { inferType(it.aktivitet) } == LegacyDefaults.TYPE_SCREENING }
        val screenings = screeningRows.map(::screening)
        val activities = activityRows.map(::activity)
        val events = backup.handelser.map(::event)
        val episodes = backup.sjukdomsepisoder.map(::episode)
        val episodeIds = episodes.map { it.id }.toSet()
        val checkins = backup.sjukdomsIncheckningar.filter { checkinHasEpisode(it, episodeIds) }
            .groupBy({ it.episodId }, ::checkin)
        notes.orphans().forEach { (target, count) -> warn("notes", "$count anteckning(ar) med target ${targetLabel(target)} utan sin post – kan inte placeras") }
        if (notes.incomplete > 0) warn("notes", "${notes.incomplete} anteckning(ar) utan target eller id – kan inte placeras")

        add(CollectionNames.user(uid), CollectionNames.USERS, CollectionNames.USERS, mapOf(SCHEMA_VERSION to Schema.CURRENT_VERSION))
        settings?.let { (_, doc) -> add(CollectionNames.document(uid, CollectionNames.SETTINGS, Settings.ID), SETTINGS_PATH, CollectionNames.SETTINGS, doc) }
        // Alternativens id bär namnet (slug) – rapporten pekar på lista och plats i stället.
        addAll(CollectionNames.OPTIONS, options.all(), OptionCodec) { OptionRegistry.reportPath(it.kind, it.sortOrder) }
        addAll(CollectionNames.PRESCRIPTIONS, prescriptions, PrescriptionCodec)
        addAll(CollectionNames.PRN_MEDICINES, prnMedicines, PrnMedicineCodec)
        addAll(CollectionNames.DOSES, doses, DoseCodec)
        addAll(CollectionNames.SCREENINGS, screenings, ScreeningCodec)
        addAll(CollectionNames.ACTIVITIES, activities, ActivityCodec)
        addAll(CollectionNames.EVENTS, events, EventCodec)
        addAll(CollectionNames.ILLNESS_EPISODES, episodes, IllnessEpisodeCodec)
        for ((episodeId, rows) in checkins) {
            for (checkin in rows) {
                add(CollectionNames.checkin(uid, episodeId, checkin.id), checkinPath(episodeId, checkin.id), CollectionNames.CHECKINS, CheckinCodec.encode(checkin))
            }
        }
        documents.sortBy { it.path }

        val counts = documents.groupingBy { CollectionNames.collectionOf(it.path) }.eachCount()
        val report = ConversionReport(
            formatVersion = backup.version,
            createdAt = backup.createdAt,
            counts = CollectionNames.ALL.filter { it in counts }.associateWith { counts.getValue(it) },
            warnings = warnings.toList(),
            problems = problems.toList(),
        )
        if (report.stopped) return ConversionResult.Stopped(report)
        val data = ConvertedData(settings?.first, options.all(), prescriptions, prnMedicines, doses, screenings, activities, events, episodes, checkins)
        return ConversionResult.Converted(data, documents.toList(), report)
    }

    // ── Dokument ─────────────────────────────────────────────────────────────────────────────────

    /** [reportPath] är sökvägen i rapporten – 3.x-id:n (UUID, `recept_…`) får stå, annat ersätts. */
    private fun <T : Identified> addAll(collection: String, items: List<T>, codec: DocCodec<T>, reportPath: (T) -> String = { entityPath(collection, it.id) }) {
        for (item in items) add(CollectionNames.document(uid, collection, item.id), reportPath(item), collection, codec.encode(item))
    }

    /** Lägger till ett dokument efter dubblettkontroll och validering mot rules-gränserna. */
    private fun add(path: String, reportPath: String, collection: String, doc: Doc) {
        if (!paths.add(path)) {
            problem(reportPath, "id", "dubblett: två poster med samma id")
            return
        }
        for (violation in DocumentRules.validate(collection, doc)) problem(reportPath, violation.field, violation.reason)
        documents += ExportFormat.Document(path, doc)
    }

    /**
     * Postens sökväg i rapporten: 3.x-id:t när det duger som dokument-id (UUID eller `recept_…`), annars
     * dess längd – ett ogiltigt id kan vara vad som helst. Rapporterar det ogiltiga id:t en gång per post.
     */
    private fun entityPath(collection: String, id: String): String = "$collection/${idLabel(id)}"

    private fun checkinPath(episodeId: String, id: String) = entityPath("${entityPath(CollectionNames.ILLNESS_EPISODES, episodeId)}/${CollectionNames.CHECKINS}", id)

    // ── Inställningar ────────────────────────────────────────────────────────────────────────────

    /**
     * `settings/app`: modellen med 3.x-defaults där backupen saknade värdet, och dokumentet med **bara**
     * de fält backupen hade (`null` = "rör inte", merge). `null` när backupen inte bar några inställningar.
     */
    private fun settings(): Pair<Settings, Doc>? {
        val path = SETTINGS_PATH
        val present = mutableSetOf<String>()
        fun <T> field(fieldPath: String, value: T?, apply: (T) -> Unit) {
            if (value != null) {
                apply(value)
                present += fieldPath
            }
        }
        val s = backup.settings
        var theme = ThemeSettings()
        field("theme.mode", s?.themeMode) { theme = theme.copy(mode = themeMode(path, it)) }
        field("theme.lightStartHour", s?.themeLightStart) { theme = theme.copy(lightStartHour = it) }
        field("theme.darkStartHour", s?.themeDarkStart) { theme = theme.copy(darkStartHour = it) }
        field("theme.isDarkTheme", s?.isDarkTheme) { theme = theme.copy(isDarkTheme = it) }
        var reminders = ReminderSettings()
        field("reminders.medsEnabled", s?.medsNotificationsEnabled) { reminders = reminders.copy(medsEnabled = it) }
        field("reminders.medSlots", backup.medNotificationConfigs) { reminders = reminders.copy(medSlots = medSlots(it)) }
        field("reminders.screeningOccasions", backup.screeningEventConfigs) { reminders = reminders.copy(screeningOccasions = occasionReminders) }
        field("reminders.periodReminderTime", backup.periodReminderTime?.takeIf { it.isNotBlank() }) {
            reminders = reminders.copy(periodReminderTime = clock(path, "periodReminderTime", it) ?: reminders.periodReminderTime)
        }
        var profile = Profile()
        field("profile.birthYear", s?.birthYear) { profile = profile.copy(birthYear = it) }
        field("profile.sex", s?.sex) { profile = profile.copy(sex = sex(path, it)) }
        var legacy = LegacySettings()
        field("legacy.dynamicColor", s?.dynamicColor) { legacy = legacy.copy(dynamicColor = it) }
        field("legacy.sheetsConfig", backup.sheetsConfig) { legacy = legacy.copy(sheetsConfig = it) }
        if (present.isEmpty()) return null
        val settings = Settings(theme = theme, reminders = reminders, profile = profile, legacy = legacy)
        return settings to prune(SettingsCodec.encode(settings), present)
    }

    /** Behåller bara fältvägarna i [present]; grupper utan kvarvarande fält faller bort. */
    private fun prune(doc: Doc, present: Set<String>, prefix: String = ""): Doc = doc.mapNotNull { (key, value) ->
        val path = prefix + key
        when {
            path in present -> key to value
            value is Map<*, *> -> prune(asDoc(value), present, "$path.").takeIf { it.isNotEmpty() }?.let { key to it }
            else -> null
        }
    }.toMap()

    private fun themeMode(path: String, raw: String): ThemeMode =
        wireValue<ThemeMode>(raw) ?: ThemeMode.AUTO.also { problem(path, "themeMode", unknown(raw)) }

    private fun sex(path: String, raw: String): Sex = when (raw) {
        LegacyDefaults.SEX_MALE -> Sex.MALE
        LegacyDefaults.SEX_FEMALE -> Sex.FEMALE
        LegacyDefaults.SEX_UNSPECIFIED -> Sex.UNSPECIFIED
        else -> Sex.UNSPECIFIED.also { problem(path, "sex", unknown(raw)) }
    }

    /**
     * Medicinpåminnelserna som 3.x `BackupMapper.toMedNotificationConfigs`: en rad per schemalagd
     * tidpunkt, matchad på namnet och annars på positionen (bara rader utan namn); saknad rad får
     * standardvärdet. En rad som inte hör till någon tidpunkt har ingen plats och stoppar.
     */
    private fun medSlots(configs: List<MedNotificationConfigJson>): List<SlotReminder> {
        val path = SETTINGS_PATH
        val used = mutableSetOf<Int>()
        val rows = Slot.SCHEDULED.mapIndexed { index, slot ->
            val match = configs.indexOfFirst { it.tidpunkt == slot.legacyName }.takeIf { it >= 0 }
                ?: index.takeIf { configs.getOrNull(it)?.tidpunkt?.isBlank() == true }
            val default = SlotReminder(slot)
            val config = match?.also(used::add)?.let(configs::get)
            SlotReminder(
                slot,
                enabled = config?.enabled ?: default.enabled,
                time = config?.time?.takeIf { it.isNotBlank() }?.let { clock(path, "medNotificationConfigs[$match].time", it) } ?: default.time,
            )
        }
        configs.indices.filterNot { it in used }.forEach { problem(path, "medNotificationConfigs[$it].tidpunkt", "okänd tidpunkt ${unknown(configs[it].tidpunkt)}") }
        return rows
    }

    /** Måendepåminnelserna: position 0–3 = tillfällena i ordning; fler än fyra har ingen plats och stoppar. */
    private fun occasionRows(configs: List<ScreeningEventConfigJson>?): List<OccasionReminder> {
        val path = SETTINGS_PATH
        if (configs != null && configs.size > Occasion.entries.size) {
            problem(path, "screeningEventConfigs", "fler tillfällen än de fyra: ${configs.size}")
        }
        return Occasion.entries.mapIndexed { index, occasion ->
            val default = OccasionReminder(occasion)
            val config = configs?.getOrNull(index)
            OccasionReminder(
                occasion,
                enabled = config?.enabled ?: default.enabled,
                time = config?.time?.takeIf { it.isNotBlank() }?.let { clock(path, "screeningEventConfigs[$index].time", it) } ?: default.time,
            )
        }
    }

    // ── Mediciner ────────────────────────────────────────────────────────────────────────────────

    private fun prescription(r: ReceptJson): Prescription {
        val path = entityPath(CollectionNames.PRESCRIPTIONS, r.id)
        id(path, r.id)
        // v1 hade en enda `tidpunkt`; båda tomma → Morgon, som 3.x BackupMapper.
        val names = r.tidpunkter.ifEmpty { listOfNotNull(r.tidpunkt?.takeIf { it.isNotBlank() }).ifEmpty { listOf(Slot.MORNING.legacyName) } }
        return Prescription(
            id = r.id,
            name = r.namn,
            dose = r.dos,
            unit = r.enhet,
            slots = names.map { slot(path, "tidpunkter", it) },
            schedule = Schedule.Repeating(repeat(path, r.upprepning), r.dagar.mapNotNull { weekday(path, it) }.toSet(), r.intervalDagar),
            period = Period(start = date(path, "startDatum", r.startDatum), end = date(path, "slutDatum", r.slutDatum.orEmpty())),
            boosts = r.dosperioder.map {
                Boost(it.id, date(path, "dosperioder.startDatum", it.startDatum), date(path, "dosperioder.slutDatum", it.slutDatum.orEmpty()), it.dos, it.enhet)
            },
            active = r.aktiv,
            createdAt = date(path, "skapad", r.skapad)?.let(LegacyTime::midnight),
            note = notes.note(LegacyDefaults.NOTE_PRESCRIPTION, r.id, r.anteckning, path),
        )
    }

    /** 3.x `Upprepning.fromString` med synonymerna; okänt → dagligen (som 3.x), men med varning. */
    private fun repeat(path: String, raw: String): Repeat = when (raw.lowercase()) {
        "", "dagligen" -> Repeat.DAILY
        "vardagar" -> Repeat.WEEKDAYS
        "helger" -> Repeat.WEEKENDS
        "anpassad", "specifika dagar" -> Repeat.CUSTOM
        "intervall", "var x:e dag" -> Repeat.INTERVAL
        else -> Repeat.DAILY.also { warn(path, "okänd upprepning (${raw.length} tecken) – dagligen, som i 3.x") }
    }

    /** 3.x 0 = måndag … 6 = söndag → ISO; annat tal har ingen veckodag och stoppar. */
    private fun weekday(path: String, day: Int): DayOfWeek? =
        if (day in 0..6) DayOfWeek(day + 1) else null.also { problem(path, "dagar", "ingen veckodag: $day (0–6)") }

    private fun prnMedicine(f: FavoritJson): PrnMedicine {
        val path = entityPath(CollectionNames.PRN_MEDICINES, f.id)
        id(path, f.id)
        return PrnMedicine(
            id = f.id,
            name = f.namn,
            dose = f.dos,
            unit = f.enhet,
            slot = slot(path, "tidpunkt", f.tidpunkt),
            minHoursBetween = f.minTidMellan,
            dispensingTime = f.dispenseringsTid.ifBlank { null },
            maxPerDay = f.maxDoserPerDag,
            favorite = f.isFavorite,
            note = notes.note(LegacyDefaults.NOTE_PRN, f.id, f.anteckning, path),
        )
    }

    private fun dose(m: MedicinJson): Dose {
        val path = entityPath(CollectionNames.DOSES, m.id)
        id(path, m.id)
        val date = date(path, "datum", m.datum)
        val time = clock(path, "tid", m.tid)
        val takenTime = m.tagenTid?.takeIf { it.isNotBlank() }?.let { clock(path, "tagenTid", it) }
        if (takenTime != null && date == null) problem(path, "tagenTid", "kan inte placeras på en dag: datum saknas")
        // En tagningstid långt före det schemalagda klockslaget är troligen fel dag i 3.x; värdet bevaras, men syns i rapporten.
        if (takenTime != null && time != null && time.toSecondOfDay() - takenTime.toSecondOfDay() > TAKEN_EARLY_SECONDS) {
            warn(path, "tagenTid ligger mer än ${TAKEN_EARLY_SECONDS / SECONDS_PER_HOUR} timmar före tid – bevaras som den är")
        }
        return Dose(
            id = m.id,
            date = date,
            slot = slot(path, "tidpunkt", m.tidpunkt),
            name = m.namn,
            dose = m.dos,
            unit = m.enhet,
            // Båda sanna är ogiltigt i 3.x; tagen går före.
            status = if (m.tagen) DoseStatus.TAKEN else if (m.skipped) DoseStatus.SKIPPED else DoseStatus.PLANNED,
            plannedTime = time,
            takenAt = if (takenTime != null && date != null) at(path, "tagenTid", date, takenTime) else null,
            prescriptionId = m.receptId,
            createdAt = createdAt(path, m.timestamp, date, time),
            note = notes.note(LegacyDefaults.NOTE_MEDICATION, m.id, m.anteckning, path),
        )
    }

    // ── Poster ───────────────────────────────────────────────────────────────────────────────────

    /** 3.x `BackupMapper.inferType`: tomt `type` är en screening om namnet är ett av tillfällena. */
    private fun inferType(name: String): String = if (Occasion.isLegacyName(name)) LegacyDefaults.TYPE_SCREENING else "aktivitet"

    private fun screening(a: AktivitetJson): Screening {
        val path = entityPath(CollectionNames.SCREENINGS, a.id)
        id(path, a.id)
        val date = date(path, "datum", a.datum)
        val time = clock(path, "tid", a.tid)
        // Alltid false respektive null på en 3.x-screening; ett annat värde har ingen plats och stoppar.
        if (a.aterhamtande) problem(path, "aterhamtande", "true på en screening har ingen plats i 4.0")
        if (a.energitjuv) problem(path, "energitjuv", "true på en screening har ingen plats i 4.0")
        if (a.spentTime != null) problem(path, "spentTime", "värdet ${a.spentTime} på en screening har ingen plats i 4.0")
        return Screening(
            id = a.id,
            date = date,
            time = time,
            occasion = Occasion.derive(a.aktivitet, time, reminderTimes),
            customText = if (Occasion.isLegacyName(a.aktivitet)) null else a.aktivitet.ifBlank { null },
            energy = a.energy,
            stress = a.stress,
            symptoms = symptoms(path, a.symptom, a.somatiska),
            createdAt = createdAt(path, a.timestamp, date, time),
            note = notes.note(LegacyDefaults.NOTE_SCREENING, a.id, "", path),
        )
    }

    private fun activity(a: AktivitetJson): Activity {
        val path = entityPath(CollectionNames.ACTIVITIES, a.id)
        id(path, a.id)
        val date = date(path, "datum", a.datum)
        val time = clock(path, "tid", a.tid)
        // Namn bland alternativen → optionId; annat namn är fritexten vid "Övrigt" (AKT-2).
        val option = options.find(OptionKind.ACTIVITY, a.aktivitet)
        // Saknat `type` är "aktivitet" i 3.x (klassens default) – bara ett tomt härleds ur namnet.
        if (Occasion.isLegacyName(a.aktivitet)) warn(path, "heter som ett måendetillfälle men har type aktivitet – konverteras som aktivitet, som i 3.x")
        return Activity(
            id = a.id,
            date = date,
            time = time,
            optionId = (option ?: options.other(OptionKind.ACTIVITY)).id,
            customText = if (option == null) a.aktivitet.ifBlank { null } else null,
            energy = a.energy,
            stress = a.stress,
            symptoms = symptoms(path, a.symptom, a.somatiska),
            recovering = a.aterhamtande,
            drain = a.energitjuv,
            minutes = a.spentTime,
            createdAt = createdAt(path, a.timestamp, date, time),
            note = notes.note(LegacyDefaults.NOTE_ACTIVITY, a.id, "", path),
        )
    }

    private fun event(h: HandelseJson): Event {
        val path = entityPath(CollectionNames.EVENTS, h.id)
        id(path, h.id)
        val date = date(path, "datum", h.datum)
        val time = clock(path, "tid", h.tid)
        return Event(
            id = h.id,
            date = date,
            time = time,
            optionId = if (h.typ.isBlank()) "".also { warn(path, "händelsen saknar typ") } else options.findOrArchived(OptionKind.EVENT, h.typ).id,
            severity = h.svarighetsgrad,
            durationMinutes = h.varaktighetMinuter,
            triggers = h.triggers.ifBlank { null },
            actions = h.atgarder.ifBlank { null },
            createdAt = createdAt(path, h.timestamp, date, time),
            note = notes.note(LegacyDefaults.NOTE_EVENT, h.id, h.anteckning, path),
        )
    }

    private fun episode(e: SjukdomsEpisodJson): IllnessEpisode {
        val path = entityPath(CollectionNames.ILLNESS_EPISODES, e.id)
        id(path, e.id)
        return IllnessEpisode(
            id = e.id,
            type = e.typ,
            start = date(path, "startDatum", e.startDatum),
            end = date(path, "slutDatum", e.slutDatum),
            createdAt = LegacyTime.epochMillis(e.timestamp),
            note = notes.note(LegacyDefaults.NOTE_EPISODE, e.id, e.anteckning, path),
        )
    }

    /** En incheckning utan sin episod har ingen sökväg (3.x hade en främmande nyckel) och stoppar. */
    private fun checkinHasEpisode(i: SjukdomsIncheckningJson, episodeIds: Set<String>): Boolean =
        (i.episodId in episodeIds).also { found ->
            if (!found) problem(checkinPath(i.episodId, i.id), "episodId", "episoden finns inte i backupen")
        }

    private fun checkin(i: SjukdomsIncheckningJson): Checkin {
        val path = checkinPath(i.episodId, i.id)
        id(path, i.id)
        val date = date(path, "datum", i.datum)
        val time = clock(path, "tid", i.tid)
        return Checkin(
            id = i.id,
            date = date,
            time = time,
            severity = i.svarighetsgrad,
            symptoms = symptoms(path, i.symptom, i.somatiska),
            createdAt = LegacyTime.epochMillis(i.timestamp),
            note = notes.note(LegacyDefaults.NOTE_CHECKIN, i.id, i.anteckning, path),
        )
    }

    /**
     * 3.x wire-formatet `Namn:Poäng,…` (`SymptomUtils.decode`: sista kolonet skiljer, dubbletter
     * samlas och den sista poängen gäller) → `symptoms[]` (DAT-6): namnet bland symptomalternativen →
     * `optionId`; `Övrigt (fritext)` → "Övrigt" + `customText`; okänt namn → nytt arkiverat alternativ.
     * En del som 3.x inte kunde läsa stoppar i stället för att hoppas över. Avviker `somatiska` från
     * summan rapporteras det som varning (DAT-6).
     */
    private fun symptoms(path: String, raw: String, somatiska: Int): List<SymptomScore> {
        if (raw.isBlank()) {
            if (somatiska != 0) warn(path, "somatiska $somatiska utan symptom – summan blir 0")
            return emptyList()
        }
        val scores = linkedMapOf<String, Int>()
        raw.split(",").forEachIndexed { index, part ->
            val colon = part.lastIndexOf(':')
            val name = if (colon < 0) "" else part.substring(0, colon).trim()
            val score = if (colon < 0) null else part.substring(colon + 1).trim().toIntOrNull()
            when {
                colon < 0 -> problem(path, "symptom[$index]", "saknar poäng (ingen kolon)")
                name.isEmpty() -> problem(path, "symptom[$index]", "saknar namn")
                score == null -> problem(path, "symptom[$index]", "poängen är inte ett heltal")
                else -> {
                    if (name in scores) warn(path, "symptom[$index] är en dubblett – den sista poängen gäller, som i 3.x")
                    scores[name] = score
                }
            }
        }
        val symptoms = scores.map { (name, score) ->
            val option = options.find(OptionKind.SYMPTOM, name)
            when {
                option != null -> SymptomScore(option.id, score)
                name.startsWith(LegacyDefaults.OTHER) -> SymptomScore(
                    options.other(OptionKind.SYMPTOM).id,
                    score,
                    name.removePrefix(LegacyDefaults.OTHER).trim().removeSurrounding("(", ")").ifBlank { null },
                )
                else -> SymptomScore(options.findOrArchived(OptionKind.SYMPTOM, name).id, score)
            }
        }
        if (symptoms.somatic != somatiska) warn(path, "somatiska $somatiska skiljer sig från summan av symptompoängen ${symptoms.somatic}")
        return symptoms
    }

    // ── Värden ───────────────────────────────────────────────────────────────────────────────────

    private fun id(path: String, id: String) {
        if (!DocumentRules.isValidId(id)) problem(path, "id", "ogiltigt dokument-id (${id.length} tecken)")
    }

    /** `yyyy-MM-dd`; tomt → `null`, ogiltigt stoppar. */
    private fun date(path: String, field: String, raw: String): LocalDate? =
        raw.takeIf { it.isNotBlank() }?.let { parseDate(it) ?: null.also { problem(path, field, "ogiltigt datum") } }

    /** `HH:mm`; tomt → `null`, ogiltigt stoppar. */
    private fun clock(path: String, field: String, raw: String): LocalTime? =
        raw.takeIf { it.isNotBlank() }?.let { parseClock(it) ?: null.also { problem(path, field, "ogiltigt klockslag") } }

    private fun slot(path: String, field: String, raw: String): Slot =
        Slot.fromLegacyName(raw) ?: Slot.AS_NEEDED.also { problem(path, field, "okänd tidpunkt ${unknown(raw)}") }

    /** Ett värde utanför de kända – bara längden, aldrig värdet (det kan vara vad som helst). */
    private fun unknown(raw: String) = "okänt värde (${raw.length} tecken)"

    /**
     * 3.x `timestamp` som ISO-ögonblick; tomt eller ogiltigt → dag och klockslag i Europe/Stockholm
     * (utan klockslag: midnatt), utan dag `null`. Ett ogiltigt värde ersätts med varning.
     */
    private fun createdAt(path: String, iso: String, date: LocalDate?, time: LocalTime?): Instant? {
        LegacyTime.instant(iso)?.let { return it }
        if (iso.isNotBlank()) warn(path, "timestamp är inte ett giltigt ögonblick (${iso.length} tecken) – datum och klockslag i Europe/Stockholm används")
        return date?.let { at(path, "timestamp", it, time ?: LocalTime(0, 0)) }
    }

    /** Dag + klockslag i Europe/Stockholm; ligger klockslaget i sommartidsbytet syns det i rapporten (OMB-4). */
    private fun at(path: String, field: String, date: LocalDate, time: LocalTime): Instant {
        when (LegacyTime.shift(date, time)) {
            LegacyTime.Shift.GAP -> warn(path, "$field ligger i luckan vid sommartidsbytet – flyttat fram med luckans längd")
            LegacyTime.Shift.OVERLAP -> warn(path, "$field ligger i överlappningen vid sommartidsbytet – första förekomsten (sommartid) gäller")
            null -> Unit
        }
        return LegacyTime.at(date, time)
    }

    private fun problem(path: String, field: String, reason: String) {
        problems += Problem(path, field, reason)
    }

    private fun warn(path: String, message: String) {
        warnings += Warning(path, message)
    }

    private companion object {
        const val SCHEMA_VERSION = "schemaVersion"
        val SETTINGS_PATH = "${CollectionNames.SETTINGS}/${Settings.ID}"
        const val SECONDS_PER_HOUR = 3600
        const val TAKEN_EARLY_SECONDS = 12 * SECONDS_PER_HOUR
    }
}

/**
 * Alternativen (DAT-9): listorna ur backupen (V2 före v1, saknad lista → 3.x standardlista) i listans
 * ordning, och de som konverteringen behöver därutöver: "Övrigt" när det saknas (3.x:s fasta sentinel)
 * och arkiverade alternativ för namn som posterna använder men listan inte har.
 */
private class OptionRegistry(backup: BackupJson, private val warn: (String, String) -> Unit) {
    private val byKind: Map<OptionKind, MutableMap<String, Option>> = mapOf(
        OptionKind.ACTIVITY to seed(OptionKind.ACTIVITY, backup.aktiviteterOptionsV2 ?: backup.aktiviteterOptions?.map(::SymptomOptionBackup) ?: defaults(LegacyDefaults.ACTIVITY_OPTIONS)),
        OptionKind.SYMPTOM to seed(OptionKind.SYMPTOM, backup.symptomOptionsV2 ?: backup.symptomOptions?.map(::SymptomOptionBackup) ?: defaults(LegacyDefaults.SYMPTOM_OPTIONS)),
        OptionKind.EVENT to seed(OptionKind.EVENT, backup.handelseTypOptions ?: defaults(LegacyDefaults.EVENT_OPTIONS)),
    )

    private fun defaults(names: List<String>) = names.map(::SymptomOptionBackup)

    /** Listan i ordning; ett namn som förekommer två gånger är samma alternativ (det första gäller, med varning). */
    private fun seed(kind: OptionKind, rows: List<SymptomOptionBackup>): MutableMap<String, Option> {
        val options = linkedMapOf<String, Option>()
        rows.forEachIndexed { index, row ->
            if (row.name in options) warn(reportPath(kind, index), "namnet finns två gånger i ${kind.wire}-listan – det första gäller")
            else options[row.name] = Option(OptionIds.of(kind, row.name), kind, row.name, favorite = row.isFavorite, sortOrder = index)
        }
        return options
    }

    fun find(kind: OptionKind, name: String): Option? = byKind.getValue(kind)[name]

    /** "Övrigt" i listan, eller skapat (synligt, sist) om listan saknar det – 3.x hade det alltid. */
    fun other(kind: OptionKind): Option = find(kind, LegacyDefaults.OTHER) ?: create(kind, LegacyDefaults.OTHER, archived = false)

    /** Alternativet med namnet, eller ett nytt **arkiverat** – posten behåller sitt namn utan att listan växer synligt. */
    fun findOrArchived(kind: OptionKind, name: String): Option = find(kind, name) ?: create(kind, name, archived = true)

    private fun create(kind: OptionKind, name: String, archived: Boolean): Option {
        val options = byKind.getValue(kind)
        return Option(OptionIds.of(kind, name), kind, name, sortOrder = options.size, archived = archived).also { options[name] = it }
    }

    fun all(): List<Option> = byKind.values.flatMap { it.values }

    companion object {
        /** Alternativets plats i rapporten: lista och position – id:t bär namnet och får inte stå där. */
        fun reportPath(kind: OptionKind, index: Int) = "${CollectionNames.OPTIONS}/${kind.wire}#$index"
    }
}

/**
 * Anteckningarna ur notes-tabellen, nyckel (target, entityId) som i 3.x `NoteEntity`; en post här går
 * före arvsfältet `anteckning` (3.x `BackupMapper.toNotes`). Tom text är ingen anteckning. Anteckningar
 * som ingen post hämtar ([orphans]) räknas och rapporteras.
 */
private class NoteIndex(notes: List<NoteJson>, private val warn: (String, String) -> Unit) {
    private val texts = linkedMapOf<Pair<String, String>, String>()
    private val used = mutableSetOf<Pair<String, String>>()

    /** Anteckningar med text men utan target eller id – kan inte placeras, räknas i rapporten. */
    val incomplete: Int = notes.count { it.text.isNotBlank() && (it.target.isBlank() || it.entityId.isBlank()) }

    init {
        for (note in notes.filter { it.target.isNotBlank() && it.entityId.isNotBlank() && it.text.isNotBlank() }) {
            val key = note.target to note.entityId
            val existing = texts[key]
            when {
                existing == null -> texts[key] = note.text
                existing != note.text -> warn("notes", "två anteckningar för ${targetLabel(note.target)} ${idLabel(note.entityId)} med olika text – den första gäller")
            }
        }
    }

    /** Anteckningen för posten: notes-posten om den finns, annars arvsfältet ([legacy]); tom → `null`. */
    fun note(target: String, id: String, legacy: String, path: String): String? {
        val explicit = texts[target to id] ?: return legacy.ifBlank { null }
        used += target to id
        if (legacy.isNotBlank() && legacy != explicit) warn(path, "notes-posten går före arvsfältet anteckning (olika texter, ${legacy.length} tecken i arvsfältet)")
        return explicit
    }

    /** Antal anteckningar utan post, per target. */
    fun orphans(): Map<String, Int> = texts.keys.filterNot { it in used }.groupingBy { it.first }.eachCount()
}

/** Ett `target` i rapporten: 3.x-konstanten när den är känd, annars bara längden – det kan vara vad som helst. */
internal fun targetLabel(target: String): String =
    if (target in LegacyDefaults.NOTE_TARGETS) target else "okänt target (${target.length} tecken)"

/** Ett post-id i rapporten: id:t när det duger som dokument-id (UUID eller `recept_…`), annars bara längden. */
internal fun idLabel(id: String): String =
    if (DocumentRules.isValidId(id)) id else "(ogiltigt id, ${id.length} tecken)"
