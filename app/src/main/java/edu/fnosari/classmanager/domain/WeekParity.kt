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
): LocalDateTime? = nextSlotWithTime(from, slots, weekARef)?.second

/** Earliest future slot within 15 days, with its concrete start time. */
fun nextSlotWithTime(
    from: LocalDateTime,
    slots: List<TimetableSlot>,
    weekARef: LocalDate?,
): Pair<TimetableSlot, LocalDateTime>? {
    if (slots.isEmpty()) return null
    for (offset in 0..15L) {
        val date = from.toLocalDate().plusDays(offset)
        val candidates = slots
            .filter { it.dayOfWeek == date.dayOfWeek.value && it.matchesParity(date, weekARef) }
            .map { it to LocalDateTime.of(date, LocalTime.parse(it.startTime)) }
            .filter { it.second.isAfter(from) }
            .sortedBy { it.second }
        if (candidates.isNotEmpty()) return candidates.first()
    }
    return null
}

/** Slot running right now (start inclusive, end exclusive), honoring A/B parity. */
fun currentSlot(
    now: LocalDateTime,
    slots: List<TimetableSlot>,
    weekARef: LocalDate?,
): TimetableSlot? {
    val date = now.toLocalDate()
    val time = now.toLocalTime()
    return slots.firstOrNull {
        it.dayOfWeek == date.dayOfWeek.value &&
            it.matchesParity(date, weekARef) &&
            !time.isBefore(LocalTime.parse(it.startTime)) &&
            time.isBefore(LocalTime.parse(it.endTime))
    }
}
