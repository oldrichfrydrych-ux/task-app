package cz.olda.tasks.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormaliserTest {
    @Test fun stripsCzechDiacriticsAndCase() {
        assertEquals("zitra zavolat na urad", normalise("Zítra zavolat na úřad"))
        assertEquals("prilis zlutoucky kun upel dabelske ody", normalise("Příliš žluťoučký kůň úpěl ďábelské ódy"))
    }

    @Test fun leavesPlainTextAlone() { assertEquals("call bob", normalise("Call Bob")) }

    @Test fun handlesEmptyText() { assertEquals("", normalise("")) }
}
