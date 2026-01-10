package xyz.qweru.cat.math

import kotlin.math.roundToLong

/**
 * Rounds to 2 digits
 */
fun round(value: Double): Double = (value * 100).roundToLong() / 100.0