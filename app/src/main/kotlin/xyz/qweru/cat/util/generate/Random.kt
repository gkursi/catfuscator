package xyz.qweru.cat.util.generate

import kotlin.random.Random

/**
 * random ints from 0 to `until`
 */
fun sortedRandomInts(until: Int, size: Int): IntArray {
    val set = HashSet<Int>(size)
    // guarantees a unique int
    (0..<size).forEach { _ -> while (!set.add(Random.nextInt(until))) {} }
    return set
        .sortedWith(Comparator.comparingInt { it.toInt() })
        .toIntArray()
}

fun pickRandom(vararg blocks: () -> Unit) {
    blocks.random()()
}

fun Random.nextNonZeroInt() =
    if (nextBoolean()) nextInt(Int.MIN_VALUE, 0)
    else nextInt(1, Int.MAX_VALUE)

fun Random.nextNonZeroLong() =
    if (nextBoolean()) nextLong(Long.MIN_VALUE, 0)
    else nextLong(1, Long.MAX_VALUE)