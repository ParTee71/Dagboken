package se.partee71.dagboken.navigation

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** NAV-8, NAV-11: flikar med egna stackar, start på Idag, tillbaka via Idag. */
class AppBackStackTest {

    @Test
    fun `appen startar på Idag med bottenraden synlig`() {
        val stack = AppBackStack()
        assertEquals(listOf<AppKey>(TodayKey), stack.entries)
        assertTrue(stack.atTopLevel)
        assertEquals(listOf(TodayKey, DiaryKey, TrendsKey, MedicinesKey), TOP_LEVEL)
    }

    @Test
    fun `en undersida döljer bottenraden och tillbaka visar den igen (NAV-3)`() {
        val stack = AppBackStack()
        stack.push(ComponentGalleryKey)
        assertFalse(stack.atTopLevel)
        assertTrue(stack.pop())
        assertTrue(stack.atTopLevel)
    }

    @Test
    fun `varje flik minns sin undersida när man byter flik`() {
        val stack = AppBackStack()
        stack.select(DiaryKey)
        stack.push(ComponentGalleryKey)
        stack.select(TrendsKey)
        assertEquals(listOf(TodayKey, TrendsKey), stack.entries)
        stack.select(DiaryKey)
        assertEquals(listOf(TodayKey, DiaryKey, ComponentGalleryKey), stack.entries)
    }

    @Test
    fun `tillbaka i roten av en annan flik går till Idag, i Idags rot lämnar appen`() {
        val stack = AppBackStack()
        stack.select(MedicinesKey)
        assertTrue(stack.pop())
        assertEquals(TodayKey, stack.currentTab)
        assertFalse(stack.pop())
    }

    @Test
    fun `att välja fliken man står på går till flikens rot`() {
        val stack = AppBackStack()
        stack.select(DiaryKey)
        stack.push(ComponentGalleryKey)
        stack.select(DiaryKey)
        assertEquals(listOf(TodayKey, DiaryKey), stack.entries)
    }

    @Test
    fun `flikar väljs, de läggs inte på stacken`() {
        assertFailsWith<IllegalArgumentException> { AppBackStack().push(DiaryKey) }
    }

    @Test
    fun `stackarna överlever att sparas och återställas`() {
        val stack = AppBackStack()
        stack.push(ComponentGalleryKey)
        stack.select(MedicinesKey)
        stack.push(ComponentGalleryKey)
        val restored = AppBackStack.restore(stack.save())
        assertEquals(stack.entries, restored.entries)
        assertEquals(MedicinesKey, restored.currentTab)
        restored.select(TodayKey)
        assertEquals(listOf(TodayKey, ComponentGalleryKey), restored.entries)
    }

    @Test
    fun `ett dubbeltryck öppnar skärmen en gång`() {
        val stack = AppBackStack()
        stack.push(ComponentGalleryKey)
        stack.push(ComponentGalleryKey)
        assertEquals(listOf(TodayKey, ComponentGalleryKey), stack.entries)
    }

    @Test
    fun `en skärm som stänger sig två gånger stänger inte skärmen under`() {
        val stack = AppBackStack()
        stack.select(TrendsKey)
        stack.push(ComponentGalleryKey)

        stack.popIfTop(ComponentGalleryKey)
        stack.popIfTop(ComponentGalleryKey)

        assertEquals(listOf(TodayKey, TrendsKey), stack.entries)
        assertEquals(TrendsKey, stack.currentTab)
    }
}
