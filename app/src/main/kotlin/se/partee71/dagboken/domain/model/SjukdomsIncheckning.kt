package se.partee71.dagboken.domain.model

data class SjukdomsIncheckning(
    val id: String,
    val episodId: String,
    val datum: String,
    val tid: String,
    val svarighetsgrad: Int,
    val symptom: String,
    val somatiska: Int,
    val timestamp: Long = System.currentTimeMillis(),
)
