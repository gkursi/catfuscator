package xyz.qweru.cat.util.math

import java.nio.ByteBuffer

fun ByteBuffer.clone(): ByteBuffer {
    val clone = ByteBuffer.allocate(this.capacity())

    this.rewind()
    clone.put(this)
    this.rewind()
    clone.flip()

    return clone
}