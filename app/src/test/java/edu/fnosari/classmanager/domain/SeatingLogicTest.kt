package edu.fnosari.classmanager.domain

import edu.fnosari.classmanager.data.Desk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatingLogicTest {
    private fun desk(id: Long, x: Float, y: Float) = Desk(id = id, roomId = 1, x = x, y = y)

    @Test fun adjacentDesksDetected() {
        val desks = listOf(desk(1, 0.10f, 0.10f), desk(2, 0.20f, 0.10f), desk(3, 0.80f, 0.80f))
        val adj = adjacentDeskPairs(desks, threshold = 0.15f)
        assertEquals(setOf(1L to 2L), adj)
    }

    @Test fun violationsOnlyForSeparatedStudentsOnAdjacentDesks() {
        val desks = listOf(desk(1, 0.10f, 0.10f), desk(2, 0.20f, 0.10f), desk(3, 0.80f, 0.80f))
        // students: 100 on desk1, 200 on desk2 (adjacent), 300 far away
        val seats = mapOf(1L to 100L, 2L to 200L, 3L to 300L)
        val separations = setOf(100L to 200L, 100L to 300L)
        val v = violatingDesks(desks, seats, separations, threshold = 0.15f)
        assertEquals(setOf(1L, 2L), v)
    }

    @Test fun reversedSeparationAlsoCounts() {
        val desks = listOf(desk(1, 0.10f, 0.10f), desk(2, 0.20f, 0.10f))
        val seats = mapOf(1L to 100L, 2L to 200L)
        val v = violatingDesks(desks, seats, setOf(200L to 100L), threshold = 0.15f)
        assertEquals(setOf(1L, 2L), v)
    }

    @Test fun emptySeatsNoViolations() {
        val desks = listOf(desk(1, 0.10f, 0.10f), desk(2, 0.20f, 0.10f))
        assertTrue(violatingDesks(desks, emptyMap(), setOf(100L to 200L), 0.15f).isEmpty())
    }
}
