package edu.fnosari.classmanager.domain

import edu.fnosari.classmanager.data.Desk
import kotlin.math.sqrt

/** Desk id pairs (smaller id first) whose centers are closer than [threshold] in normalized space. */
fun adjacentDeskPairs(desks: List<Desk>, threshold: Float): Set<Pair<Long, Long>> {
    val result = mutableSetOf<Pair<Long, Long>>()
    for (i in desks.indices) for (j in i + 1 until desks.size) {
        val a = desks[i]
        val b = desks[j]
        val dx = a.x - b.x
        val dy = a.y - b.y
        if (sqrt(dx * dx + dy * dy) < threshold) {
            result.add(if (a.id < b.id) a.id to b.id else b.id to a.id)
        }
    }
    return result
}

/** Desk ids whose seated students violate a separation constraint with an adjacent desk. */
fun violatingDesks(
    desks: List<Desk>,
    seats: Map<Long, Long>, // deskId -> studentId
    separations: Set<Pair<Long, Long>>,
    threshold: Float,
): Set<Long> {
    val forbidden = HashSet<Pair<Long, Long>>().apply {
        for ((a, b) in separations) { add(a to b); add(b to a) }
    }
    val result = mutableSetOf<Long>()
    for ((d1, d2) in adjacentDeskPairs(desks, threshold)) {
        val s1 = seats[d1] ?: continue
        val s2 = seats[d2] ?: continue
        if ((s1 to s2) in forbidden) {
            result.add(d1)
            result.add(d2)
        }
    }
    return result
}
