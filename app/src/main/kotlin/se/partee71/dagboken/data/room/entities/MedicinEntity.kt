package se.partee71.dagboken.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
    val anteckning: String,
    val receptId: String? = null,
    val skipped: Boolean = false,
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
    val anteckning: String,
    val aktiv: Boolean,
    val skapad: String,
)

@Entity(tableName = "favoriter")
data class FavoritEntity(
    @PrimaryKey val id: String,
    val namn: String,
    val dos: String,
    val enhet: String,
    val tidpunkt: String,
    val anteckning: String,
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
    anteckning = anteckning,
    receptId   = receptId,
    skipped    = skipped,
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
    anteckning = anteckning,
    receptId   = receptId,
    skipped    = skipped,
)

fun ReceptEntity.toDomain(
    parseList: (String) -> List<String>,
    parseIntList: (String) -> List<Int>,
) = Recept(
    id           = id,
    namn         = namn,
    dos          = dos,
    enhet        = enhet,
    tidpunkter   = parseList(tidpunkterJson),
    upprepning   = upprepning,
    dagar        = parseIntList(dagarJson),
    intervalDagar = intervalDagar,
    anteckning   = anteckning,
    aktiv        = aktiv,
    skapad       = skapad,
)

fun Recept.toEntity(
    serializeList: (List<String>) -> String,
    serializeIntList: (List<Int>) -> String,
) = ReceptEntity(
    id              = id,
    namn            = namn,
    dos             = dos,
    enhet           = enhet,
    tidpunkterJson  = serializeList(tidpunkter),
    upprepning      = upprepning,
    dagarJson       = serializeIntList(dagar),
    intervalDagar   = intervalDagar,
    anteckning      = anteckning,
    aktiv           = aktiv,
    skapad          = skapad,
)

fun FavoritEntity.toDomain() = Favorit(
    id               = id,
    namn             = namn,
    dos              = dos,
    enhet            = enhet,
    tidpunkt         = tidpunkt,
    anteckning       = anteckning,
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
    anteckning       = anteckning,
    minTidMellan     = minTidMellan,
    dispenseringsTid = dispenseringsTid,
    maxDoserPerDag   = maxDoserPerDag,
    isFavorite       = isFavorite,
)
