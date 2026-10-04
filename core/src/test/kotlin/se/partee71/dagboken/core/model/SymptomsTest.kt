package se.partee71.dagboken.core.model

import kotlin.test.assertEquals
import org.junit.Test

/** 3.x `somatiska` beräknas ur symptomen och persisteras inte (DAT-6). */
class SymptomsTest {

    @Test
    fun `summan av poängen är somatiska`() {
        assertEquals(0, emptyList<SymptomScore>().somatic)
        assertEquals(7, listOf(SymptomScore("huvudvark", 4), SymptomScore("ovrigt", 3, "Stel nacke")).somatic)
    }
}
