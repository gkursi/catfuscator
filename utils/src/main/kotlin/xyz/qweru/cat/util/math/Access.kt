/*
 * Copyright 2014 Higher Frequency Trading http://www.higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.qweru.cat.util.math

import sun.misc.Unsafe
import xyz.qweru.cat.util.math.Primitives.NATIVE_LITTLE_ENDIAN
import xyz.qweru.cat.util.math.Primitives.unsignedByte
import xyz.qweru.cat.util.math.Primitives.unsignedInt
import xyz.qweru.cat.util.math.Primitives.unsignedShort
import java.nio.ByteOrder

/**
 * Strategy of reading bytes, defines the abstraction of `T` class instances as ordered byte
 * sequence. All `getXXX(input, offset)` should be consistent to each other in terms of
 * *ordered byte sequence* each `T` instance represents. For example, if some `Access` implementation returns [ByteOrder.LITTLE_ENDIAN] on [ byteOrder(input)][.byteOrder] call, the following expressions should always have the same value:
 *
 *  * `getLong(input, 0)`
 *  * `getUnsignedInt(input, 0) | (getUnsignedInt(input, 4) << 32)`
 *  * <pre>`getUnsignedInt(input, 0) |
 * ((long) getUnsignedShort(input, 4) << 32) |
 * ((long) getUnsignedByte(input, 6) << 48) |
 * ((long) getUnsignedByte(input, 7) << 56)`</pre>
 *  * And so on
 *
 *
 *
 * `getXXX(input, offset)` methods could throw unchecked exceptions when requested bytes
 * range is outside of the bounds of the byte sequence, represented by the given `input`.
 * However, they could omit checks for better performance.
 *
 *
 * Only [.getByte] and [.byteOrder] methods are abstract in
 * this class, so implementing them is sufficient for valid `Access` instance, but for
 * efficiency your should override methods used by target [LongHashFunction] implementation.
 *
 *
 * `Access` API is designed for inputs, that actually represent byte sequences that lay
 * continuously in memory. Theoretically `Access` strategy could be implemented for
 * non-continuous byte sequences, or abstractions which aren't actually present in memory as they
 * are accessed, but this should be awkward, and hashing using such `Access` is expected to
 * be slow.
 *
 * @param <T> the type of the object to access
 * @see LongHashFunction.hash
</T> */
abstract class Access<T>
/**
 * Constructor for use in subclasses.
 */
protected constructor() {
    /**
     * Reads `[offset, offset + 7]` bytes of the byte sequence represented by the given
     * `input` as a single `long` value.
     *
     * @param input the object to access
     * @param offset offset to the first byte to read within the byte sequence represented
     * by the given object
     * @return eight bytes as a `long` value, in [the expected][.byteOrder]
     */
    open fun getLong(input: T?, offset: Long): Long {
        if (byteOrder(input) == ByteOrder.LITTLE_ENDIAN) {
            return getUnsignedInt(input, offset) or (getUnsignedInt(input, offset + 4L) shl 32)
        } else {
            return getUnsignedInt(input, offset + 4L) or (getUnsignedInt(input, offset) shl 32)
        }
    }

    /**
     * Shortcut for `getInt(input, offset) & 0xFFFFFFFFL`. Could be implemented more
     * efficiently.
     *
     * @param input the object to access
     * @param offset offset to the first byte to read within the byte sequence represented
     * by the given object
     * @return four bytes as an unsigned int value, in [the expected][.byteOrder]
     */
    open fun getUnsignedInt(input: T?, offset: Long): Long {
        return (getInt(input, offset).toLong()) and 0xFFFFFFFFL
    }

    /**
     * Reads `[offset, offset + 3]` bytes of the byte sequence represented by the given
     * `input` as a single `int` value.
     *
     * @param input the object to access
     * @param offset offset to the first byte to read within the byte sequence represented
     * by the given object
     * @return four bytes as an `int` value, in [the expected][.byteOrder]
     */
    open fun getInt(input: T?, offset: Long): Int {
        if (byteOrder(input) == ByteOrder.LITTLE_ENDIAN) {
            return getUnsignedShort(input, offset) or (getUnsignedShort(input, offset + 2L) shl 16)
        } else {
            return getUnsignedShort(input, offset + 2L) or (getUnsignedShort(input, offset) shl 16)
        }
    }

    /**
     * Shortcut for `getShort(input, offset) & 0xFFFF`. Could be implemented more
     * efficiently.
     *
     * @param input the object to access
     * @param offset offset to the first byte to read within the byte sequence represented
     * by the given object
     * @return two bytes as an unsigned short value, in [the expected][.byteOrder]
     */
    open fun getUnsignedShort(input: T?, offset: Long): Int {
        if (byteOrder(input) == ByteOrder.LITTLE_ENDIAN) {
            return getUnsignedByte(input, offset) or (getUnsignedByte(input, offset + 1L) shl 8)
        } else {
            return getUnsignedByte(input, offset + 1L) or (getUnsignedByte(input, offset) shl 8)
        }
    }

    /**
     * Reads `[offset, offset + 1]` bytes of the byte sequence represented by the given
     * `input` as a single `short` value, returned widened to `int`.
     *
     * @param input the object to access
     * @param offset offset to the first byte to read within the byte sequence represented
     * by the given object
     * @return two bytes as a `short` value, in [the expected][.byteOrder], widened to `int`
     */
    open fun getShort(input: T?, offset: Long): Int {
        return getUnsignedShort(input, offset).toShort().toInt()
    }

    /**
     * Shortcut for `getByte(input, offset) & 0xFF`. Could be implemented more efficiently.
     *
     * @param input the object to access
     * @param offset offset to the byte to read within the byte sequence represented
     * by the given object
     * @return a byte by the given `offset`, interpreted as unsigned
     */
    open fun getUnsignedByte(input: T?, offset: Long): Int {
        return getByte(input, offset) and 0xFF
    }

    /**
     * Reads a single byte at the given `offset` in the byte sequence represented by the given
     * `input`, returned widened to `int`.
     *
     * @param input the object to access
     * @param offset offset to the byte to read within the byte sequence represented
     * by the given object
     * @return a byte by the given `offset`, widened to `int`
     */
    abstract fun getByte(input: T?, offset: Long): Int

    // short names
    open fun i64(input: T?, offset: Long): Long {
        return getLong(input, offset)
    }

    open fun u32(input: T?, offset: Long): Long {
        return getUnsignedInt(input, offset)
    }

    open fun i32(input: T?, offset: Long): Int {
        return getInt(input, offset)
    }

    open fun u16(input: T?, offset: Long): Int {
        return getUnsignedShort(input, offset)
    }

    open fun i16(input: T?, offset: Long): Int {
        return getShort(input, offset)
    }

    open fun u8(input: T?, offset: Long): Int {
        return getUnsignedByte(input, offset)
    }

    open fun i8(input: T?, offset: Long): Int {
        return getByte(input, offset)
    }

    /**
     * The byte order in which all multi-byte `getXXX()` reads from the given `input`
     * are performed.
     *
     * @param input the accessed object
     * @return the byte order of all multi-byte reads from the given `input`
     */
    abstract fun byteOrder(input: T?): ByteOrder?

    /**
     * Get `this` or the reversed access object for reading the input as fixed
     * byte order of `byteOrder`.
     *
     * @param input the accessed object
     * @param byteOrder the byte order to be used for reading the `input`
     * @return a `Access` object which will read the `input` with the
     * byte order of `byteOrder`.
     */
    open fun byteOrder(input: T?, byteOrder: ByteOrder?): Access<T> {
        return if (byteOrder(input) == byteOrder) this else reverseAccess()
    }

    /**
     * Get the `Access` object with a different byte order. This method should
     * always return a fixed reference.
     */
    protected abstract fun reverseAccess(): Access<T>

    /**
     * The default reverse byte order delegating `Access` class.
     */
    private class ReverseAccess<T>(val access: Access<T?>) : Access<T?>() {
        override fun getLong(input: T?, offset: Long): Long {
            return java.lang.Long.reverseBytes(access.getLong(input, offset))
        }

        override fun getUnsignedInt(input: T?, offset: Long): Long {
            return java.lang.Long.reverseBytes(access.getUnsignedInt(input, offset)) ushr 32
        }

        override fun getInt(input: T?, offset: Long): Int {
            return Integer.reverseBytes(access.getInt(input, offset))
        }

        override fun getUnsignedShort(input: T?, offset: Long): Int {
            return Integer.reverseBytes(access.getUnsignedShort(input, offset)) ushr 16
        }

        override fun getShort(input: T?, offset: Long): Int {
            return Integer.reverseBytes(access.getShort(input, offset)) shr 16
        }

        override fun getUnsignedByte(input: T?, offset: Long): Int {
            return access.getUnsignedByte(input, offset)
        }

        override fun getByte(input: T?, offset: Long): Int {
            return access.getByte(input, offset)
        }

        override fun byteOrder(input: T?): ByteOrder? {
            return if (ByteOrder.LITTLE_ENDIAN == access.byteOrder(input)) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        }

        override fun reverseAccess(): Access<T?> {
            return access
        }
    }

    companion object {
        /**
         * Get or create the reverse byte order `Access` object for `access`.
         */
        fun <T> newDefaultReverseAccess(access: Access<T?>): Access<T?> {
            return if (access is ReverseAccess<*>)
                access.reverseAccess()
            else
                ReverseAccess(access)
        }
    }
}

open class UnsafeAccess private constructor() : Access<Any?>() {
    override fun getLong(input: Any?, offset: Long): Long {
        return UNSAFE.getLong(input, offset)
    }

    override fun getUnsignedInt(input: Any?, offset: Long): Long {
        return unsignedInt(getInt(input, offset))
    }

    override fun getInt(input: Any?, offset: Long): Int {
        return UNSAFE.getInt(input, offset)
    }

    override fun getUnsignedShort(input: Any?, offset: Long): Int {
        return unsignedShort(getShort(input, offset))
    }

    override fun getShort(input: Any?, offset: Long): Int {
        return UNSAFE.getShort(input, offset).toInt()
    }

    override fun getUnsignedByte(input: Any?, offset: Long): Int {
        return unsignedByte(getByte(input, offset))
    }

    override fun getByte(input: Any?, offset: Long): Int {
        return UNSAFE.getByte(input, offset).toInt()
    }

    override fun byteOrder(input: Any?): ByteOrder? {
        return ByteOrder.nativeOrder()
    }

    override fun reverseAccess(): Access<Any?> {
        return INSTANCE_NON_NATIVE
    }

    private class OldUnsafeAccessLittleEndian : UnsafeAccess() {
        override fun getShort(input: Any?, offset: Long): Int {
            return UNSAFE.getInt(input, offset - 2) shr 16
        }

        override fun getByte(input: Any?, offset: Long): Int {
            return UNSAFE.getInt(input, offset - 3) shr 24
        }
    }

    private class OldUnsafeAccessBigEndian : UnsafeAccess() {
        override fun getShort(input: Any?, offset: Long): Int {
            return UNSAFE.getInt(input, offset - 2).toShort().toInt()
        }

        override fun getByte(input: Any?, offset: Long): Int {
            return UNSAFE.getInt(input, offset - 3).toByte().toInt()
        }
    }

    companion object {
        val INSTANCE: UnsafeAccess
        private val INSTANCE_NON_NATIVE: Access<Any?>

        // for test only
        val OLD_INSTANCE: UnsafeAccess = if (NATIVE_LITTLE_ENDIAN)
            OldUnsafeAccessLittleEndian()
        else
            OldUnsafeAccessBigEndian()

        val UNSAFE: Unsafe

        val BOOLEAN_BASE: Long
        val BYTE_BASE: Long
        val CHAR_BASE: Long
        val SHORT_BASE: Long
        val INT_BASE: Long
        val LONG_BASE: Long

        val TRUE_BYTE_VALUE: Byte
        val FALSE_BYTE_VALUE: Byte

        init {
            try {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.setAccessible(true)
                UNSAFE = theUnsafe.get(null) as Unsafe

                BOOLEAN_BASE = UNSAFE.arrayBaseOffset(BooleanArray::class.java).toLong()
                BYTE_BASE = UNSAFE.arrayBaseOffset(ByteArray::class.java).toLong()
                CHAR_BASE = UNSAFE.arrayBaseOffset(CharArray::class.java).toLong()
                SHORT_BASE = UNSAFE.arrayBaseOffset(ShortArray::class.java).toLong()
                INT_BASE = UNSAFE.arrayBaseOffset(IntArray::class.java).toLong()
                LONG_BASE = UNSAFE.arrayBaseOffset(LongArray::class.java).toLong()

                TRUE_BYTE_VALUE = UNSAFE.getInt(
                    booleanArrayOf(true, true, true, true),
                    BOOLEAN_BASE
                ).toByte()
                FALSE_BYTE_VALUE = UNSAFE.getInt(
                    booleanArrayOf(false, false, false, false),
                    BOOLEAN_BASE
                ).toByte()
            } catch (e: Exception) {
                throw AssertionError(e)
            }

            var hasGetByte = true
            try {
                UNSAFE.getByte(ByteArray(1), BYTE_BASE)
            } catch (ignore: Throwable) {
                // Unsafe in pre-Nougat Android does not have getByte(), fall back to workround
                hasGetByte = false
            }

            INSTANCE = if (hasGetByte) UnsafeAccess() else OLD_INSTANCE
            INSTANCE_NON_NATIVE = newDefaultReverseAccess(INSTANCE)
        }
    }
}