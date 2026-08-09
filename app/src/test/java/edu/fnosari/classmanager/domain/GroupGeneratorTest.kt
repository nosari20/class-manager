package edu.fnosari.classmanager.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupGeneratorTest {
    @Test fun sizesGroupsOfN() {
        assertEquals(listOf(4, 4, 4), computeSizes(12, SplitMode.GROUPS_OF_N, 4))
        // remainder spread: 14 in groups of 4 -> 4,4,3,3
        assertEquals(listOf(4, 4, 3, 3), computeSizes(14, SplitMode.GROUPS_OF_N, 4))
        assertEquals(listOf(3, 3, 3, 2), computeSizes(11, SplitMode.GROUPS_OF_N, 3))
    }

    @Test fun sizesNGroups() {
        assertEquals(listOf(4, 4, 4), computeSizes(12, SplitMode.N_GROUPS, 3))
        assertEquals(listOf(5, 5, 4), computeSizes(14, SplitMode.N_GROUPS, 3))
    }

    @Test fun allStudentsPlacedExactlyOnce() {
        val ids = (1L..14L).toList()
        val r = generateGroups(ids, emptySet(), computeSizes(14, SplitMode.GROUPS_OF_N, 4),
            Random(42)) as GroupResult.Success
        assertEquals(ids.sorted(), r.groups.flatten().sorted())
        assertEquals(listOf(4, 4, 3, 3), r.groups.map { it.size })
    }

    @Test fun separationRespected() {
        val ids = (1L..8L).toList()
        val seps = setOf(1L to 2L, 3L to 4L)
        repeat(20) { seed ->
            val r = generateGroups(ids, seps, computeSizes(8, SplitMode.N_GROUPS, 2),
                Random(seed.toLong())) as GroupResult.Success
            for (g in r.groups) {
                assertFalse(g.contains(1L) && g.contains(2L))
                assertFalse(g.contains(3L) && g.contains(4L))
            }
        }
    }

    @Test fun reverseOrderPairAlsoRespected() {
        val ids = (1L..4L).toList()
        val r = generateGroups(ids, setOf(2L to 1L), listOf(2, 2), Random(1)) as GroupResult.Success
        for (g in r.groups) assertFalse(g.contains(1L) && g.contains(2L))
    }

    @Test fun infeasibleDetected() {
        // 3 mutually separated students, only 2 groups -> impossible
        val ids = listOf(1L, 2L, 3L, 4L)
        val seps = setOf(1L to 2L, 1L to 3L, 2L to 3L)
        val r = generateGroups(ids, seps, listOf(2, 2), Random(1))
        assertTrue(r is GroupResult.Infeasible)
        assertTrue((r as GroupResult.Infeasible).clashingPairs.isNotEmpty())
    }

    @Test fun tightButFeasibleSolved() {
        // chain of separations, 2 groups of 2: 1-2, 2-3, 3-4 -> {1,3},{2,4} etc.
        val ids = listOf(1L, 2L, 3L, 4L)
        val seps = setOf(1L to 2L, 2L to 3L, 3L to 4L)
        val r = generateGroups(ids, seps, listOf(2, 2), Random(7))
        assertTrue(r is GroupResult.Success)
    }
}
