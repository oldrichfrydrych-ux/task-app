package cz.olda.tasks.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldTest {

    @Test
    fun stripsCzechDiacriticsAndCase() {
        assertEquals("zitra ve 3 zavolat na urad", fold("Zítra ve 3 zavolat na úřad"))
        assertEquals("prilis zlutoucky kun upel dabelske ody", fold("Příliš žluťoučký kůň úpěl ďábelské ódy"))
    }

    /**
     * The whole reason this is not `data/TextNormaliser.kt`'s `normalise()`: a range found in
     * the folded text has to be the same range in what Olda typed, so the recognised words can
     * be cut out of the title. NFD normalisation changes the length and loses that.
     */
    @Test
    fun keepsOneCharacterPerCharacter() {
        listOf("Zítra", "ÚŘAD", "Číst při Týdenním resetu (pondělí).", "🌊Práce", "")
            .forEach { assertEquals(it, it.length, fold(it).length) }
    }

    @Test
    fun leavesEverythingElseWhereItIs() {
        assertEquals("#prace @telefon p1 15:30", fold("#prace @telefon P1 15:30"))
    }
}
