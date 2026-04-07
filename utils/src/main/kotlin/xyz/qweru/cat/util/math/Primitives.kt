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

import java.nio.ByteOrder
import kotlin.Boolean
import kotlin.Char
import kotlin.Int

internal object Primitives {
    val NATIVE_LITTLE_ENDIAN: Boolean = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN

    fun unsignedInt(i: Int): Long {
        return i.toLong() and 0xFFFFFFFFL
    }

    fun unsignedShort(s: Int): Int {
        return s and 0xFFFF
    }

    fun unsignedByte(b: Int): Int {
        return b and 0xFF
    }

    private val H2LE = if (NATIVE_LITTLE_ENDIAN) ByteOrderHelper() else ByteOrderHelperReverse()
    private val H2BE = if (NATIVE_LITTLE_ENDIAN) ByteOrderHelperReverse() else ByteOrderHelper()

    fun nativeToLittleEndian(v: Long): Long {
        return H2LE.adjustByteOrder(v)
    }

    fun nativeToLittleEndian(v: Int): Int {
        return H2LE.adjustByteOrder(v)
    }

    fun nativeToLittleEndian(v: Short): Short {
        return H2LE.adjustByteOrder(v)
    }

    fun nativeToLittleEndian(v: Char): Char {
        return H2LE.adjustByteOrder(v)
    }

    fun nativeToBigEndian(v: Long): Long {
        return H2BE.adjustByteOrder(v)
    }

    fun nativeToBigEndian(v: Int): Int {
        return H2BE.adjustByteOrder(v)
    }

    fun nativeToBigEndian(v: Short): Short {
        return H2BE.adjustByteOrder(v)
    }

    fun nativeToBigEndian(v: Char): Char {
        return H2BE.adjustByteOrder(v)
    }

    private open class ByteOrderHelper {
        open fun adjustByteOrder(v: Long): Long {
            return v
        }

        open fun adjustByteOrder(v: Int): Int {
            return v
        }

        open fun adjustByteOrder(v: Short): Short {
            return v
        }

        open fun adjustByteOrder(v: Char): Char {
            return v
        }
    }

    private class ByteOrderHelperReverse : ByteOrderHelper() {
        override fun adjustByteOrder(v: Long): Long {
            return java.lang.Long.reverseBytes(v)
        }

        override fun adjustByteOrder(v: Int): Int {
            return Integer.reverseBytes(v)
        }

        override fun adjustByteOrder(v: Short): Short {
            return java.lang.Short.reverseBytes(v)
        }

        override fun adjustByteOrder(v: Char): Char {
            return Character.reverseBytes(v)
        }
    }
}