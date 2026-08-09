package edu.fnosari.classmanager.domain

import edu.fnosari.classmanager.data.OneOffSlot
import edu.fnosari.classmanager.data.SlotCancellation
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** One concrete course occurrence on a date (recurring slot instance or one-off). */
data class Occurrence(
    val classId: Long,
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val roomId: Long?,
    val slotId: Long?,     // set when from a recurring slot
    val oneOffId: Long?,   // set when from a one-off
)

private fun TimetableSlot.parityMatches(date: LocalDate, weekARef: LocalDate?): Boolean {
    if (weekParity == WeekParityTag.BOTH || weekARef == null) return true
    val p = parityOf(date, weekARef)
    return (weekParity == WeekParityTag.A && p == Parity.A) ||
        (weekParity == WeekParityTag.B && p == Parity.B)
}

/** All occurrences on [date], cancellations applied, one-offs included, sorted by start. */
fun occurrencesOn(
    date: LocalDate,
    slots: List<TimetableSlot>,
    cancellations: List<SlotCancellation>,
    oneOffs: List<OneOffSlot>,
    weekARef: LocalDate?,
): List<Occurrence> {
    val dateStr = date.toString()
    val cancelled = cancellations.filter { it.date == dateStr }.map { it.slotId }.toSet()
    val recurring = slots
        .filter {
            it.dayOfWeek == date.dayOfWeek.value &&
                it.parityMatches(date, weekARef) &&
                it.id !in cancelled
        }
        .map {
            Occurrence(it.classId, date, it.startTime, it.endTime, it.roomId, it.id, null)
        }
    val extra = oneOffs
        .filter { it.date == dateStr }
        .map { Occurrence(it.classId, date, it.startTime, it.endTime, it.roomId, null, it.id) }
    return (recurring + extra).sortedBy { it.startTime }
}

/** Occurrence running at [now] (start inclusive, end exclusive). */
fun currentOccurrence(
    now: LocalDateTime,
    slots: List<TimetableSlot>,
    cancellations: List<SlotCancellation>,
    oneOffs: List<OneOffSlot>,
    weekARef: LocalDate?,
): Occurrence? {
    val time = now.toLocalTime()
    return occurrencesOn(now.toLocalDate(), slots, cancellations, oneOffs, weekARef)
        .firstOrNull {
            !time.isBefore(LocalTime.parse(it.startTime)) && time.isBefore(LocalTime.parse(it.endTime))
        }
}

/** Earliest future occurrence within 15 days. */
fun nextOccurrence(
    from: LocalDateTime,
    slots: List<TimetableSlot>,
    cancellations: List<SlotCancellation>,
    oneOffs: List<OneOffSlot>,
    weekARef: LocalDate?,
): Occurrence? {
    for (offset in 0..15L) {
        val date = from.toLocalDate().plusDays(offset)
        val candidates = occurrencesOn(date, slots, cancellations, oneOffs, weekARef)
            .filter { LocalDateTime.of(date, LocalTime.parse(it.startTime)).isAfter(from) }
        if (candidates.isNotEmpty()) return candidates.first()
    }
    return null
}
