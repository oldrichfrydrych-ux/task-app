package cz.olda.tasks.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexColorTest {
    @Test fun acceptsWithAndWithoutHash() {
        assertEquals(Color(0xFF3D6BE0), parseHex("#3d6be0"))
        assertEquals(Color(0xFF3D6BE0), parseHex("3D6BE0"))
    }

    @Test fun rejectsBadInput() {
        listOf("fff", "#GGGGGG", "", "#3D6BE", "#3D6BE0FF").forEach { assertNull(it, parseHex(it)) }
    }

    @Test fun roundTripsThroughHex() { assertEquals("#3D6BE0", parseHex("#3d6be0")!!.toHex()) }
}
