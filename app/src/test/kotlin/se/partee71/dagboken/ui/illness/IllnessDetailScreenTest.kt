package se.partee71.dagboken.ui.illness

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.partee71.dagboken.core.engine.EndDateError
import se.partee71.dagboken.core.engine.illnessSummary
import se.partee71.dagboken.core.model.Checkin
import se.partee71.dagboken.core.model.IllnessEpisode
import se.partee71.dagboken.core.model.SymptomScore
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.testing.captureLightAndDark
import se.partee71.dagboken.testing.captureScreenLightAndDark
import se.partee71.dagboken.ui.common.DetailUiState
import se.partee71.dagboken.ui.common.Failure
import se.partee71.dagboken.ui.runDetailScreenContract
import se.partee71.dagboken.ui.theme.DagbokenTheme

/**
 * Sjukdomsdetaljen (SJ-4, SJ-5, SJ-9, SJ-12, SJ-13, NFR-15/16): ramens kontrakt, det unika och skärmdumpar bredvid
 * mockupens tavlor (canvas avsnitt 13 – Sjuk-Pagaende, Sjuk-Avslutad, Sjuk-Tom, Sjuk-Avsluta, Sjuk-Radera). Påhittad data.
 */
@RunWith(RobolectricTestRunner::class)
class IllnessDetailScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val created = Instant.fromEpochSeconds(1_791_270_000)
    private val names = mapOf("snuva" to "Snuva", "hosta" to "Hosta", "feber" to "Feber", "trotthet" to "Trötthet")
    private val flu = IllnessEpisode("flu", "Förkylning", LocalDate(2026, 10, 1), createdAt = created, note = "Började efter jobbresan.")

    private fun checkin(id: String, day: Int, hour: Int, minute: Int, severity: Int, vararg symptoms: Pair<String, Int>, note: String? = null) =
        Checkin(id, LocalDate(2026, 10, day), LocalTime(hour, minute), severity, symptoms.map { (s, v) -> SymptomScore(s, v) }, created, note)

    private val checkins = listOf(
        checkin("c1", 1, 19, 0, 5, "snuva" to 4, "trotthet" to 5),
        checkin("c2", 3, 8, 30, 6, "snuva" to 6, "feber" to 2, "trotthet" to 6),
        checkin("c3", 5, 21, 40, 4, "snuva" to 5, "trotthet" to 4, note = "Sov dåligt"),
        checkin("c4", 6, 9, 0, 3, "snuva" to 3, "hosta" to 4),
    )

    private val ongoing = IllnessDetail(illnessSummary(flu, checkins, LocalDate(2026, 10, 6)), LocalDate(2026, 10, 6), names)
    private val ended = IllnessDetail(
        illnessSummary(flu.copy(end = LocalDate(2026, 10, 9), note = null), checkins + checkin("c5", 9, 8, 0, 1, "snuva" to 1), LocalDate(2026, 10, 10)),
        LocalDate(2026, 10, 10),
        names,
    )
    private val empty = IllnessDetail(
        illnessSummary(IllnessEpisode("migran", "Migrän", LocalDate(2026, 10, 6), createdAt = created), emptyList(), LocalDate(2026, 10, 6)),
        LocalDate(2026, 10, 6),
    )

    @Test
    fun `detaljen uppfyller detaljkontraktet`() = rule.runDetailScreenContract(ongoing, "tors 1 okt 2026") { state, onRetry, onEdit, onBack ->
        IllnessDetailScreen(state, null, null, { if (it == IllnessDetailEvent.Retry) onRetry() }, onBack, onEdit, {})
    }

    @Test
    fun `en pågående episod visar huvudet, knapparna och incheckningarna senaste först (SJ-5, SJ-13)`() {
        val events = mutableListOf<IllnessDetailEvent>()
        val opened = mutableListOf<String?>()
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ongoing), null, null, { events += it }, {}, {}, { opened += it }) } }
        rule.onNodeWithText("Förkylning").assertIsDisplayed()
        rule.onNodeWithText("Pågår").assertIsDisplayed()
        rule.onNodeWithText("tors 1 okt 2026").assertIsDisplayed()
        rule.onNodeWithText("Slutade").assertDoesNotExist()
        rule.onNodeWithText("6 dagar").assertIsDisplayed()
        rule.onNodeWithText("3 · tis 6 okt").assertIsDisplayed()
        rule.onNodeWithText("Började efter jobbresan.").assertIsDisplayed()
        rule.onNodeWithText("Ny incheckning").performClick()
        rule.onNodeWithText("Avsluta episod").performClick()
        rule.onNodeWithText("09:00 · Svårighet 3 · Snuva 3 · Hosta 4").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Dag 6").assertIsDisplayed()
        rule.onNodeWithText("Mån 5 okt").performScrollTo().performClick()
        assertEquals(listOf(null, "c3"), opened, "Ny incheckning, och tryck på ett postkort redigerar")
        assertEquals(listOf<IllnessDetailEvent>(IllnessDetailEvent.Finish), events)
    }

    @Test
    fun `en avslutad episod visar slutet och saknar knapparna (SJ-4, SJ-5)`() {
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ended), null, null, {}, {}, {}, {}) } }
        rule.onNodeWithText("Avslutad").assertIsDisplayed()
        rule.onNodeWithText("fre 9 okt 2026").assertIsDisplayed()
        rule.onNodeWithText("9 dagar").assertIsDisplayed()
        rule.onNodeWithText("1 · fre 9 okt").assertIsDisplayed()
        rule.onNodeWithText("Ny incheckning").assertDoesNotExist()
        rule.onNodeWithText("Avsluta episod").assertDoesNotExist()
    }

    @Test
    fun `utan incheckningar visas det tomma tillståndet och ett streck för senaste svårighet`() {
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(empty), null, null, {}, {}, {}, {}) } }
        rule.onNodeWithText("1 dag").assertIsDisplayed()
        rule.onNodeWithText("—").assertIsDisplayed()
        rule.onNodeWithText("Inga incheckningar än").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `en incheckning raderas efter bekräftelse i postkortets meny (HIST-5, NFR-15)`() {
        val events = mutableListOf<IllnessDetailEvent>()
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ongoing), null, null, { events += it }, {}, {}, {}) } }
        // Postkortens menyer först i trädet (senaste incheckningen överst), toppradens sist.
        rule.onAllNodesWithContentDescription("Fler val").onFirst().performClick()
        rule.onNodeWithText("Radera").performClick()
        rule.onNodeWithText("Incheckningen för Förkylning, 6 okt kl. 09:00 raderas med sin anteckning. Det går inte att ångra.").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<IllnessDetailEvent>(IllnessDetailEvent.DeleteCheckin("c4")), events)
    }

    @Test
    fun `Radera i toppradens meny frågar och nämner antalet incheckningar (SJ-9)`() {
        val events = mutableListOf<IllnessDetailEvent>()
        var prompt by mutableStateOf<IllnessPrompt?>(null)
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ongoing), prompt, null, { events += it }, {}, {}, {}) } }
        rule.onAllNodesWithContentDescription("Fler val").onLast().performClick()
        rule.onNodeWithText("Radera").performClick()
        assertEquals(listOf<IllnessDetailEvent>(IllnessDetailEvent.Delete), events)

        prompt = IllnessPrompt.Delete(4)
        rule.onNodeWithText("Radera sjukdomsepisoden?").assertIsDisplayed()
        rule.onNodeWithText("Förkylning (1 okt – 6 okt) raderas med sina 4 incheckningar och deras anteckningar. Det går inte att ångra.").assertIsDisplayed()
        rule.onNodeWithText("Radera").performClick()
        prompt = IllnessPrompt.Delete(1)
        rule.onNodeWithText("Förkylning (1 okt – 6 okt) raderas med sin incheckning och dess anteckning. Det går inte att ångra.").assertIsDisplayed()
        prompt = IllnessPrompt.Delete(0)
        rule.onNodeWithText("Förkylning (1 okt – 6 okt) raderas. Det går inte att ångra.").assertIsDisplayed()
        rule.onNodeWithText("Avbryt").performClick()
        assertEquals(listOf(IllnessDetailEvent.Delete, IllnessDetailEvent.ConfirmDelete, IllnessDetailEvent.DismissPrompt), events)
    }

    @Test
    fun `Avsluta episod frågar efter slutdatumet och visar ett slut före starten som fel (SJ-4)`() {
        val events = mutableListOf<IllnessDetailEvent>()
        var prompt by mutableStateOf<IllnessPrompt?>(IllnessPrompt.Finish(LocalDate(2026, 10, 6)))
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ongoing), prompt, null, { events += it }, {}, {}, {}) } }
        rule.onNodeWithText("Avsluta episoden?").assertIsDisplayed()
        rule.onNodeWithText("Förkylning markeras som avslutad. Incheckningarna står kvar.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Slutdatum", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Avsluta").performClick()
        assertEquals(listOf<IllnessDetailEvent>(IllnessDetailEvent.ConfirmFinish), events)
        prompt = IllnessPrompt.Finish(LocalDate(2026, 9, 30), EndDateError.BEFORE_START)
        rule.onNodeWithContentDescription("Slutdatumet kan inte vara före startdatumet.", substring = true).assertIsDisplayed()
        prompt = IllnessPrompt.Finish(LocalDate(2026, 10, 7), EndDateError.AFTER_TODAY)
        rule.onNodeWithContentDescription("Slutdatumet kan inte vara senare än idag.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `under en radering finns varken Ny incheckning, Avsluta, Redigera eller menyer (SJ-9)`() {
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ongoing.copy(deleting = true)), null, null, {}, {}, {}, {}) } }
        rule.onNodeWithText("Förkylning").assertIsDisplayed()
        rule.onNodeWithText("Ny incheckning").assertDoesNotExist()
        rule.onNodeWithText("Avsluta episod").assertDoesNotExist()
        rule.onNodeWithContentDescription("Redigera").assertDoesNotExist()
        rule.onNodeWithContentDescription("Fler val").assertDoesNotExist()
    }

    @Test
    fun `en episod som börjar efter idag har Ny incheckning men inte Avsluta episod (SJ-4)`() {
        val later = IllnessDetail(illnessSummary(flu.copy(start = LocalDate(2026, 10, 8)), emptyList(), LocalDate(2026, 10, 6)), LocalDate(2026, 10, 6))
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(later), null, null, {}, {}, {}, {}) } }
        rule.onNodeWithText("Ny incheckning").assertIsDisplayed()
        rule.onNodeWithText("Avsluta episod").assertDoesNotExist()
    }

    @Test
    fun `ett fel från en åtgärd visas som meddelande (SJ-9)`() {
        rule.setContent { DagbokenTheme { IllnessDetailScreen(DetailUiState.Content(ongoing), null, Failure(DataError.Offline), {}, {}, {}, {}) } }
        rule.onNodeWithText("Ingen anslutning just nu. Det du sparar skickas när nätet är tillbaka.").assertIsDisplayed()
    }

    // ── Skärmdumpar bredvid mockupen ──────────────────────────────────────

    @Test
    @Config(qualifiers = "w390dp-h1120dp-xxhdpi")
    fun `skärmdump - pågående`() = rule.captureLightAndDark("Sjuk_pagaende") {
        IllnessDetailScreen(DetailUiState.Content(ongoing), null, null, {}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "w390dp-h1120dp-xxhdpi")
    fun `skärmdump - avslutad`() = rule.captureLightAndDark("Sjuk_avslutad") {
        IllnessDetailScreen(DetailUiState.Content(ended), null, null, {}, {}, {}, {})
    }

    @Test
    fun `skärmdump - utan incheckningar`() = rule.captureLightAndDark("Sjuk_tom") {
        IllnessDetailScreen(DetailUiState.Content(empty), null, null, {}, {}, {}, {})
    }

    @Test
    fun `skärmdump - avsluta episod`() = rule.captureScreenLightAndDark("Sjuk_avsluta") {
        IllnessDetailScreen(DetailUiState.Content(ongoing), IllnessPrompt.Finish(LocalDate(2026, 10, 6)), null, {}, {}, {}, {})
    }

    @Test
    fun `skärmdump - radera episod`() = rule.captureScreenLightAndDark("Sjuk_radera") {
        IllnessDetailScreen(DetailUiState.Content(ongoing), IllnessPrompt.Delete(4), null, {}, {}, {}, {})
    }
}
