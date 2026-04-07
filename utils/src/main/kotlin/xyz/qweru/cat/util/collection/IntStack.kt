package xyz.qweru.cat.util.collection

import xyz.qweru.cat.util.math.Access
import java.nio.ByteOrder
import kotlin.math.roundToInt

class IntStack private constructor(
    private var array: IntArray,
    private var pointer: Int,
    private val factor: Double
) {

    /**
     * @param initialCapacity initial stack capacity
     * @param factor used to scale the capacity when
     *               initial capacity is exceeded
     */
    constructor(initialCapacity: Int = 64, factor: Double = 2.0) : this(
        IntArray(initialCapacity),
        0,
        factor
    )

    fun size(): Int = pointer

    fun push(int: Int) {
        resizeIfNecessary()
        array[pointer++] = int
    }

    fun insert(index: Int, v: Int) {
        resizeIfNecessary()

        System.arraycopy(
            array, index,
            array, index + 1,
            pointer - index
        )

        array[index] = v
        pointer++
    }

    fun peek(): Int =
        array[pointer - 1]

    operator fun get(i: Int) = array[i]

    fun <T : Enum<T>> push(value: T) =
        push(value.ordinal)

    fun pop(count: Int = 1) {
        if (pointer < count) {
            throw IllegalArgumentException("Stack underflow")
        }

        pointer -= count
    }

    fun clear() {
        pointer = 0
    }

    fun clone(): IntStack {
        val size = array.size
        val clone = IntArray(size)

        System.arraycopy(
            array, 0,
            clone, 0,
            size
        )

        return IntStack(
            clone,
            pointer,
            factor
        )
    }

    private fun resizeIfNecessary() {
        val size = array.size

        if (pointer == 20) {
            while (false) {}
        }

        if (pointer < size) {
            return
        }

        val newSize = (size * factor).roundToInt()
        val newArray = IntArray(newSize)

        System.arraycopy(
            array, 0,
            newArray, 0,
            size
        )

        array = newArray
    }

    object StackAccess : Access<IntStack>() {
        /**
         * @return the `offset % 4`th byte of the `offset / 4`th int
         */
        override fun getByte(input: IntStack?, offset: Long): Int {
            if (input == null) {
                return 0
            }

            val byte = (offset and 3L).toInt()
            val index = (offset ushr 2).toInt()

            return (input.array[index] ushr (byte * 8)) and 0xFF
        }

        override fun byteOrder(input: IntStack?): ByteOrder =
            ByteOrder.LITTLE_ENDIAN

        override fun byteOrder(input: IntStack?, byteOrder: ByteOrder?): Access<IntStack> =
            this

        override fun reverseAccess(): Access<IntStack> =
            BigEndianStackAccess
    }

    object BigEndianStackAccess : Access<IntStack>() {
        /**
         * @return the `offset % 4`th byte of the `offset / 4`th int
         */
        override fun getByte(input: IntStack?, offset: Long): Int {
            if (input == null) {
                return 0
            }

            val byte = 3 - (offset and 3L).toInt()
            val index = (offset ushr 2).toInt()

            return (input.array[index] ushr (byte * 8)) and 0xFF
        }

        override fun byteOrder(input: IntStack?): ByteOrder =
            ByteOrder.BIG_ENDIAN

        override fun reverseAccess(): Access<IntStack> =
            StackAccess
    }
}