package xyz.qweru.cat.profile

import xyz.qweru.cat.math.round

data class Timer(private val startNs: Long = System.nanoTime()) {
    fun time(): Double {
        return round((System.nanoTime() - startNs) / 1_000_000.0)
    }
}