package se.partee71.dagboken.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyLabelTest {

    @Test fun `positive energy gets plus prefix`() = assertEquals("+5", energyLabel(5))

    @Test fun `negative energy keeps minus sign`() = assertEquals("-3", energyLabel(-3))

    @Test fun `zero has no prefix`() = assertEquals("0", energyLabel(0))

    @Test fun `one has plus prefix`() = assertEquals("+1", energyLabel(1))

    @Test fun `max scale positive`() = assertEquals("+10", energyLabel(10))

    @Test fun `max scale negative`() = assertEquals("-10", energyLabel(-10))
}
