package edu.fnosari.classmanager.domain

import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class Parity { A, B }

private fun mondayOf(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

fun parityOf(date: LocalDate, weekARef: LocalDate): Parity {
    val weeks = ChronoUnit.WEEKS.between(mondayOf(weekARef), mondayOf(date))
    return if (Math.floorMod(weeks, 2L) == 0L) Parity.A else Parity.B
}

private fun TimetableSlot.matchesParity(date: LocalDate, weekARef: LocalDate?): Boolean {
    if (weekParity == WeekParityTag.BOTH || weekARef == null) return true
    val p = parityOf(date, weekARef)
    return (weekParity == WeekParityTag.A && p == Parity.A) ||
        (weekParity == WeekParityTag.B && p == Parity.B)
}

fun nextLessonStart(
    from: LocalDateTime,
    slots: List<TimetableSlot>,
    weekARef: LocalDate?,
): LocalDateTime? {
    if (slots.isEmpty()) return null
    for (offset in 0..15L) {
        val date = from.toLocalDate().plusDays(offset)
        val candidates = slots
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.matchesParity(date, weekARef) }
            .map { LocalDateTime.of(date, LocalTime.parse(it.startTime)) }
            .filter { it.isAfter(from) }
            .sorted()
        if (candidates.isNotEmpty()) return candidates.first()
    }
    return null
}
