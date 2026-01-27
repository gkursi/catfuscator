package xyz.qweru.cat.util.crypto

import java.security.MessageDigest

private val hashFun
    get() = MessageDigest.getInstance("SHA-256")

fun hash(string: String): String =
    hashFun.digest(string.toByteArray()).toString(Charsets.UTF_8)