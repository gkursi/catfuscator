package xyz.qweru.cat.util.profile

import xyz.qweru.cat.util.math.round

class Timer() {
    private val startNs: Long = System.nanoTime()

    fun time(): Double {
        return round((System.nanoTime() - startNs) / 1_000_000.0)
    }
}