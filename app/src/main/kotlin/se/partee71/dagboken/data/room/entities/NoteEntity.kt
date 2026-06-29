package se.partee71.dagboken.data.room.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "notes",
    primaryKeys = ["target", "entityId"],
    indices = [Index("entityId")],
)
data class NoteEntity(
    val target: String,
    val entityId: String,
    val text: String,
)
