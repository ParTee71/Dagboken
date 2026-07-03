package se.partee71.dagboken.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sjukdoms_incheckningar",
    foreignKeys = [ForeignKey(
        entity = SjukdomsEpisodEntity::class,
        parentColumns = ["id"],
        childColumns = ["episod_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("episod_id")],
)
data class SjukdomsIncheckningEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "episod_id") val episodId: String,
    val datum: String,
    val tid: String,
    val svarighetsgrad: Int,
    val symptom: String = "",
    val somatiska: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)
