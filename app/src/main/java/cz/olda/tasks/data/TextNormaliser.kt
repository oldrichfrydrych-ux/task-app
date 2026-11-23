package cz.olda.tasks.data

import java.text.Normalizer

private val combiningMarks = Regex("""\p{Mn}+""")

/** Lowercases [text] and strips diacritics, so search matches "urad" against "úřad". */
fun normalise(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "").lowercase()
