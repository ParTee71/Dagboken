package se.partee71.dagboken.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import se.partee71.dagboken.domain.model.Handelse

@Entity(
    tableName = "health_events",
    indices = [Index("datum")],
)
data class HandelseEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val datum: String,
    val tid: String,
    val typ: String,
    val svarighetsgrad: Int,
    val varaktighetMinuter: Int,
    val triggers: String,
    val atgarder: String,
    val anteckning: String,
)

fun HandelseEntity.toDomain() = Handelse(
    id                 = id,
    timestamp          = timestamp,
    datum              = datum,
    tid                = tid,
    typ                = typ,
    svarighetsgrad     = svarighetsgrad,
    varaktighetMinuter = varaktighetMinuter,
    triggers           = triggers,
    atgarder           = atgarder,
    anteckning         = anteckning,
)

fun Handelse.toEntity() = HandelseEntity(
    id                 = id,
    timestamp          = timestamp,
    datum              = datum,
    tid                = tid,
    typ                = typ,
    svarighetsgrad     = svarighetsgrad,
    varaktighetMinuter = varaktighetMinuter,
    triggers           = triggers,
    atgarder           = atgarder,
    anteckning         = anteckning,
)
