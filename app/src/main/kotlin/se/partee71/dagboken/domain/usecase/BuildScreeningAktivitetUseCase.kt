package se.partee71.dagboken.domain.usecase

import se.partee71.dagboken.domain.Timestamps
import se.partee71.dagboken.domain.model.Aktivitet
import java.util.UUID
import javax.inject.Inject

/**
 * Bygger en screening-[Aktivitet] från energi/stress/symptom — samma mappning som
 * [se.partee71.dagboken.ui.aktiviteter.AktiviteterViewModel.save] använder för
 * `type == "screening"`, delad så appen och widgeten (#157) inte kan divergera.
 */
class BuildScreeningAktivitetUseCase @Inject constructor() {
    fun build(
        aktivitetName: String,
        datum: String,
        tid: String,
        energy: Int,
        stress: Int,
        symptomScores: Map<String, Int>,
        editId: String? = null,
    ): Aktivitet {
        val symptomStr = SymptomUtils.encode(symptomScores)
        return Aktivitet(
            id = editId ?: UUID.randomUUID().toString(),
            timestamp = Timestamps.of(datum, tid),
            datum = datum,
            tid = tid,
            aktivitet = aktivitetName,
            energy = energy,
            stress = stress,
            somatiska = SymptomUtils.sum(symptomStr),
            symptom = symptomStr,
            type = "screening",
        )
    }
}
