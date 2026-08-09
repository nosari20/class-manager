package edu.fnosari.classmanager.domain

import edu.fnosari.classmanager.data.TimetableSlot
import edu.fnosari.classmanager.data.WeekParityTag
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekParityTest {
    // 2026-08-03 is a Monday. Declare its week = A.
    private val refMonday = LocalDate.of(2026, 8, 3)

    @Test fun sameWeekIsA() {
        assertEquals(Parity.A, parityOf(LocalDate.of(2026, 8, 9), refMonday)) // Sunday same week
    }

    @Test fun nextWeekIsB() {
        assertEquals(Parity.B, parityOf(LocalDate.of(2026, 8, 10), refMonday)) // next Monday
    }

    @Test fun pastWeeksAlternateToo() {
        assertEquals(Parity.B, parityOf(LocalDate.of(2026, 7, 27), refMonday)) // Monday before ref
        assertEquals(Parity.A, parityOf(LocalDate.of(2026, 7, 20), refMonday))
    }

    @Test fun yearBoundaryStable() {
        // 2026-12-28 (Mon) .. 2027-01-03 belong to one week; parity must not jump inside it
        val p1 = parityOf(LocalDate.of(2026, 12, 28), refMonday)
        val p2 = parityOf(LocalDate.of(2027, 1, 3), refMonday)
        assertEquals(p1, p2)
    }

    private fun slot(day: Int, start: String, parity: WeekParityTag = WeekParityTag.BOTH) =
        TimetableSlot(classId = 1, dayOfWeek = day, startTime = start, endTime = "10:00", weekParity = parity)

    @Test fun nextLessonSameDayLaterSlot() {
        val from = LocalDateTime.of(2026, 8, 3, 7, 0) // Mon 07:00
        val next = nextLessonStart(from, listOf(slot(1, "08:00")), refMonday)
        assertEquals(LocalDateTime.of(2026, 8, 3, 8, 0), next)
    }

    @Test fun nextLessonSkipsPastSlotToday() {
        val from = LocalDateTime.of(2026, 8, 3, 9, 0)
        val next = nextLessonStart(from, listOf(slot(1, "08:00")), refMonday)
        assertEquals(LocalDateTime.of(2026, 8, 10, 8, 0), next) // next Monday
    }

    @Test fun nextLessonHonorsParity() {
        // slot only on B weeks; from A-week Monday -> lands 7 days later
        val from = LocalDateTime.of(2026, 8, 3, 7, 0)
        val next = nextLessonStart(from, listOf(slot(1, "08:00", WeekParityTag.B)), refMonday)
        assertEquals(LocalDateTime.of(2026, 8, 10, 8, 0), next)
    }

    @Test fun parityIgnoredWhenNoRef() {
        val from = LocalDateTime.of(2026, 8, 3, 7, 0)
        val next = nextLessonStart(from, listOf(slot(1, "08:00", WeekParityTag.B)), null)
        assertEquals(LocalDateTime.of(2026, 8, 3, 8, 0), next)
    }

    @Test fun noSlotsReturnsNull() {
        assertNull(nextLessonStart(LocalDateTime.of(2026, 8, 3, 7, 0), emptyList(), refMonday))
    }

    @Test fun currentSlotFoundDuringLesson() {
        val slots = listOf(slot(1, "08:00")) // Mon 08:00-10:00
        val now = LocalDateTime.of(2026, 8, 3, 8, 30)
        assertEquals(slots[0], currentSlot(now, slots, refMonday))
    }

    @Test fun currentSlotNullOutsideLesson() {
        val slots = listOf(slot(1, "08:00"))
        assertNull(currentSlot(LocalDateTime.of(2026, 8, 3, 10, 0), slots, refMonday)) // end exclusive
        assertNull(currentSlot(LocalDateTime.of(2026, 8, 3, 7, 59), slots, refMonday))
        assertNull(currentSlot(LocalDateTime.of(2026, 8, 4, 8, 30), slots, refMonday)) // wrong day
    }

    @Test fun currentSlotHonorsParity() {
        val slots = listOf(slot(1, "08:00", WeekParityTag.B))
        // 2026-08-03 is week A -> B-only slot not current
        assertNull(currentSlot(LocalDateTime.of(2026, 8, 3, 8, 30), slots, refMonday))
        // next Monday is week B -> current
        assertEquals(slots[0], currentSlot(LocalDateTime.of(2026, 8, 10, 8, 30), slots, refMonday))
    }

    @Test fun nextSlotWithTimeReturnsSlotAndStart() {
        val s = slot(1, "08:00")
        val r = nextSlotWithTime(LocalDateTime.of(2026, 8, 3, 9, 0), listOf(s), refMonday)
        assertEquals(s, r!!.first)
        assertEquals(LocalDateTime.of(2026, 8, 10, 8, 0), r.second)
    }
}
