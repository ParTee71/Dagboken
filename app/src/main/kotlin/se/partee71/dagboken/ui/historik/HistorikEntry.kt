package se.partee71.dagboken.ui.historik

import se.partee71.dagboken.domain.model.Aktivitet
import se.partee71.dagboken.domain.model.Handelse
import se.partee71.dagboken.domain.model.Medicin
import se.partee71.dagboken.domain.model.SjukdomsIncheckning

enum class HistorikType { AKTIVITET, SCREENING, MEDICIN, HANDELSE, SJUKDOM }

enum class HistorikViewMode { LISTA, KALENDER }

sealed class HistorikEntry {
    abstract val id: String
    abstract val datum: String
    abstract val tid: String
    abstract val entryType: HistorikType

    data class AktivitetEntry(val aktivitet: Aktivitet) : HistorikEntry() {
        override val id = aktivitet.id
        override val datum = aktivitet.datum
        override val tid = aktivitet.tid
        override val entryType =
            if (aktivitet.type == "screening") HistorikType.SCREENING else HistorikType.AKTIVITET
    }

    data class MedicinEntry(val medicin: Medicin) : HistorikEntry() {
        override val id = medicin.id
        override val datum = medicin.datum
        override val tid = medicin.tid
        override val entryType = HistorikType.MEDICIN
    }

    data class HandelseEntry(val handelse: Handelse) : HistorikEntry() {
        override val id = handelse.id
        override val datum = handelse.datum
        override val tid = handelse.tid
        override val entryType = HistorikType.HANDELSE
    }

    data class IncheckningEntry(
        val incheckning: SjukdomsIncheckning,
        val episodTyp: String,
    ) : HistorikEntry() {
        override val id = incheckning.id
        override val datum = incheckning.datum
        override val tid = incheckning.tid
        override val entryType = HistorikType.SJUKDOM
    }
}
