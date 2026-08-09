package edu.fnosari.classmanager.domain

import edu.fnosari.classmanager.data.OneOffSlot
import edu.fnosari.classmanager.data.SlotCancellation
import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OccurrencesTest {
    private val refMonday = LocalDate.of(2026, 8, 3) // week A
    private val monday = LocalDate.of(2026, 8, 3)

    private fun slot(id: Long, day: Int, start: String = "08:00", end: String = "09:00",
                     parity: WeekParityTag = WeekParityTag.BOTH, classId: Long = 1) =
        TimetableSlot(id = id, classId = classId, dayOfWeek = day, startTime = start,
            endTime = end, weekParity = parity)

    @Test fun recurringSlotProducesOccurrence() {
        val occ = occurrencesOn(monday, listOf(slot(1, 1)), emptyList(), emptyList(), refMonday)
        assertEquals(1, occ.size)
        assertEquals(1L, occ[0].classId)
        assertEquals("08:00", occ[0].startTime)
        assertEquals(1L, occ[0].slotId)
    }

    @Test fun cancellationSuppressesOccurrenceOnThatDateOnly() {
        val cancels = listOf(SlotCancellation(slotId = 1, date = "2026-08-03"))
        assertTrue(occurrencesOn(monday, listOf(slot(1, 1)), cancels, emptyList(), refMonday).isEmpty())
        // next week unaffected
        assertEquals(1, occurrencesOn(monday.plusDays(7), listOf(slot(1, 1)), cancels, emptyList(), refMonday).size)
    }

    @Test fun oneOffAppearsOnItsDateOnly() {
        val oneOff = OneOffSlot(id = 5, classId = 2, date = "2026-08-03", startTime = "10:00", endTime = "11:00")
        val occ = occurrencesOn(monday, emptyList(), emptyList(), listOf(oneOff), refMonday)
        assertEquals(1, occ.size)
        assertEquals(2L, occ[0].classId)
        assertNull(occ[0].slotId)
        assertEquals(5L, occ[0].oneOffId)
        assertTrue(occurrencesOn(monday.plusDays(1), emptyList(), emptyList(), listOf(oneOff), refMonday).isEmpty())
    }

    @Test fun sortedByStartTime() {
        val occ = occurrencesOn(
            monday,
            listOf(slot(1, 1, "14:00", "15:00"), slot(2, 1, "08:00", "09:00")),
            emptyList(),
            listOf(OneOffSlot(classId = 3, date = "2026-08-03", startTime = "10:00", endTime = "11:00")),
            refMonday,
        )
        assertEquals(listOf("08:00", "10:00", "14:00"), occ.map { it.startTime })
    }

    @Test fun parityRespected() {
        val occ = occurrencesOn(monday, listOf(slot(1, 1, parity = WeekParityTag.B)),
            emptyList(), emptyList(), refMonday)
        assertTrue(occ.isEmpty())
        assertEquals(1, occurrencesOn(monday.plusDays(7), listOf(slot(1, 1, parity = WeekParityTag.B)),
            emptyList(), emptyList(), refMonday).size)
    }

    @Test fun currentOccurrenceHonorsCancellation() {
        val now = LocalDateTime.of(2026, 8, 3, 8, 30)
        val cancels = listOf(SlotCancellation(slotId = 1, date = "2026-08-03"))
        assertNull(currentOccurrence(now, listOf(slot(1, 1)), cancels, emptyList(), refMonday))
        assertEquals(1L, currentOccurrence(now, listOf(slot(1, 1)), emptyList(), emptyList(), refMonday)?.classId)
    }

    @Test fun currentOccurrenceFindsOneOff() {
        val now = LocalDateTime.of(2026, 8, 3, 10, 30)
        val oneOff = OneOffSlot(classId = 9, date = "2026-08-03", startTime = "10:00", endTime = "11:00")
        assertEquals(9L, currentOccurrence(now, emptyList(), emptyList(), listOf(oneOff), refMonday)?.classId)
    }

    @Test fun nextOccurrenceSkipsCancelledAndFindsFollowingWeek() {
        val from = LocalDateTime.of(2026, 8, 3, 7, 0)
        val cancels = listOf(SlotCancellation(slotId = 1, date = "2026-08-03"))
        val r = nextOccurrence(from, listOf(slot(1, 1)), cancels, emptyList(), refMonday)
        assertEquals(LocalDate.of(2026, 8, 10), r?.date)
    }
}
