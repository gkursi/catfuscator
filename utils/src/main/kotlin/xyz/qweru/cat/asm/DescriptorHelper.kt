package xyz.qweru.cat.asm

fun classFromDescriptor(descriptor: String): String =
    descriptor.substring(1, descriptor.length - 1)