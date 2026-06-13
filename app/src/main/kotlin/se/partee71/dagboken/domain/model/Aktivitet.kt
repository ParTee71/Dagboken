package se.partee71.dagboken.domain.model

data class Aktivitet(
    val id: String,
    val timestamp: String,
    val datum: String,           // YYYY-MM-DD
    val tid: String,             // HH:MM
    val aktivitet: String,
    val energy: Int,             // -10..+10 (aktivitet) or 1..10 (screening)
    val stress: Int,             // 0..10
    val somatiska: Int,          // sum of symptom scores
    val symptom: String,         // "Name:Score,Name:Score" wire format
    val aterhamtande: Boolean = false,
    val energitjuv: Boolean = false,
    val type: String = "aktivitet",  // "aktivitet" | "screening"
    val spentTime: Int? = null,  // minutes
)
