package se.partee71.dagboken.data.repository

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import se.partee71.dagboken.core.model.Profile
import se.partee71.dagboken.core.model.Settings
import se.partee71.dagboken.core.model.Sex
import se.partee71.dagboken.core.model.Slot
import se.partee71.dagboken.core.model.ThemeMode
import se.partee71.dagboken.core.model.ThemeSettings
import se.partee71.dagboken.data.FakeCollectionFactory
import se.partee71.dagboken.data.TestUserScope
import se.partee71.dagboken.core.model.Identified
import se.partee71.dagboken.core.schema.DocCodec
import se.partee71.dagboken.core.schema.SettingsCodec
import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.data.common.EntityCollection
import se.partee71.dagboken.data.firestore.CollectionTable
import se.partee71.dagboken.data.firestore.Paths

/** `settings/app` (DAT-11): standardvärden när dokumentet saknas, och en ändring bevarar allt den inte rör. */
class SettingsRepositoryTest {

    private val factory = FakeCollectionFactory()
    private val repository = DefaultSettingsRepository(factory)
    private val path = Paths.settings("uid-test")

    private fun stored() = checkNotNull(factory.store.read(path, Settings.ID))

    @Test
    fun `utan dokument gäller standardvärdena`() = runTest {
        assertEquals(Settings(), repository.get().getOrThrow())
        repository.settings.test { assertEquals(Settings(), awaitItem()) }
    }

    @Test
    fun `legacy och okända fält överlever en spara-rundtur (DAT-11)`() = runTest {
        factory.store.set(
            path,
            Settings.ID,
            mapOf(
                "theme" to mapOf("mode" to "dark", "lightStartHour" to 6L, "darkStartHour" to 20L, "isDarkTheme" to false, "accent" to "teal"),
                "reminders" to mapOf("medsEnabled" to true, "snooze" to 10L),
                "profile" to mapOf("birthYear" to 1971L, "sex" to "female", "height" to 170L),
                "legacy" to mapOf("dynamicColor" to true, "sheetsConfig" to "https://sheets.exempel.se/1", "widgetTheme" to "rund"),
                "futureGroup" to mapOf("x" to 1L),
            ),
            merge = false,
        )

        repository.update { it.copy(profile = Profile(birthYear = 1980, sex = Sex.MALE)) }.getOrThrow()

        val doc = stored()
        assertEquals(mapOf("dynamicColor" to true, "sheetsConfig" to "https://sheets.exempel.se/1", "widgetTheme" to "rund"), doc["legacy"])
        assertEquals(mapOf("x" to 1L), doc["futureGroup"])
        val profile = doc["profile"] as Map<*, *>
        assertEquals(1980L, profile["birthYear"])
        assertEquals("male", profile["sex"])
        assertEquals(170L, profile["height"], "okänt fält i den ändrade gruppen")
        val theme = doc["theme"] as Map<*, *>
        assertEquals("dark", theme["mode"])
        assertEquals("teal", theme["accent"])
        assertEquals(10L, (doc["reminders"] as Map<*, *>)["snooze"])
        assertEquals(ThemeMode.DARK, repository.get().getOrThrow().theme.mode)
    }

    @Test
    fun `en ändring skrivs ovanpå det som är lagrat, inte ovanpå standardvärdena`() = runTest {
        repository.update { it.copy(theme = it.theme.copy(mode = ThemeMode.LIGHT)) }.getOrThrow()
        repository.update { it.copy(profile = Profile(birthYear = 1971)) }.getOrThrow()

        val settings = repository.get().getOrThrow()
        assertEquals(ThemeMode.LIGHT, settings.theme.mode)
        assertEquals(1971, settings.profile.birthYear)
    }

    @Test
    fun `samtidiga ändringar av olika grupper och i samma grupp tappar ingen av dem`() = runTest {
        repository.update { it.copy(reminders = it.reminders.copy(medsEnabled = true)) }.getOrThrow()

        listOf(
            async { repository.update { it.copy(theme = it.theme.copy(mode = ThemeMode.DARK)) } },
            async { repository.update { it.copy(theme = it.theme.copy(lightStartHour = 5)) } },
            async { repository.update { it.copy(profile = Profile(birthYear = 1971, sex = Sex.FEMALE)) } },
            async { repository.update { it.copy(theme = it.theme.copy(darkStartHour = 23)) } },
        ).awaitAll().forEach { it.getOrThrow() }

        val settings = repository.get().getOrThrow()
        assertEquals(ThemeSettings(mode = ThemeMode.DARK, lightStartHour = 5, darkStartHour = 23), settings.theme)
        assertEquals(Profile(birthYear = 1971, sex = Sex.FEMALE), settings.profile)
        assertTrue(settings.reminders.medsEnabled)
    }

    @Test
    fun `en ny användare utan dokument kan spara – dokumentet skapas med bara ändringen`() = runTest {
        repository.update { it.copy(theme = it.theme.copy(mode = ThemeMode.DARK)) }.getOrThrow()

        assertEquals(mapOf("theme" to mapOf("mode" to "dark")), stored(), "inga standardvärden som kan skriva över serverns")
        assertEquals(Settings(theme = ThemeSettings(mode = ThemeMode.DARK)), repository.get().getOrThrow())
    }

    @Test
    fun `bara ändrade fält skrivs – inget annat i gruppen, inga standardvärden`() = runTest {
        // Delvis lagrat (en äldre app, eller en annan enhet som bara skrivit läget).
        factory.store.set(path, Settings.ID, mapOf("theme" to mapOf("mode" to "auto", "darkStartHour" to 22L)), merge = false)

        repository.update { it.copy(theme = it.theme.copy(mode = ThemeMode.LIGHT)) }.getOrThrow()

        assertEquals(mapOf("theme" to mapOf("mode" to "light", "darkStartHour" to 22L)), stored())
    }

    @Test
    fun `ett formulär skriver bara det användaren ändrat – inte tillbaka det en annan enhet ändrat sedan`() = runTest {
        repository.update { it.copy(profile = Profile(birthYear = 1971), reminders = it.reminders.copy(medsEnabled = false)) }.getOrThrow()
        val loaded = repository.get().getOrThrow()
        // En annan enhet slår på medicinpåminnelserna och sätter kön medan formuläret är öppet.
        factory.store.set(path, Settings.ID, mapOf("reminders" to mapOf("medsEnabled" to true), "profile" to mapOf("sex" to "female")), merge = true)

        repository.save(loaded, loaded.copy(profile = loaded.profile.copy(birthYear = 1972))).getOrThrow()

        val settings = repository.get().getOrThrow()
        assertEquals(Profile(birthYear = 1972, sex = Sex.FEMALE), settings.profile)
        assertTrue(settings.reminders.medsEnabled)
    }

    @Test
    fun `ett oförändrat formulär skriver ingenting`() = runTest {
        repository.save(Settings(), Settings()).getOrThrow()
        assertNull(factory.store.read(path, Settings.ID))
    }

    @Test
    fun `inte i cachen och servern nås inte – Offline, inga standardvärden och ingenting skrivs`() = runTest {
        // Servern har värden som cachen ännu inte fått.
        val server = mapOf("theme" to mapOf("mode" to "dark"), "profile" to mapOf("birthYear" to 1971L))
        factory.store.set(path, Settings.ID, server, merge = false)
        val offline = DefaultSettingsRepository(CachedDoc(factory, DataError.Offline))

        assertEquals(DataError.Offline, offline.get().exceptionOrNull(), "standardvärden visas inte som lagrade")
        assertEquals(DataError.Offline, offline.update { it.copy(theme = it.theme.copy(mode = ThemeMode.AUTO)) }.exceptionOrNull())

        assertEquals(server, stored())
    }

    private fun Settings.withSlot(slot: Slot, time: LocalTime) =
        copy(reminders = reminders.copy(medSlots = reminders.medSlots.map { if (it.slot == slot) it.copy(time = time) else it }))

    @Test
    fun `en ändrad påminnelserad läggs på den lagrade listan – en annan enhets ändring på en annan rad står kvar`() = runTest {
        repository.update { it.copy(reminders = it.reminders.copy(medsEnabled = true)) }.getOrThrow()
        val loaded = repository.get().getOrThrow()
        // En annan enhet ändrar kvällsraden medan formuläret är öppet.
        repository.update { it.withSlot(Slot.EVENING, LocalTime(20, 30)) }.getOrThrow()

        repository.save(loaded, loaded.withSlot(Slot.MORNING, LocalTime(6, 15))).getOrThrow()

        val slots = repository.get().getOrThrow().reminders.medSlots.associate { it.slot to it.time }
        assertEquals(LocalTime(6, 15), slots[Slot.MORNING])
        assertEquals(LocalTime(20, 30), slots[Slot.EVENING], "den andra enhetens rad skrivs inte tillbaka")
    }

    @Test
    fun `utan lagrat att bygga på skrivs påminnelseraderna inte – övrigt skrivs och felet är Offline`() = runTest {
        val serverRows = Settings().withSlot(Slot.EVENING, LocalTime(21, 45))
        factory.store.set(path, Settings.ID, SettingsCodec.encode(serverRows), merge = false)
        val loaded = repository.get().getOrThrow() // formuläret laddades medan dokumentet fanns i cachen
        val edited = loaded.withSlot(Slot.MORNING, LocalTime(6, 0)).let { it.copy(reminders = it.reminders.copy(medsEnabled = true)) }

        val result = DefaultSettingsRepository(CachedDoc(factory, DataError.Offline)).save(loaded, edited)

        assertEquals(DataError.Offline, result.exceptionOrNull())
        val stored = repository.get().getOrThrow().reminders
        assertTrue(stored.medsEnabled, "det skalära fältet skrevs")
        assertEquals(serverRows.reminders.medSlots, stored.medSlots, "raderna skrevs inte")
    }

    @Test
    fun `ett annat fel än Offline vid läsningen av raderna – ingenting skrivs`() = runTest {
        factory.store.set(path, Settings.ID, SettingsCodec.encode(Settings()), merge = false)
        val loaded = repository.get().getOrThrow()
        val before = stored()
        val edited = loaded.withSlot(Slot.MORNING, LocalTime(6, 0)).let { it.copy(reminders = it.reminders.copy(medsEnabled = true)) }

        val result = DefaultSettingsRepository(CachedDoc(factory, DataError.PermissionDenied)).save(loaded, edited)

        assertEquals(DataError.PermissionDenied, result.exceptionOrNull())
        assertEquals(before, stored())
    }

    @Test
    fun `fram och tillbaka i snabb följd tappar inte den sista ändringen`() = runTest {
        listOf(
            async { repository.update { it.copy(theme = it.theme.copy(mode = ThemeMode.DARK)) } },
            async { repository.update { it.copy(theme = it.theme.copy(mode = ThemeMode.AUTO)) } },
        ).awaitAll().forEach { it.getOrThrow() }

        assertEquals(ThemeMode.AUTO, repository.get().getOrThrow().theme.mode)
    }

    @Test
    fun `går det lagrade inte att läsa skrivs ingenting`() = runTest {
        val signedOut = DefaultSettingsRepository(FakeCollectionFactory(store = factory.store, scope = TestUserScope(uid = null)))

        assertTrue(signedOut.update { it.copy(theme = it.theme.copy(mode = ThemeMode.LIGHT)) }.isFailure)
        assertNull(factory.store.read(path, Settings.ID))
    }
}

/** Som [FakeCollectionFactory], men dokumentet ur cachen ger [error] (t.ex. inte i cachen och offline). */
private class CachedDoc(private val fake: FakeCollectionFactory, private val error: DataError) : CollectionTable() {
    override fun <T : Identified> create(codec: DocCodec<T>, name: String, path: (uid: String?) -> String): EntityCollection<T> =
        object : EntityCollection<T> by fake.collection(codec, name, path) {
            override suspend fun cached(id: String): Result<T?> = Result.failure(error)
        }
}
