package se.partee71.dagboken.data.room.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import se.partee71.dagboken.domain.model.Aktivitet

@Entity(
    tableName = "aktiviteter",
    indices = [Index("datum"), Index("timestamp")],
)
data class AktivitetEntity(
    @PrimaryKey val id: String,
    val timestamp: String,
    val datum: String,
    val tid: String,
    val aktivitet: String,
    val energy: Int,
    val stress: Int,
    val somatiska: Int,
    val symptom: String,
    val aterhamtande: Boolean = false,
    val energitjuv: Boolean = false,
    val type: String = "aktivitet",
    val spentTime: Int? = null,
)

fun AktivitetEntity.toDomain() = Aktivitet(
    id           = id,
    timestamp    = timestamp,
    datum        = datum,
    tid          = tid,
    aktivitet    = aktivitet,
    energy       = energy,
    stress       = stress,
    somatiska    = somatiska,
    symptom      = symptom,
    aterhamtande = aterhamtande,
    energitjuv   = energitjuv,
    type         = type,
    spentTime    = spentTime,
)

fun Aktivitet.toEntity() = AktivitetEntity(
    id           = id,
    timestamp    = timestamp,
    datum        = datum,
    tid          = tid,
    aktivitet    = aktivitet,
    energy       = energy,
    stress       = stress,
    somatiska    = somatiska,
    symptom      = symptom,
    aterhamtande = aterhamtande,
    energitjuv   = energitjuv,
    type         = type,
    spentTime    = spentTime,
)
