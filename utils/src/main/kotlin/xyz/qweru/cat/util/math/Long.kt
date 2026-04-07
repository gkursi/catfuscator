package xyz.qweru.cat.util.math

fun unsignedLongMulXorFold(lhs: Long, rhs: Long): Long {
    // The Grade School method of multiplication is a hair faster in Java, primarily used here
    // because the implementation is simpler.
    val lhs_l = lhs and 0xFFFFFFFFL
    val lhs_h = lhs ushr 32
    val rhs_l = rhs and 0xFFFFFFFFL
    val rhs_h = rhs ushr 32
    val lo_lo = lhs_l * rhs_l
    val hi_lo = lhs_h * rhs_l
    val lo_hi = lhs_l * rhs_h
    val hi_hi = lhs_h * rhs_h

    // Add the products together. This will never overflow.
    val cross = (lo_lo ushr 32) + (hi_lo and 0xFFFFFFFFL) + lo_hi
    val upper = (hi_lo ushr 32) + (cross ushr 32) + hi_hi
    val lower = (cross shl 32) or (lo_lo and 0xFFFFFFFFL)
    return lower xor upper
}

fun unsignedLongMulHigh(lhs: Long, rhs: Long): Long {
    // The Grade School method of multiplication is a hair faster in Java, primarily used here
    // because the implementation is simpler.
    val lhs_l = lhs and 0xFFFFFFFFL
    val lhs_h = lhs ushr 32
    val rhs_l = rhs and 0xFFFFFFFFL
    val rhs_h = rhs ushr 32
    val lo_lo = lhs_l * rhs_l
    val hi_lo = lhs_h * rhs_l
    val lo_hi = lhs_l * rhs_h
    val hi_hi = lhs_h * rhs_h

    // Add the products together. This will never overflow.
    val cross = (lo_lo ushr 32) + (hi_lo and 0xFFFFFFFFL) + lo_hi
    val upper = (hi_lo ushr 32) + (cross ushr 32) + hi_hi
    return upper
}