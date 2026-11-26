package cz.olda.tasks.ui.lists

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The letter in a list's tile. Olda's own lists are named with an emoji in front, and the first
 * version of this took one `Char` - half of a surrogate pair - so every tile drew a replacement
 * box on his phone. The emoji is the thing worth protecting here; the plain letters are the easy
 * case.
 */
class FirstCharacterTest {

    @Test
    fun aPlainNameGivesItsFirstLetterInCapitals() {
        assertEquals("P", firstCharacter("Práce"))
    }

    @Test
    fun aCzechLetterKeepsItsDiacritics() {
        assertEquals("Š", firstCharacter("Škola"))
    }

    @Test
    fun anEmojiComesBackWhole() {
        assertEquals("🌊", firstCharacter("🌊Práce"))
    }

    /** A sun with a variation selector: two code points, one character on screen. */
    @Test
    fun anEmojiWithAVariationSelectorKeepsIt() {
        assertEquals("☀️", firstCharacter("☀️Osobní"))
    }

    @Test
    fun leadingSpaceIsIgnoredAndAnEmptyNameGivesNothing() {
        assertEquals("A", firstCharacter("  Auto"))
        assertEquals("", firstCharacter("   "))
    }
}
