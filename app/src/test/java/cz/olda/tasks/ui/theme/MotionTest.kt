package cz.olda.tasks.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    /**
     * These are Material's published expressive motion tokens. The app copies them because
     * material3 1.4.0 keeps the scheme internal, so this test is the only thing stopping a
     * later edit from drifting away from the specification.
     */
    @Test
    fun springsCarryTheMaterialExpressiveValues() {
        assertEquals(0.8f, AppMotion.spatial<Float>().dampingRatio, 0f)
        assertEquals(380f, AppMotion.spatial<Float>().stiffness, 0f)
        assertEquals(0.6f, AppMotion.fastSpatial<Float>().dampingRatio, 0f)
        assertEquals(800f, AppMotion.fastSpatial<Float>().stiffness, 0f)
        assertEquals(0.8f, AppMotion.slowSpatial<Float>().dampingRatio, 0f)
        assertEquals(200f, AppMotion.slowSpatial<Float>().stiffness, 0f)
        assertEquals(1.0f, AppMotion.effects<Float>().dampingRatio, 0f)
        assertEquals(1600f, AppMotion.effects<Float>().stiffness, 0f)
        assertEquals(1.0f, AppMotion.fastEffects<Float>().dampingRatio, 0f)
        assertEquals(3800f, AppMotion.fastEffects<Float>().stiffness, 0f)
        assertEquals(1.0f, AppMotion.slowEffects<Float>().dampingRatio, 0f)
        assertEquals(800f, AppMotion.slowEffects<Float>().stiffness, 0f)
    }

    /** Accessibility -> Remove animations sets the scale to 0; nothing else turns motion off. */
    @Test
    fun animationsAreOffOnlyWhenTheScaleIsZero() {
        assertFalse(animationsEnabled(0f))
        assertTrue(animationsEnabled(1f))
        assertTrue(animationsEnabled(0.5f))
    }
}
