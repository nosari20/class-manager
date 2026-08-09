package edu.fnosari.classmanager.domain

import kotlin.random.Random

enum class SplitMode { GROUPS_OF_N, N_GROUPS }

sealed class GroupResult {
    data class Success(val groups: List<List<Long>>) : GroupResult()
    data class Infeasible(val clashingPairs: List<Pair<Long, Long>>) : GroupResult()
}

fun computeSizes(total: Int, mode: SplitMode, n: Int): List<Int> {
    require(n > 0)
    if (total == 0) return emptyList()
    val groupCount = when (mode) {
        SplitMode.N_GROUPS -> minOf(n, total)
        SplitMode.GROUPS_OF_N -> (total + n - 1) / n
    }
    val base = total / groupCount
    val extra = total % groupCount
    return List(groupCount) { i -> base + if (i < extra) 1 else 0 }
}

fun generateGroups(
    studentIds: List<Long>,
    separations: Set<Pair<Long, Long>>,
    sizes: List<Int>,
    random: Random = Random.Default,
    maxAttempts: Int = 100,
): GroupResult {
    require(sizes.sum() == studentIds.size) { "sizes must sum to student count" }
    val forbidden = HashSet<Pair<Long, Long>>().apply {
        for ((a, b) in separations) { add(a to b); add(b to a) }
    }
    fun conflicts(id: Long, group: List<Long>) = group.any { (id to it) in forbidden }

    repeat(maxAttempts) {
        val order = studentIds.shuffled(random)
        val groups = sizes.map { mutableListOf<Long>() }
        if (assign(order, 0, groups, sizes, ::conflicts)) {
            return GroupResult.Success(groups.map { it.toList() })
        }
    }
    // Infeasible (or extremely unlucky): report constraints among the most-constrained students
    val degree = studentIds.associateWith { id -> separations.count { it.first == id || it.second == id } }
    val hot = studentIds.sortedByDescending { degree[it] ?: 0 }.take(5).toSet()
    val clashing = separations.filter { it.first in hot || it.second in hot }
    return GroupResult.Infeasible(clashing.ifEmpty { separations.toList() })
}

private fun assign(
    order: List<Long>,
    index: Int,
    groups: List<MutableList<Long>>,
    sizes: List<Int>,
    conflicts: (Long, List<Long>) -> Boolean,
): Boolean {
    if (index == order.size) return true
    val id = order[index]
    // smallest-fill-first keeps sizes balanced while backtracking
    val candidates = groups.indices
        .filter { groups[it].size < sizes[it] && !conflicts(id, groups[it]) }
        .sortedBy { groups[it].size }
    for (g in candidates) {
        groups[g].add(id)
        if (assign(order, index + 1, groups, sizes, conflicts)) return true
        groups[g].removeAt(groups[g].size - 1)
    }
    return false
}
