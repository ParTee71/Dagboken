package se.partee71.dagboken.domain.model

data class Handelse(
    val id: String,
    val timestamp: String,
    val datum: String,
    val tid: String,
    val typ: String,
    val svarighetsgrad: Int,
    val varaktighetMinuter: Int,
    val triggers: String,
    val atgarder: String,
)
