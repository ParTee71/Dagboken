package se.partee71.dagboken.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import se.partee71.dagboken.domain.model.Dosperiod
import se.partee71.dagboken.domain.model.Favorit
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.Recept

@Entity(
    tableName = "mediciner",
    indices = [
        Index("datum"),
        Index(value = ["namn", "datum"]),
        Index("receptId"),
    ],
)
data class MedicinEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val datum: String,
    val tid: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkt: String,
    val tagen: Boolean,
    val receptId: String? = null,
    val skipped: Boolean = false,
    val tagenTid: String? = null,
)

@Entity(tableName = "recept")
data class ReceptEntity(
    @PrimaryKey val id: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkterJson: String,   // JSON array of strings
    val upprepning: String,
    val dagarJson: String,        // JSON array of ints
    val intervalDagar: Int = 2,
    val aktiv: Boolean,
    val skapad: String,
    val startDatum: String = "",      // "" = faller tillbaka på skapad (REC-7)
    val slutDatum: String? = null,    // null = tills vidare
    val dosperioderJson: String = "[]", // JSON array of Dosperiod
)

@Entity(tableName = "favoriter")
data class FavoritEntity(
    @PrimaryKey val id: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkt: String,
    val minTidMellan: Int,
    val dispenseringsTid: String = "",
    val maxDoserPerDag: Int = 0,
    val isFavorite: Boolean = false,
)

// ─── Mappers ──────────────────────────────────────────────────────────────────
fun MedicinEntity.toDomain() = Medicin(
    id         = id,
    timestamp  = timestamp,
    datum      = datum,
    tid        = tid,
    namn       = namn,
    dos        = dos,
    enhet      = enhet,
    tidpunkt   = tidpunkt,
    tagen      = tagen,
    receptId   = receptId,
    skipped    = skipped,
    tagenTid   = tagenTid,
)

fun Medicin.toEntity() = MedicinEntity(
    id         = id,
    timestamp  = timestamp,
    datum      = datum,
    tid        = tid,
    namn       = namn,
    dos        = dos,
    enhet      = enhet,
    tidpunkt   = tidpunkt,
    tagen      = tagen,
    receptId   = receptId,
    skipped    = skipped,
    tagenTid   = tagenTid,
)

fun ReceptEntity.toDomain(
    parseList: (String) -> List<String>,
    parseIntList: (String) -> List<Int>,
    parseDosperioder: (String) -> List<Dosperiod>,
) = Recept(
    id           = id,
    namn         = namn,
    dos          = dos,
    enhet        = enhet,
    tidpunkter   = parseList(tidpunkterJson),
    upprepning   = upprepning,
    dagar        = parseIntList(dagarJson),
    intervalDagar = intervalDagar,
    aktiv        = aktiv,
    skapad       = skapad,
    startDatum   = startDatum,
    slutDatum    = slutDatum,
    dosperioder  = parseDosperioder(dosperioderJson),
)

fun Recept.toEntity(
    serializeList: (List<String>) -> String,
    serializeIntList: (List<Int>) -> String,
    serializeDosperioder: (List<Dosperiod>) -> String,
) = ReceptEntity(
    id              = id,
    namn            = namn,
    dos             = dos,
    enhet           = enhet,
    tidpunkterJson  = serializeList(tidpunkter),
    upprepning      = upprepning,
    dagarJson       = serializeIntList(dagar),
    intervalDagar   = intervalDagar,
    aktiv           = aktiv,
    skapad          = skapad,
    startDatum      = startDatum,
    slutDatum       = slutDatum,
    dosperioderJson = serializeDosperioder(dosperioder),
)

fun FavoritEntity.toDomain() = Favorit(
    id               = id,
    namn             = namn,
    dos              = dos,
    enhet            = enhet,
    tidpunkt         = tidpunkt,
    minTidMellan     = minTidMellan,
    dispenseringsTid = dispenseringsTid,
    maxDoserPerDag   = maxDoserPerDag,
    isFavorite       = isFavorite,
)

fun Favorit.toEntity() = FavoritEntity(
    id               = id,
    namn             = namn,
    dos              = dos,
    enhet            = enhet,
    tidpunkt         = tidpunkt,
    minTidMellan     = minTidMellan,
    dispenseringsTid = dispenseringsTid,
    maxDoserPerDag   = maxDoserPerDag,
    isFavorite       = isFavorite,
)
