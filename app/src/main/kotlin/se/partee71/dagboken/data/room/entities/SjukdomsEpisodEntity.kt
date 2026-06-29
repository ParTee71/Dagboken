package se.partee71.dagboken.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sjukdomsepisoder")
data class SjukdomsEpisodEntity(
    @PrimaryKey val id: String,
    val typ: String,
    @ColumnInfo(name = "start_datum") val startDatum: String,
    @ColumnInfo(name = "slut_datum")  val slutDatum: String = "",
    val anteckning: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)
