package xyz.qweru.cat.util.generate

import java.util.ArrayDeque

/**
 * Load-balancing based pool
 */
class MaxLoadPool<T>(val maxLoad: Int = 4, val supplier: (Int) -> T) {
    private val existing = ArrayDeque<T>()
    private var currentLoad = 0

    fun getNext(): T {
        currentLoad++
        if (existing.isEmpty() || currentLoad > maxLoad) {
            currentLoad = 0
            existing.push(supplier(existing.size)!!)
        }
        return existing.peek()
    }

    fun iterator(): Iterator<T> = existing.iterator()
}