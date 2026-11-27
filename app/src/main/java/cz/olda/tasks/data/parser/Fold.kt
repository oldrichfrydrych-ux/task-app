package cz.olda.tasks.data.parser

/**
 * Lowercases [text] and strips diacritics, **one character out for every character in**, so a
 * range found in the folded text is the same range in the text Olda typed. That is what lets
 * the recognised words be cut out of the title afterwards.
 *
 * `data/TextNormaliser.kt`'s `normalise()` cannot be used here: it normalises to NFD and drops
 * the combining marks, which changes the string's length and loses the mapping. It stays as it
 * is for search, where only the comparison matters.
 */
fun fold(text: String): String = buildString(text.length) {
    for (character in text) append(foldChar(character))
}

private fun foldChar(character: Char): Char {
    val lower = character.lowercaseChar()
    return when (lower) {
        'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
        'č', 'ç' -> 'c'
        'ď' -> 'd'
        'é', 'ě', 'è', 'ê', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ľ', 'ł' -> 'l'
        'ň', 'ñ' -> 'n'
        'ó', 'ô', 'ò', 'ö', 'õ' -> 'o'
        'ř' -> 'r'
        'š', 'ś' -> 's'
        'ť' -> 't'
        'ú', 'ů', 'ù', 'û', 'ü' -> 'u'
        'ý', 'ÿ' -> 'y'
        'ž', 'ź' -> 'z'
        else -> lower
    }
}
