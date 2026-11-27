package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The suite `docs/design.md` asks for: 100+ real phrases in both languages, one row per phrase,
 * so adding a phrase the parser missed is adding a row.
 *
 * Everything is read at a fixed moment — **Friday 27 November 2026, 16:30** — against Olda's own
 * five lists, and uses his own vocabulary. Olda reviews this list and adds what it is missing;
 * see the last task of `docs/superpowers/plans/2026-11-27-phase-2-quick-add-parser.md`.
 */
class QuickAddPhraseSuiteTest {

    private val lists = listOf("Inbox", "☀️Osobní", "🌊Práce", "🎓Škola", "🧊Someday")
    private val now = LocalDateTime.of(2026, 11, 27, 16, 30)

    private val today = LocalDate.of(2026, 11, 27)
    private val tomorrow = LocalDate.of(2026, 11, 28)
    private fun on(month: Int, day: Int, year: Int = 2026) = LocalDate.of(year, month, day)
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val prace = "🌊Práce"
    private val osobni = "☀️Osobní"
    private val skola = "🎓Škola"
    private val someday = "🧊Someday"

    private data class Phrase(
        val input: String,
        val title: String,
        val date: LocalDate? = null,
        val minute: Int? = null,
        val list: String? = null,
        val tags: List<String> = emptyList(),
        val priority: Int? = null,
        val recurrence: String? = null,
    )

    private val phrases: List<Phrase> by lazy {
        dayWords + weekdays + calendarDates + times + relative + repeats + symbols +
            mixedLanguage + manyFields + deliberatelyNotRecognised + fromTheReview
    }

    private val dayWords = listOf(
        Phrase("dnes zavolat na urad", "zavolat na urad", today),
        Phrase("dneska zavolat", "zavolat", today),
        Phrase("today call the office", "call the office", today),
        Phrase("zitra zavolat", "zavolat", tomorrow),
        Phrase("zítra zavolat", "zavolat", tomorrow),
        Phrase("zejtra zavolat", "zavolat", tomorrow),
        Phrase("tomorrow call", "call", tomorrow),
        Phrase("pozitri zavolat", "zavolat", on(11, 29)),
        Phrase("pozítří zavolat", "zavolat", on(11, 29)),
        Phrase("day after tomorrow call", "call", on(11, 29)),
        Phrase("dnes koupit zimni kabat", "koupit zimni kabat", today),
        Phrase("zitra napsat na ekonomku", "napsat na ekonomku", tomorrow),
    )

    private val weekdays = listOf(
        Phrase("v pondeli porada", "porada", on(11, 30)),
        Phrase("v pondělí porada", "porada", on(11, 30)),
        Phrase("v utery zavolat", "zavolat", on(12, 1)),
        Phrase("v úterý zavolat", "zavolat", on(12, 1)),
        Phrase("ve stredu zavolat", "zavolat", on(12, 2)),
        Phrase("ve středu zavolat", "zavolat", on(12, 2)),
        Phrase("ve ctvrtek zavolat", "zavolat", on(12, 3)),
        // Today is a Friday, and the nearest such day includes today — Olda's call on 27 Nov.
        Phrase("v patek zavolat", "zavolat", today),
        Phrase("v sobotu zavolat", "zavolat", tomorrow),
        Phrase("v nedeli zavolat", "zavolat", on(11, 29)),
        Phrase("on monday call", "call", on(11, 30)),
        Phrase("next friday call", "call", on(12, 4)),
        Phrase("pristi patek zavolat", "zavolat", on(12, 4)),
        Phrase("příští pondělí porada", "porada", on(11, 30)),
        Phrase("this sunday call", "call", on(11, 29)),
    )

    private val calendarDates = listOf(
        Phrase("24. 12. koupit darky", "koupit darky", on(12, 24)),
        Phrase("24.12. koupit darky", "koupit darky", on(12, 24)),
        Phrase("24. 12 koupit darky", "koupit darky", on(12, 24)),
        Phrase("24.12.2026 koupit darky", "koupit darky", on(12, 24)),
        Phrase("24. 12. 2026 koupit darky", "koupit darky", on(12, 24)),
        Phrase("3.1.2027 revize", "revize", on(1, 3, 2027)),
        Phrase("24. prosince darky", "darky", on(12, 24)),
        Phrase("24. prosinec darky", "darky", on(12, 24)),
        Phrase("1. ledna novy rok", "novy rok", on(1, 1, 2027)),
        Phrase("15. brezna danove priznani", "danove priznani", on(3, 15, 2027)),
        Phrase("Dec 24 presents", "presents", on(12, 24)),
        Phrase("24 December presents", "presents", on(12, 24)),
        Phrase("dec 24 presents", "presents", on(12, 24)),
        Phrase("30. 11. odevzdat dokumentaci", "odevzdat dokumentaci", on(11, 30)),
    )

    private val times = listOf(
        Phrase("zitra ve 3 zavolat", "zavolat", tomorrow, at(15)),
        Phrase("zitra v 15 zavolat", "zavolat", tomorrow, at(15)),
        Phrase("zitra v 15:30 zavolat", "zavolat", tomorrow, at(15, 30)),
        Phrase("zitra v 15.30 zavolat", "zavolat", tomorrow, at(15, 30)),
        Phrase("zitra 15:30 zavolat", "zavolat", tomorrow, at(15, 30)),
        Phrase("zitra v 8 zavolat", "zavolat", tomorrow, at(8)),
        Phrase("zitra v 21:45 film", "film", tomorrow, at(21, 45)),
        Phrase("zitra ve 3:30 zavolat", "zavolat", tomorrow, at(15, 30)),
        Phrase("zitra v 15h zavolat", "zavolat", tomorrow, at(15)),
        Phrase("zitra ve 3 hod zavolat", "zavolat", tomorrow, at(15)),
        Phrase("tomorrow at 3 call", "call", tomorrow, at(15)),
        Phrase("tomorrow at 3:30 pm call", "call", tomorrow, at(15, 30)),
        Phrase("tomorrow 3pm call", "call", tomorrow, at(15)),
        Phrase("tomorrow 7am call", "call", tomorrow, at(7)),
        Phrase("zitra ve 3 rano zavolat", "zavolat", tomorrow, at(3)),
        Phrase("zitra rano zavolat", "zavolat", tomorrow, at(8)),
        Phrase("zitra dopoledne zavolat", "zavolat", tomorrow, at(10)),
        Phrase("zitra v poledne obed", "obed", tomorrow, at(12)),
        Phrase("zitra odpoledne zavolat", "zavolat", tomorrow, at(15)),
        Phrase("zitra vecer film", "film", tomorrow, at(19)),
        Phrase("zitra v noci sluzba", "sluzba", tomorrow, at(22)),
        Phrase("tonight call Petr", "call Petr", today, at(19)),
        // A bare time that has already gone means the next one — Olda's call on 27 Nov.
        Phrase("ve 3 zavolat", "zavolat", tomorrow, at(15)),
        Phrase("dnes ve 3 zavolat", "zavolat", today, at(15)),
    )

    private val relative = listOf(
        Phrase("za 3 dny zavolat", "zavolat", on(11, 30)),
        Phrase("za den zavolat", "zavolat", tomorrow),
        Phrase("za 5 dni zavolat", "zavolat", on(12, 2)),
        Phrase("za tyden porada", "porada", on(12, 4)),
        Phrase("za 2 tydny porada", "porada", on(12, 11)),
        Phrase("za mesic platba", "platba", on(12, 27)),
        Phrase("za rok revize", "revize", on(11, 27, 2027)),
        Phrase("in 3 days call", "call", on(11, 30)),
        Phrase("in a week call", "call", on(12, 4)),
        Phrase("in a month pay", "pay", on(12, 27)),
        Phrase("za hodinu zavolat", "zavolat", today, at(17, 30)),
        Phrase("za 20 minut zavolat", "zavolat", today, at(16, 50)),
        Phrase("in 30 min call", "call", today, at(17)),
        Phrase("in an hour call", "call", today, at(17, 30)),
    )

    private val repeats = listOf(
        Phrase("kazdy den cvicit", "cvicit", today, recurrence = "FREQ=DAILY"),
        Phrase("každý den cvicit", "cvicit", today, recurrence = "FREQ=DAILY"),
        Phrase("denne cvicit", "cvicit", today, recurrence = "FREQ=DAILY"),
        Phrase("every day exercise", "exercise", today, recurrence = "FREQ=DAILY"),
        Phrase("daily exercise", "exercise", today, recurrence = "FREQ=DAILY"),
        Phrase("kazde pondeli porada", "porada", on(11, 30), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
        Phrase("každé pondělí porada", "porada", on(11, 30), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
        Phrase("every monday meeting", "meeting", on(11, 30), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
        Phrase("kazdy patek porada", "porada", today, recurrence = "FREQ=WEEKLY;BYDAY=FR"),
        Phrase("kazdy tyden porada", "porada", today, recurrence = "FREQ=WEEKLY"),
        Phrase("kazdy mesic platba", "platba", today, recurrence = "FREQ=MONTHLY"),
        Phrase("kazdy rok revize", "revize", today, recurrence = "FREQ=YEARLY"),
        Phrase("kazde 2 dny cvicit", "cvicit", today, recurrence = "FREQ=DAILY;INTERVAL=2"),
        Phrase("every 3 days exercise", "exercise", today, recurrence = "FREQ=DAILY;INTERVAL=3"),
        Phrase("kazde 2 tydny porada", "porada", today, recurrence = "FREQ=WEEKLY;INTERVAL=2"),
        // 09:00 next Monday is still ahead, so the implied first day does not roll on.
        Phrase("kazde pondeli v 9 porada", "porada", on(11, 30), at(9), recurrence = "FREQ=WEEKLY;BYDAY=MO"),
    )

    private val symbols = listOf(
        Phrase("zavolat #prace", "zavolat", list = prace),
        Phrase("zavolat #Práce", "zavolat", list = prace),
        Phrase("zavolat #pra", "zavolat", list = prace),
        Phrase("ukol #skola", "ukol", list = skola),
        Phrase("ukol #osobni", "ukol", list = osobni),
        Phrase("ukol #someday", "ukol", list = someday),
        Phrase("zavolat @telefon", "zavolat", tags = listOf("telefon")),
        Phrase("zavolat @telefon @rychle", "zavolat", tags = listOf("telefon", "rychle")),
        Phrase("ukol @cekam #prace", "ukol", list = prace, tags = listOf("cekam")),
        Phrase("@telefon zavolat ekonomce", "zavolat ekonomce", tags = listOf("telefon")),
        Phrase("zavolat p1", "zavolat", priority = 1),
        Phrase("zavolat p2", "zavolat", priority = 2),
        Phrase("zavolat p3", "zavolat", priority = 3),
        Phrase("zavolat p4", "zavolat", priority = 4),
        Phrase("zavolat P1", "zavolat", priority = 1),
        Phrase("zavolat #prace @telefon p1", "zavolat", list = prace, tags = listOf("telefon"), priority = 1),
    )

    private val mixedLanguage = listOf(
        Phrase("tomorrow ve 3 zavolat", "zavolat", tomorrow, at(15)),
        Phrase("zitra at 3 pm call", "call", tomorrow, at(15)),
        Phrase("next friday zavolat #prace", "zavolat", on(12, 4), list = prace),
        Phrase("kazdy den exercise", "exercise", today, recurrence = "FREQ=DAILY"),
        Phrase("every monday porada #prace", "porada", on(11, 30), list = prace, recurrence = "FREQ=WEEKLY;BYDAY=MO"),
        Phrase("in 3 days zavolat na urad", "zavolat na urad", on(11, 30)),
        Phrase("za 3 dny call the office", "call the office", on(11, 30)),
        Phrase("today vecer film", "film", today, at(19)),
        Phrase("zitra morning zavolat", "zavolat", tomorrow, at(8)),
        Phrase("on monday v 9 porada", "porada", on(11, 30), at(9)),
    )

    private val manyFields = listOf(
        Phrase(
            "pristi patek v 9:30 odevzdat dokumentaci #skola @urgent p1",
            "odevzdat dokumentaci", on(12, 4), at(9, 30), skola, listOf("urgent"), 1,
        ),
        Phrase("zitra ve 3 zavolat na urad #prace", "zavolat na urad", tomorrow, at(15), prace),
        Phrase(
            "kazde pondeli v 9 porada #prace p2",
            "porada", on(11, 30), at(9), prace, priority = 2, recurrence = "FREQ=WEEKLY;BYDAY=MO",
        ),
        Phrase("24. 12. v 18:00 vecere #osobni", "vecere", on(12, 24), at(18), osobni),
        Phrase(
            "za 2 dny v 15:30 zavolat ekonomce #prace p3 @telefon",
            "zavolat ekonomce", on(11, 29), at(15, 30), prace, listOf("telefon"), 3,
        ),
        Phrase("tomorrow at 8am gym #osobni @zdravi", "gym", tomorrow, at(8), osobni, listOf("zdravi")),
        // 08:00 today has gone, so the day the repeat implied moves on by one day.
        Phrase("every day rano cvicit #osobni", "cvicit", tomorrow, at(8), osobni, recurrence = "FREQ=DAILY"),
        Phrase("v nedeli dopoledne uklidit #osobni p4", "uklidit", on(11, 29), at(10), osobni, priority = 4),
        Phrase("3. 12. v 11:00 doktor #osobni", "doktor", on(12, 3), at(11), osobni),
        Phrase("next friday 15:00 review #prace @work p2", "review", on(12, 4), at(15), prace, listOf("work"), 2),
    )

    /**
     * Phrases that must come out as ordinary words. Some are Olda's real titles, some are the
     * forms the spec deliberately left out — each one of those is a row away from working, once
     * he asks for it.
     */
    private val deliberatelyNotRecognised = listOf(
        Phrase("koupit 3 rohliky", "koupit 3 rohliky"),
        Phrase("pondeli resetovat", "pondeli resetovat"),
        Phrase("Číst při Týdenním resetu (pondělí).", "Číst při Týdenním resetu (pondělí)."),
        Phrase("Vyzkoušet speech stuff s ai", "Vyzkoušet speech stuff s ai"),
        Phrase("zavolat #xyz", "zavolat #xyz"),
        Phrase("zavolat #s", "zavolat #s"),
        Phrase("24.12 koupit darky", "24.12 koupit darky"),
        Phrase("napsat na jan@firma.cz", "napsat na jan@firma.cz"),
        Phrase("zavolat p5", "zavolat p5"),
        Phrase("tisknout typ1a", "tisknout typ1a"),
        // Left out on purpose, see "Deliberately left out" in the design doc.
        Phrase("v pul ctvrte zavolat", "v pul ctvrte zavolat"),
        Phrase("o vikendu uklidit", "o vikendu uklidit"),
        Phrase("za 3 pracovni dny odpovedet", "za 3 pracovni dny odpovedet"),
        Phrase("Alea, Více rolí?", "Alea, Více rolí?"),
        Phrase("Penzijní spoření", "Penzijní spoření"),
    )

    /**
     * The lines a fresh reviewer found by running the parser over ordinary Czech and English on
     * 27 Nov. Every one of them used to lose a word from the title and invent a date, a time or
     * a repeat — the failure the spec names as its worst. They are here so they cannot come back.
     */
    private val fromTheReview = listOf(
        // "tydne" is both "weekly" and the genitive of "week", and folding makes them one word.
        Phrase("do konce týdne uklidit", "do konce týdne uklidit"),
        Phrase("report za prvni polovinu tydne", "report za prvni polovinu tydne"),
        Phrase("v polovine tydne zavolat", "v polovine tydne zavolat"),
        // A preposition has to be a word of its own, not glued to a number: these are versions.
        Phrase("nasadit v2 na produkci", "nasadit v2 na produkci"),
        Phrase("opravit Fluxus v3", "opravit Fluxus v3"),
        Phrase("upgrade na v15", "upgrade na v15"),
        // A decimal is one number, not an hour and a stray fraction.
        Phrase("zkontrolovat verzi v2.1", "zkontrolovat verzi v2.1"),
        Phrase("zaplatit v 1.000 Kc", "zaplatit v 1.000 Kc"),
        Phrase("v 7.5 zavolat", "v 7.5 zavolat"),
        // "od" used to be read as a time preposition, which ate the date behind it.
        Phrase("Dovolena od 3. 12.", "Dovolena od", on(12, 3)),
        Phrase("kolem 15. 3. zaplatit", "kolem zaplatit", on(3, 15, 2027)),
        // The English preposition in front of a named hour belongs to the time, not the title.
        Phrase("call tomorrow at noon", "call", tomorrow, at(12)),
        Phrase("call at night", "call", today, at(22)),
        Phrase("wake up in the morning", "wake up", tomorrow, at(8)),
        // "hodiny" is as common as "hod" and was left behind in the title.
        Phrase("zavolat ve 3 hodiny", "zavolat", tomorrow, at(15)),
        Phrase("zavolat ve 3 hodiny odpoledne", "zavolat", tomorrow, at(15)),
        Phrase("porada v 10 hodin", "porada", tomorrow, at(10)),
        // Czech puts the weekday in the accusative after "každý", and the feminine days change
        // the pronoun too: "každou středu". This is the likeliest phrase he will actually type.
        Phrase("každou středu bazen", "bazen", on(12, 2), recurrence = "FREQ=WEEKLY;BYDAY=WE"),
        Phrase("kazdou nedeli uklidit", "uklidit", on(11, 29), recurrence = "FREQ=WEEKLY;BYDAY=SU"),
        Phrase("kazdou sobotu nakup", "nakup", tomorrow, recurrence = "FREQ=WEEKLY;BYDAY=SA"),
    )

    @Test
    fun everyPhraseParses() {
        val failures = phrases.mapNotNull { phrase ->
            val got = parseQuickAdd(phrase.input, now, lists)
            val want = ParsedInput(
                title = phrase.title,
                dueDate = phrase.date,
                dueMinute = phrase.minute,
                listName = phrase.list,
                tagNames = phrase.tags,
                priority = phrase.priority,
                recurrence = phrase.recurrence,
                tokens = got.tokens,
            )
            if (got == want) null else "\"${phrase.input}\"\n    want $want\n    got  $got"
        }
        assertEquals("${failures.size} of ${phrases.size} phrases failed", "", failures.joinToString("\n"))
    }

    @Test
    fun theSuiteIsBigEnoughAndCoversBothLanguages() {
        assertTrue("only ${phrases.size} phrases", phrases.size >= 100)
        assertTrue("repeats: ${phrases.count { it.recurrence != null }}", phrases.count { it.recurrence != null } >= 8)
        assertTrue("times: ${phrases.count { it.minute != null }}", phrases.count { it.minute != null } >= 25)
        assertTrue("tags: ${phrases.count { it.tags.isNotEmpty() }}", phrases.count { it.tags.isNotEmpty() } >= 8)
        assertTrue("lists: ${phrases.count { it.list != null }}", phrases.count { it.list != null } >= 8)
        assertTrue(
            "not recognised: ${deliberatelyNotRecognised.size}",
            deliberatelyNotRecognised.size >= 10,
        )
    }
}
