package cz.olda.tasks.data.parser

import java.time.LocalDate
import java.time.Period
import java.time.temporal.TemporalAdjusters

/** "každý", "každá", "každé" and their diacritic-free spellings all count. */
private const val EVERY = "(?:kazdy|kazda|kazde|every)"

private val daily = wordPattern("$EVERY\\s+day|kazdy\\s+den|kazda\\s+den|kazde\\s+den|denne|daily")
private val weekly = wordPattern("$EVERY\\s+(?:week|tyden)|tydne|weekly")
private val monthly = wordPattern("$EVERY\\s+(?:month|mesic)|mesicne|monthly")
private val yearly = wordPattern("$EVERY\\s+(?:year|rok)|rocne|yearly")

/** Longest first, so the regex cannot stop at "mon" inside "monday". */
private val anyWeekday = weekdayNames.keys.sortedByDescending { it.length }.joinToString("|")
private val weeklyOnADay = wordPattern("$EVERY\\s+($anyWeekday)")

private val everyNDays = wordPattern("$EVERY\\s+(\\d{1,2})\\s+(?:dny|dni|dnu|den|days|day)")
private val everyNWeeks = wordPattern("$EVERY\\s+(\\d{1,2})\\s+(?:tydny|tydnu|tyden|weeks|week)")

/**
 * A repeat, recognised and stored as an RRULE fragment. Nothing acts on it until Phase 3, which
 * creates the next task when one is ticked.
 *
 * Every repeat also carries the first day it implies and one period of itself, because a repeat
 * with no first occurrence is something Phase 3 cannot act on. It **offers** that day rather
 * than claiming it (see [Candidate.impliedDate]), so "dnes kazdy den ve 3" keeps the day Olda
 * typed.
 */
internal fun recurrenceCandidates(folded: String, today: LocalDate): List<Candidate> =
    daily.candidates(folded) { Repeat("FREQ=DAILY", today, Period.ofDays(1)) } +
        weekly.candidates(folded) { Repeat("FREQ=WEEKLY", today, Period.ofWeeks(1)) } +
        monthly.candidates(folded) { Repeat("FREQ=MONTHLY", today, Period.ofMonths(1)) } +
        yearly.candidates(folded) { Repeat("FREQ=YEARLY", today, Period.ofYears(1)) } +
        weeklyOnADay.candidates(folded) { groups ->
            val day = weekdayNames[groups[1]] ?: return@candidates null
            Repeat(
                rule = "FREQ=WEEKLY;BYDAY=${day.name.take(2)}",
                date = today.with(TemporalAdjusters.nextOrSame(day)),
                rollOn = Period.ofWeeks(1),
            )
        } +
        everyNDays.candidates(folded) { groups ->
            val count = groups[1].toIntOrNull() ?: return@candidates null
            Repeat("FREQ=DAILY;INTERVAL=$count", today, Period.ofDays(count))
        } +
        everyNWeeks.candidates(folded) { groups ->
            val count = groups[1].toIntOrNull() ?: return@candidates null
            Repeat("FREQ=WEEKLY;INTERVAL=$count", today, Period.ofWeeks(count))
        }

private class Repeat(val rule: String, val date: LocalDate, val rollOn: Period)

private fun Regex.candidates(folded: String, read: (List<String>) -> Repeat?): List<Candidate> =
    findAll(folded).mapNotNull { match ->
        val repeat = read(match.groupValues) ?: return@mapNotNull null
        Candidate(
            start = match.range.first,
            end = match.range.last + 1,
            kind = TokenKind.RECURRENCE,
            recurrence = repeat.rule,
            impliedDate = repeat.date,
            rollOn = repeat.rollOn,
        )
    }.toList()
