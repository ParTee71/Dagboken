package se.partee71.dagboken.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index(value = ["target_type", "target_id"], unique = true)],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id")   val targetId: String,
    val text: String,
    @ColumnInfo(name = "updated_at")  val updatedAt: Long = System.currentTimeMillis(),
)
