package cz.olda.tasks.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CLAUDE.md, Fixed choices: "Keep the core ready for Windows (Phase 8): no `android.*`
 * imports and no `Context` in `data/` or the parser."
 *
 * Phase 8 moves this package into a shared Kotlin Multiplatform module, where an Android
 * class is not merely unwanted but unavailable. The rule is cheap to hold while the
 * package is small and expensive to restore once a dozen files have leaned on a
 * `Context`, so it gets a test rather than good intentions.
 */
class DataLayerIsPlatformFreeTest {

    /** Gradle runs unit tests from the module directory; a checkout root is allowed too. */
    private fun dataSources(): List<File> {
        val dir = listOf(
            File("src/main/java/cz/olda/tasks/data"),
            File("app/src/main/java/cz/olda/tasks/data"),
        ).firstOrNull { it.isDirectory }
        assertTrue(
            "cannot find the data package from ${File("").absolutePath}",
            dir != null,
        )
        return dir!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Without this, a broken path would make the two rules below pass over an empty list
     * and the guard would quietly stop guarding.
     */
    @Test
    fun theTestCanSeeTheDataPackage() {
        val files = dataSources()
        assertTrue("found only ${files.size} Kotlin files in data/", files.size >= 10)
    }

    @Test
    fun noFileInDataImportsAnAndroidClass() {
        val offenders = dataSources().mapNotNull { file ->
            val bad = file.readLines().map { it.trim() }.filter { it.startsWith("import android.") }
            if (bad.isEmpty()) null else "${file.name} -> ${bad.joinToString("; ")}"
        }.sorted()
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun noFileInDataMentionsAContext() {
        val offenders = dataSources()
            .filter { Regex("\\bContext\\b").containsMatchIn(it.readText()) }
            .map { it.name }
            .sorted()
        assertEquals(emptyList<String>(), offenders)
    }
}
