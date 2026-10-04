package se.partee71.dagboken.navigation

import kotlin.test.assertNotNull
import org.junit.Test

/** Varje nyckel har en skärm i `appEntries` – en saknad skulle krascha appen (fallbacken är ett fel). */
class AppNavigationTest {

    /** En av varje nyckeltyp; när AppKey får en ny typ följer listan med (kompilatorn kräver det). */
    private val keys: List<AppKey> = listOf(
        TodayKey,
        DiaryKey,
        TrendsKey,
        MedicinesKey,
        ComponentGalleryKey,
    ).onEach { key ->
        // Uttömmande: en ny nyckeltyp utan gren här ger ett kompileringsfel.
        when (key) {
            TodayKey, DiaryKey, TrendsKey, MedicinesKey, ComponentGalleryKey -> Unit
        }
    }

    @Test
    fun `varje nyckel har en egen skärm`() {
        val entries = appEntries(AppBackStack(), onAccount = {})
        // En nyckel utan skärm når fallbacken, som kastar; en med skärm ger sin post.
        keys.forEach { key -> assertNotNull(entries(key)) }
    }
}
