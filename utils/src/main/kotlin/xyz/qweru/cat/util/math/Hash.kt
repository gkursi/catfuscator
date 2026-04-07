package xyz.qweru.cat.util.math

import xyz.qweru.cat.util.math.UnsafeAccess.Companion.BYTE_BASE
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Adapted version of XXH3 implementation from https://github.com/Cyan4973/xxHash.
 * This implementation provides endian-independant hash values,
         but it's slower on big-endian platforms.
 */
object XXH3 {
    private val unsafeLE: Access<Any?> = UnsafeAccess.INSTANCE.byteOrder(null, ByteOrder.LITTLE_ENDIAN)!! as Access<Any?>

    /*! Pseudorandom secret taken directly from FARSH. */
    private val XXH3_kSecret = byteArrayOf(
        0xb8.toByte(), 0xfe.toByte(), 0x6c.toByte(), 0x39.toByte(),
        0x23.toByte(), 0xa4.toByte(), 0x4b.toByte(), 0xbe.toByte(),
        0x7c.toByte(), 0x01.toByte(), 0x81.toByte(), 0x2c.toByte(),
        0xf7.toByte(), 0x21.toByte(), 0xad.toByte(), 0x1c.toByte(),
        0xde.toByte(), 0xd4.toByte(), 0x6d.toByte(), 0xe9.toByte(),
        0x83.toByte(), 0x90.toByte(), 0x97.toByte(), 0xdb.toByte(),
        0x72.toByte(), 0x40.toByte(), 0xa4.toByte(), 0xa4.toByte(),
        0xb7.toByte(), 0xb3.toByte(), 0x67.toByte(), 0x1f.toByte(),
        0xcb.toByte(), 0x79.toByte(), 0xe6.toByte(), 0x4e.toByte(),
        0xcc.toByte(), 0xc0.toByte(), 0xe5.toByte(), 0x78.toByte(),
        0x82.toByte(), 0x5a.toByte(), 0xd0.toByte(), 0x7d.toByte(),
        0xcc.toByte(), 0xff.toByte(), 0x72.toByte(), 0x21.toByte(),
        0xb8.toByte(), 0x08.toByte(), 0x46.toByte(), 0x74.toByte(),
        0xf7.toByte(), 0x43.toByte(), 0x24.toByte(), 0x8e.toByte(),
        0xe0.toByte(), 0x35.toByte(), 0x90.toByte(), 0xe6.toByte(),
        0x81.toByte(), 0x3a.toByte(), 0x26.toByte(), 0x4c.toByte(),
        0x3c.toByte(), 0x28.toByte(), 0x52.toByte(), 0xbb.toByte(),
        0x91.toByte(), 0xc3.toByte(), 0x00.toByte(), 0xcb.toByte(),
        0x88.toByte(), 0xd0.toByte(), 0x65.toByte(), 0x8b.toByte(),
        0x1b.toByte(), 0x53.toByte(), 0x2e.toByte(), 0xa3.toByte(),
        0x71.toByte(), 0x64.toByte(), 0x48.toByte(), 0x97.toByte(),
        0xa2.toByte(), 0x0d.toByte(), 0xf9.toByte(), 0x4e.toByte(),
        0x38.toByte(), 0x19.toByte(), 0xef.toByte(), 0x46.toByte(),
        0xa9.toByte(), 0xde.toByte(), 0xac.toByte(), 0xd8.toByte(),
        0xa8.toByte(), 0xfa.toByte(), 0x76.toByte(), 0x3f.toByte(),
        0xe3.toByte(), 0x9c.toByte(), 0x34.toByte(), 0x3f.toByte(),
        0xf9.toByte(), 0xdc.toByte(), 0xbb.toByte(), 0xc7.toByte(),
        0xc7.toByte(), 0x0b.toByte(), 0x4f.toByte(), 0x1d.toByte(),
        0x8a.toByte(), 0x51.toByte(), 0xe0.toByte(), 0x4b.toByte(),
        0xcd.toByte(), 0xb4.toByte(), 0x59.toByte(), 0x31.toByte(),
        0xc8.toByte(), 0x9f.toByte(), 0x7e.toByte(), 0xc9.toByte(),
        0xd9.toByte(), 0x78.toByte(), 0x73.toByte(), 0x64.toByte(),
        0xea.toByte(), 0xc5.toByte(), 0xac.toByte(), 0x83.toByte(),
        0x34.toByte(), 0xd3.toByte(), 0xeb.toByte(), 0xc3.toByte(),
        0xc5.toByte(), 0x81.toByte(), 0xa0.toByte(), 0xff.toByte(),
        0xfa.toByte(), 0x13.toByte(), 0x63.toByte(), 0xeb.toByte(),
        0x17.toByte(), 0x0d.toByte(), 0xdd.toByte(), 0x51.toByte(),
        0xb7.toByte(), 0xf0.toByte(), 0xda.toByte(), 0x49.toByte(),
        0xd3.toByte(), 0x16.toByte(), 0x55.toByte(), 0x26.toByte(),
        0x29.toByte(), 0xd4.toByte(), 0x68.toByte(), 0x9e.toByte(),
        0x2b.toByte(), 0x16.toByte(), 0xbe.toByte(), 0x58.toByte(),
        0x7d.toByte(), 0x47.toByte(), 0xa1.toByte(), 0xfc.toByte(),
        0x8f.toByte(), 0xf8.toByte(), 0xb8.toByte(), 0xd1.toByte(),
        0x7a.toByte(), 0xd0.toByte(), 0x31.toByte(), 0xce.toByte(),
        0x45.toByte(), 0xcb.toByte(), 0x3a.toByte(), 0x8f.toByte(),
        0x95.toByte(), 0x16.toByte(), 0x04.toByte(), 0x28.toByte(),
        0xaf.toByte(), 0xd7.toByte(), 0xfb.toByte(), 0xca.toByte(),
        0xbb.toByte(), 0x4b.toByte(), 0x40.toByte(), 0x7e.toByte()
    )

    // Primes
    private const val XXH_PRIME32_1 = 0x9E3779B1L /*!< 0b10011110001101110111100110110001 */
    private const val XXH_PRIME32_2 = 0x85EBCA77L /*!< 0b10000101111010111100101001110111 */
    private const val XXH_PRIME32_3 = 0xC2B2AE3DL /*!< 0b11000010101100101010111000111101 */

    private const val XXH_PRIME64_1 =
        -0x61c8864e7a143579L /*!< 0b1001111000110111011110011011000110000101111010111100101010000111 */
    private const val XXH_PRIME64_2 =
        -0x3d4d51c2d82b14b1L /*!< 0b1100001010110010101011100011110100100111110101001110101101001111 */
    private const val XXH_PRIME64_3 =
        0x165667B19E3779F9L /*!< 0b0001011001010110011001111011000110011110001101110111100111111001 */
    private const val XXH_PRIME64_4 =
        -0x7a1435883d4d519dL /*!< 0b1000010111101011110010100111011111000010101100101010111001100011 */
    private const val XXH_PRIME64_5 =
        0x27D4EB2F165667C5L /*!< 0b0010011111010100111010110010111100010110010101100110011111000101 */

    // only support fixed size secret
    private val nbStripesPerBlock = ((192 - 64) / 8).toLong()
    private val block_len = 64 * nbStripesPerBlock

    fun <T> hash64(input: T, access: Access<T>, len: Long, seed: Long = 0, off: Long = 0): Long =
        XXH3_64bits_internal(
            seed,
            XXH3_kSecret,
            input,
            access.byteOrder(
                input,
                ByteOrder.LITTLE_ENDIAN
            ),
            off,
            len,
        )

    fun <T> hash128(input: T, access: Access<T>, len: Long, seed: Long = 0, off: Long = 0): LongArray {
        val arr = longArrayOf(0L, 0L)

        XXH3_128bits_internal(
            seed,
            XXH3_kSecret,
            input,
            access.byteOrder(
                input,
                ByteOrder.LITTLE_ENDIAN
            ),
            off,
            len,
            arr
        )

        return arr
    }

    private fun XXH64_avalanche(h64: Long): Long {
        var h64 = h64
        h64 = h64 xor (h64 ushr 33)
        h64 *= XXH_PRIME64_2
        h64 = h64 xor (h64 ushr 29)
        h64 *= XXH_PRIME64_3
        return h64 xor (h64 ushr 32)
    }

    private fun XXH3_avalanche(h64: Long): Long {
        var h64 = h64
        h64 = h64 xor (h64 ushr 37)
        h64 *= 0x165667919E3779F9L
        return h64 xor (h64 ushr 32)
    }

    private fun XXH3_rrmxmx(h64: Long,
         length: Long): Long {
        var h64 = h64
        h64 = h64 xor (h64.rotateLeft(49) xor h64.rotateLeft(24))
        h64 *= -0x604de39ae16720dbL
        h64 = h64 xor (h64 ushr 35) + length
        h64 *= -0x604de39ae16720dbL
        return h64 xor (h64 ushr 28)
    }

    private fun <T> XXH3_mix16B(
        seed: Long,
        input: T?,
        access: Access<T>,
        offIn: Long,
        offSec: Long
    ): Long {
        val input_lo: Long = access.i64(input, offIn)
        val input_hi: Long = access.i64(input, offIn + 8)
        return unsignedLongMulXorFold(
            input_lo xor (unsafeLE.i64(XXH3_kSecret, offSec) + seed),
            input_hi xor (unsafeLE.i64(XXH3_kSecret, offSec + 8) - seed)
        )
    }

    /*
     * A bit slower than XXH3_mix16B, but handles multiply by zero better.
     */
    private fun XXH128_mix32B_once(
        seed: Long,
        offSec: Long,
        acc: Long,
        input0: Long,
        input1: Long,
        input2: Long,
        input3: Long
    ): Long {
        var acc = acc
        acc += unsignedLongMulXorFold(
            input0 xor (unsafeLE.i64(XXH3_kSecret, offSec) + seed),
            input1 xor (unsafeLE.i64(XXH3_kSecret, offSec + 8) - seed)
        )
        return acc xor (input2 + input3)
    }

    private fun XXH3_mix2Accs(
        acc_lh: Long,
        acc_rh: Long,
        secret: ByteArray?,
        offSec: Long
    ): Long {
        return unsignedLongMulXorFold(
            acc_lh xor unsafeLE.i64(secret, offSec),
            acc_rh xor unsafeLE.i64(secret, offSec + 8)
        )
    }

    private fun <T> XXH3_64bits_internal(
        seed: Long,
        secret: ByteArray?,
        input: T?,
        access: Access<T>,
        off: Long,
        length: Long
    ): Long {
        if (length <= 16) {
            // XXH3_len_0to16_64b
            if (length > 8) {
                // XXH3_len_9to16_64b
                val bitflip1: Long =
                    (unsafeLE.i64(XXH3_kSecret, 24 + BYTE_BASE) xor unsafeLE.i64(XXH3_kSecret, 32 + BYTE_BASE)) + seed
                val bitflip2: Long =
                    (unsafeLE.i64(XXH3_kSecret, 40 + BYTE_BASE) xor unsafeLE.i64(XXH3_kSecret, 48 + BYTE_BASE)) - seed
                val input_lo: Long = access.i64(input, off) xor bitflip1
                val input_hi: Long = access.i64(input, off + length - 8) xor bitflip2
                val acc: Long =
                    length + java.lang.Long.reverseBytes(input_lo) + input_hi + unsignedLongMulXorFold(input_lo, input_hi)
                return XXH3_avalanche(acc)
            }
            if (length >= 4) {
                // XXH3_len_4to8_64b
                val s = seed xor java.lang.Long.reverseBytes(seed and 0xFFFFFFFFL)
                val input1 = access.i32(input, off).toLong() // high int will be shifted
                val input2: Long = access.u32(input, off + length - 4)
                val bitflip: Long =
                    (unsafeLE.i64(XXH3_kSecret, 8 + BYTE_BASE) xor unsafeLE.i64(XXH3_kSecret, 16 + BYTE_BASE)) - s
                val keyed = (input2 + (input1 shl 32)) xor bitflip
                return XXH3_rrmxmx(keyed, length)
            }
            if (length != 0L) {
                // XXH3_len_1to3_64b
                val c1: Int = access.u8(input, off + 0)
                val c2: Int = access.i8(input, off + (length shr 1)) // high 3 bytes will be shifted
                val c3: Int = access.u8(input, off + length - 1)
                val combined: Long =
                    Primitives.unsignedInt((c1 shl 16) or (c2 shl 24) or c3 or (length.toInt() shl 8))
                val bitflip: Long = Primitives.unsignedInt(
                    unsafeLE.i32(XXH3_kSecret, BYTE_BASE) xor unsafeLE.i32(
                        XXH3_kSecret, 4 + BYTE_BASE
                    )
                ) + seed
                return XXH64_avalanche(combined xor bitflip)
            }
            return XXH64_avalanche(
                seed xor unsafeLE.i64(XXH3_kSecret, 56 + BYTE_BASE) xor unsafeLE.i64(
                    XXH3_kSecret,
                    64 + BYTE_BASE
                )
            )
        }

        if (length <= 128) {
            // XXH3_len_17to128_64b
            var acc = length * XXH_PRIME64_1

            if (length > 32) {
                if (length > 64) {
                    if (length > 96) {
                        acc += XXH3_mix16B(seed, input, access, off + 48, BYTE_BASE + 96)
                        acc += XXH3_mix16B(seed, input, access, off + length - 64, BYTE_BASE + 112)
                    }
                    acc += XXH3_mix16B(seed, input, access, off + 32, BYTE_BASE + 64)
                    acc += XXH3_mix16B(seed, input, access, off + length - 48, BYTE_BASE + 80)
                }
                acc += XXH3_mix16B(seed, input, access, off + 16, BYTE_BASE + 32)
                acc += XXH3_mix16B(seed, input, access, off + length - 32, BYTE_BASE + 48)
            }
            acc += XXH3_mix16B(seed, input, access, off, BYTE_BASE)
            acc += XXH3_mix16B(seed, input, access, off + length - 16, BYTE_BASE + 16)

            return XXH3_avalanche(acc)
        }

        if (length <= 240) {
            // XXH3_len_129to240_64b
            var acc = length * XXH_PRIME64_1
            val nbRounds = length.toInt() / 16
            var i = 0
            while (i < 8) {
                acc += XXH3_mix16B(seed, input, access, off + 16 * i, BYTE_BASE + 16 * i)
                ++i
            }
            acc = XXH3_avalanche(acc)

            while (i < nbRounds) {
                acc += XXH3_mix16B(seed, input, access, off + 16 * i, BYTE_BASE + 16 * (i - 8) + 3)
                ++i
            }

            /* last bytes */
            acc += XXH3_mix16B(seed, input, access, off + length - 16, BYTE_BASE + 136 - 17)
            return XXH3_avalanche(acc)
        }

        // XXH3_hashLong_64b_internal
        var acc_0 = XXH_PRIME32_3
        var acc_1 = XXH_PRIME64_1
        var acc_2 = XXH_PRIME64_2
        var acc_3 = XXH_PRIME64_3
        var acc_4 = XXH_PRIME64_4
        var acc_5 = XXH_PRIME32_2
        var acc_6 = XXH_PRIME64_5
        var acc_7 = XXH_PRIME32_1

        // XXH3_hashLong_internal_loop
        val nb_blocks = (length - 1) / block_len
        for (n in 0..<nb_blocks) {
            // XXH3_accumulate
            val offBlock = off + n * block_len
            for (s in 0..<nbStripesPerBlock) {
                // XXH3_accumulate_512
                val offStripe = offBlock + s * 64
                val offSec = s * 8

                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 0)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 1)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 0)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 1)
                    /* swap adjacent lanes */
                    acc_0 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_1+= data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }

                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 2)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 3)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 2)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 3)
                    /* swap adjacent lanes */
                    acc_2 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_3 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }

                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 4)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 5)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 4)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 5)
                    /* swap adjacent lanes */
                    acc_4 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_5 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }

                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 6)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 7)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 6)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 7)
                    /* swap adjacent lanes */
                    acc_6 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_7 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }
            }

            // XXH3_scrambleAcc_scalar
            val offSec: Long = BYTE_BASE + 192 - 64
            acc_0 = (acc_0 xor (acc_0 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 0)) * XXH_PRIME32_1
            acc_1 = (acc_1 xor (acc_1 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 1)) * XXH_PRIME32_1
            acc_2 = (acc_2 xor (acc_2 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 2)) * XXH_PRIME32_1
            acc_3 = (acc_3 xor (acc_3 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 3)) * XXH_PRIME32_1
            acc_4 = (acc_4 xor (acc_4 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 4)) * XXH_PRIME32_1
            acc_5 = (acc_5 xor (acc_5 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 5)) * XXH_PRIME32_1
            acc_6 = (acc_6 xor (acc_6 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 6)) * XXH_PRIME32_1
            acc_7 = (acc_7 xor (acc_7 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 7)) * XXH_PRIME32_1
        }

        /* last partial block */
        val nbStripes = ((length - 1) - (block_len * nb_blocks)) / 64
        val offBlock = off + block_len * nb_blocks
        for (s in 0..<nbStripes) {
            // XXH3_accumulate_512
            val offStripe = offBlock + s * 64
            val offSec = s * 8

            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 0)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 1)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 0)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 1)
                /* swap adjacent lanes */
                acc_0 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_1 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }

            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 2)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 3)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 2)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 3)
                /* swap adjacent lanes */
                acc_2 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_3 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }

            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 4)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 5)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 4)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 5)
                /* swap adjacent lanes */
                acc_4 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_5 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }

            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 6)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 7)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 6)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 7)
                /* swap adjacent lanes */
                acc_6 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_7 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }
        }

        /* last stripe */
        // XXH3_accumulate_512
        val offStripe = off + length - 64
        val offSec = (192 - 64 - 7).toLong()

        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 0)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 1)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 0)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 1)
            /* swap adjacent lanes */
            acc_0 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_1 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }

        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 2)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 3)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 2)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 3)
            /* swap adjacent lanes */
            acc_2 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_3 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }

        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 4)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 5)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 4)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 5)
            /* swap adjacent lanes */
            acc_4 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_5 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }

        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 6)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 7)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 6)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 7)
            /* swap adjacent lanes */
            acc_6 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_7 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }

        // XXH3_mergeAccs
        val result64 = (length * XXH_PRIME64_1 + XXH3_mix2Accs(acc_0, acc_1, secret, BYTE_BASE + 11)
                + XXH3_mix2Accs(acc_2, acc_3, secret, BYTE_BASE + 11 + 16)
                + XXH3_mix2Accs(acc_4, acc_5, secret, BYTE_BASE + 11 + 16 * 2)
                + XXH3_mix2Accs(acc_6, acc_7, secret, BYTE_BASE + 11 + 16 * 3))

        return XXH3_avalanche(result64)
    }

    private fun <T> XXH3_128bits_internal(
        seed: Long,
        secret: ByteArray?,
        input: T,
        access: Access<T>,
        off: Long,
        length: Long,
        result: LongArray?
    ): Long {
        if (length <= 16) {
            // XXH3_len_0to16_128b
            if (length > 8) {
                // XXH3_len_9to16_128b
                val bitflipl: Long =
                    (unsafeLE.i64(XXH3_kSecret, 32 + BYTE_BASE) xor unsafeLE.i64(XXH3_kSecret, 40 + BYTE_BASE)) - seed
                val bitfliph: Long =
                    (unsafeLE.i64(XXH3_kSecret, 48 + BYTE_BASE) xor unsafeLE.i64(XXH3_kSecret, 56 + BYTE_BASE)) + seed
                var input_hi: Long = access.i64(input, off + length - 8)
                val input_lo: Long = access.i64(input, off) xor input_hi xor bitflipl
                var m128_lo = input_lo * XXH_PRIME64_1
                var m128_hi: Long = unsignedLongMulHigh(input_lo, XXH_PRIME64_1)
                m128_lo += (length - 1) shl 54
                input_hi = input_hi xor bitfliph
                m128_hi += input_hi + Primitives.unsignedInt(input_hi.toInt()) * (XXH_PRIME32_2 - 1)
                m128_lo = m128_lo xor java.lang.Long.reverseBytes(m128_hi)

                val low = XXH3_avalanche(m128_lo * XXH_PRIME64_2)
                if (null != result) {
                    result[0] = low
                    result[1] =
                        XXH3_avalanche(unsignedLongMulHigh(m128_lo, XXH_PRIME64_2) + m128_hi * XXH_PRIME64_2)
                }
                return low
            }
            if (length >= 4) {
                // XXH3_len_4to8_128b
                val s = seed xor java.lang.Long.reverseBytes(seed and 0xFFFFFFFFL)
                val input_lo: Long = access.u32(input, off)
                val input_hi = access.i32(input, off + length - 4) as Long // high int will be shifted

                val bitflip: Long =
                    (unsafeLE.i64(XXH3_kSecret, 16 + BYTE_BASE) xor unsafeLE.i64(XXH3_kSecret, 24 + BYTE_BASE)) + s
                val keyed = (input_lo + (input_hi shl 32)) xor bitflip
                val pl =
                    XXH_PRIME64_1 + (length shl 2) /* Shift len to the left to ensure it is even, this avoids even multiplies. */
                var m128_lo = keyed * pl
                var m128_hi: Long = unsignedLongMulHigh(keyed, pl)
                m128_hi += (m128_lo shl 1)
                m128_lo = m128_lo xor (m128_hi ushr 3)

                m128_lo = m128_lo xor (m128_lo ushr 35)
                m128_lo *= -0x604de39ae16720dbL
                m128_lo = m128_lo xor (m128_lo ushr 28)

                if (null != result) {
                    result[0] = m128_lo
                    result[1] = XXH3_avalanche(m128_hi)
                }
                return m128_lo
            }
            if (length != 0L) {
                // XXH3_len_1to3_128b
                val c1: Int = access.u8(input, off + 0)
                val c2: Int = access.i8(input, off + (length shr 1)) // high 3 bytes will be shifted
                val c3: Int = access.u8(input, off + length - 1)
                val combinedl = (c1 shl 16) or (c2 shl 24) or c3 or (length.toInt() shl 8)
                val combinedh = Integer.rotateLeft(Integer.reverseBytes(combinedl), 13)
                val bitflipl: Long = Primitives.unsignedInt(
                    unsafeLE.i32(XXH3_kSecret, BYTE_BASE) xor unsafeLE.i32(
                        XXH3_kSecret, BYTE_BASE + 4
                    )
                ) + seed
                val bitfliph: Long = Primitives.unsignedInt(
                    unsafeLE.i32(XXH3_kSecret, BYTE_BASE + 8) xor unsafeLE.i32(
                        XXH3_kSecret, BYTE_BASE + 12
                    )
                ) - seed

                val low = XXH3.XXH64_avalanche(Primitives.unsignedInt(combinedl) xor bitflipl)
                if (null != result) {
                    result[0] = low
                    result[1] = XXH3.XXH64_avalanche(Primitives.unsignedInt(combinedh) xor bitfliph)
                }
                return low
            }
            val low = XXH64_avalanche(
                seed xor unsafeLE.i64(XXH3_kSecret, BYTE_BASE + 64) xor unsafeLE.i64(
                    XXH3_kSecret,
                    BYTE_BASE + 72
                )
            )
            if (null != result) {
                result[0] = low
                result[1] = XXH64_avalanche(
                    seed xor unsafeLE.i64(XXH3_kSecret, BYTE_BASE + 80) xor unsafeLE.i64(
                        XXH3_kSecret, BYTE_BASE + 88
                    )
                )
            }
            return low
        }
        if (length <= 128) {
            // XXH3_len_17to128_128b
            var acc0 = length * XXH_PRIME64_1
            var acc1: Long = 0
            if (length > 32) {
                if (length > 64) {
                    if (length > 96) {
                        val input0: Long = access.i64(input, off + 48)
                        val input1: Long = access.i64(input, off + 48 + 8)
                        val input2: Long = access.i64(input, off + length - 64)
                        val input3: Long = access.i64(input, off + length - 64 + 8)
                        acc0 = XXH128_mix32B_once(seed, BYTE_BASE + 96, acc0, input0, input1, input2, input3)
                        acc1 = XXH128_mix32B_once(seed, BYTE_BASE + 96 + 16, acc1, input2, input3, input0, input1)
                    }
                    val input0: Long = access.i64(input, off + 32)
                    val input1: Long = access.i64(input, off + 32 + 8)
                    val input2: Long = access.i64(input, off + length - 48)
                    val input3: Long = access.i64(input, off + length - 48 + 8)
                    acc0 = XXH128_mix32B_once(seed, BYTE_BASE + 64, acc0, input0, input1, input2, input3)
                    acc1 = XXH128_mix32B_once(seed, BYTE_BASE + 64 + 16, acc1, input2, input3, input0, input1)
                }
                val input0: Long = access.i64(input, off + 16)
                val input1: Long = access.i64(input, off + 16 + 8)
                val input2: Long = access.i64(input, off + length - 32)
                val input3: Long = access.i64(input, off + length - 32 + 8)
                acc0 = XXH128_mix32B_once(seed, BYTE_BASE + 32, acc0, input0, input1, input2, input3)
                acc1 = XXH128_mix32B_once(seed, BYTE_BASE + 32 + 16, acc1, input2, input3, input0, input1)
            }
            val input0: Long = access.i64(input, off + 0)
            val input1: Long = access.i64(input, off + 0 + 8)
            val input2: Long = access.i64(input, off + length - 16)
            val input3: Long = access.i64(input, off + length - 16 + 8)
            acc0 = XXH128_mix32B_once(seed, BYTE_BASE, acc0, input0, input1, input2, input3)
            acc1 = XXH128_mix32B_once(seed, BYTE_BASE + 16, acc1, input2, input3, input0, input1)

            val low = XXH3_avalanche(acc0 + acc1)
            if (null != result) {
                result[0] = low
                result[1] =
                    -XXH3_avalanche(acc0 * XXH_PRIME64_1 + acc1 * XXH_PRIME64_4 + (length - seed) * XXH_PRIME64_2)
            }
            return low
        }

        if (length <= 240) {
            // XXH3_len_129to240_128b
            val nbRounds = length.toInt() / 32
            var acc0 = length * XXH_PRIME64_1
            var acc1: Long = 0
            var i = 0
            while (i < 4) {
                val input0: Long = access.i64(input, off + 32 * i)
                val input1: Long = access.i64(input, off + 32 * i + 8)
                val input2: Long = access.i64(input, off + 32 * i + 16)
                val input3: Long = access.i64(input, off + 32 * i + 24)
                acc0 = XXH128_mix32B_once(seed, BYTE_BASE + 32 * i, acc0, input0, input1, input2, input3)
                acc1 = XXH128_mix32B_once(seed, BYTE_BASE + 32 * i + 16, acc1, input2, input3, input0, input1)
                ++i
            }
            acc0 = XXH3_avalanche(acc0)
            acc1 = XXH3_avalanche(acc1)

            while (i < nbRounds) {
                val input0: Long = access.i64(input, off + 32 * i)
                val input1: Long = access.i64(input, off + 32 * i + 8)
                val input2: Long = access.i64(input, off + 32 * i + 16)
                val input3: Long = access.i64(input, off + 32 * i + 24)
                acc0 = XXH128_mix32B_once(seed, BYTE_BASE + 3 + 32 * (i - 4), acc0, input0, input1, input2, input3)
                acc1 = XXH128_mix32B_once(seed, BYTE_BASE + 3 + 32 * (i - 4) + 16, acc1, input2, input3, input0, input1)
                ++i
            }

            /* last bytes */
            val input0: Long = access.i64(input, off + length - 16)
            val input1: Long = access.i64(input, off + length - 16 + 8)
            val input2: Long = access.i64(input, off + length - 32)
            val input3: Long = access.i64(input, off + length - 32 + 8)
            acc0 = XXH128_mix32B_once(-seed, BYTE_BASE + 136 - 17 - 16, acc0, input0, input1, input2, input3)
            acc1 = XXH128_mix32B_once(-seed, BYTE_BASE + 136 - 17, acc1, input2, input3, input0, input1)

            val low = XXH3_avalanche(acc0 + acc1)
            if (null != result) {
                result[0] = low
                result[1] =
                    -XXH3_avalanche(acc0 * XXH_PRIME64_1 + acc1 * XXH_PRIME64_4 + (length - seed) * XXH_PRIME64_2)
            }
            return low
        }

        // XXH3_hashLong_128b_internal
        var acc_0 = XXH_PRIME32_3
        var acc_1 = XXH_PRIME64_1
        var acc_2 = XXH_PRIME64_2
        var acc_3 = XXH_PRIME64_3
        var acc_4 = XXH_PRIME64_4
        var acc_5 = XXH_PRIME32_2
        var acc_6 = XXH_PRIME64_5
        var acc_7 = XXH_PRIME32_1

        // XXH3_hashLong_internal_loop
        val nb_blocks = (length - 1) / block_len
        for (n in 0..<nb_blocks) {
            // XXH3_accumulate
            val offBlock = off + n * block_len
            for (s in 0..<nbStripesPerBlock) {
                // XXH3_accumulate_512
                val offStripe = offBlock + s * 64
                val offSec = s * 8
                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 0)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 1)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 0)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 1)
                    /* swap adjacent lanes */
                    acc_0 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_1 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }
                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 2)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 3)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 2)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 3)
                    /* swap adjacent lanes */
                    acc_2 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_3 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }
                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 4)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 5)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 4)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 5)
                    /* swap adjacent lanes */
                    acc_4 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_5 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }
                run {
                    val data_val_0: Long = access.i64(input, offStripe + 8 * 6)
                    val data_val_1: Long = access.i64(input, offStripe + 8 * 7)
                    val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 6)
                    val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 7)
                    /* swap adjacent lanes */
                    acc_6 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                    acc_7 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
                }
            }

            // XXH3_scrambleAcc_scalar
            val offSec: Long = BYTE_BASE + 192 - 64
            acc_0 = (acc_0 xor (acc_0 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 0)) * XXH_PRIME32_1
            acc_1 = (acc_1 xor (acc_1 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 1)) * XXH_PRIME32_1
            acc_2 = (acc_2 xor (acc_2 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 2)) * XXH_PRIME32_1
            acc_3 = (acc_3 xor (acc_3 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 3)) * XXH_PRIME32_1
            acc_4 = (acc_4 xor (acc_4 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 4)) * XXH_PRIME32_1
            acc_5 = (acc_5 xor (acc_5 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 5)) * XXH_PRIME32_1
            acc_6 = (acc_6 xor (acc_6 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 6)) * XXH_PRIME32_1
            acc_7 = (acc_7 xor (acc_7 ushr 47) xor unsafeLE.i64(secret, offSec + 8 * 7)) * XXH_PRIME32_1
        }

        /* last partial block */
        val nbStripes = ((length - 1) - (block_len * nb_blocks)) / 64
        val offBlock = off + block_len * nb_blocks
        for (s in 0..<nbStripes) {
            // XXH3_accumulate_512
            val offStripe = offBlock + s * 64
            val offSec = s * 8
            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 0)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 1)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 0)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 1)
                /* swap adjacent lanes */
                acc_0 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_1 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }
            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 2)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 3)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 2)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 3)
                /* swap adjacent lanes */
                acc_2 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_3 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }
            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 4)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 5)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 4)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 5)
                /* swap adjacent lanes */
                acc_4 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_5 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }
            run {
                val data_val_0: Long = access.i64(input, offStripe + 8 * 6)
                val data_val_1: Long = access.i64(input, offStripe + 8 * 7)
                val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 6)
                val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 7)
                /* swap adjacent lanes */
                acc_6 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
                acc_7 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
            }
        }

        /* last stripe */
        // XXH3_accumulate_512
        val offStripe = off + length - 64
        val offSec = (192 - 64 - 7).toLong()
        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 0)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 1)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 0)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 1)
            /* swap adjacent lanes */
            acc_0 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_1 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }
        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 2)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 3)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 2)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 3)
            /* swap adjacent lanes */
            acc_2 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_3 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }
        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 4)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 5)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 4)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 5)
            /* swap adjacent lanes */
            acc_4 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_5 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }
        run {
            val data_val_0: Long = access.i64(input, offStripe + 8 * 6)
            val data_val_1: Long = access.i64(input, offStripe + 8 * 7)
            val data_key_0 = data_val_0 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 6)
            val data_key_1 = data_val_1 xor XXH3.unsafeLE.i64(secret, BYTE_BASE + offSec + 8 * 7)
            /* swap adjacent lanes */
            acc_6 += data_val_1 + (0xFFFFFFFFL and data_key_0) * (data_key_0 ushr 32)
            acc_7 += data_val_0 + (0xFFFFFFFFL and data_key_1) * (data_key_1 ushr 32)
        }

        // XXH3_mergeAccs
        val low = XXH3_avalanche(
            (length * XXH_PRIME64_1 + XXH3_mix2Accs(acc_0, acc_1, secret, BYTE_BASE + 11)
                    + XXH3_mix2Accs(acc_2, acc_3, secret, BYTE_BASE + 11 + 16)
                    + XXH3_mix2Accs(acc_4, acc_5, secret, BYTE_BASE + 11 + 16 * 2)
                    + XXH3_mix2Accs(acc_6, acc_7, secret, BYTE_BASE + 11 + 16 * 3))
        )
        if (null != result) {
            result[0] = low
            result[1] = XXH3_avalanche(
                ((length * XXH_PRIME64_2).inv()
                        + XXH3_mix2Accs(acc_0, acc_1, secret, BYTE_BASE + 192 - 64 - 11)
                        + XXH3_mix2Accs(acc_2, acc_3, secret, BYTE_BASE + 192 - 64 - 11 + 16)
                        + XXH3_mix2Accs(acc_4, acc_5, secret, BYTE_BASE + 192 - 64 - 11 + 16 * 2)
                        + XXH3_mix2Accs(acc_6, acc_7, secret, BYTE_BASE + 192 - 64 - 11 + 16 * 3))
            )
        }
        return low
    }

    private fun XXH3_initCustomSecret(customSecret: ByteArray, seed64: Long) {
        val nbRounds = 192 / 16
        val bb = ByteBuffer.wrap(customSecret).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..<nbRounds) {
            val lo: Long = unsafeLE.i64(XXH3_kSecret, BYTE_BASE + 16 * i) + seed64
            val hi: Long = unsafeLE.i64(XXH3_kSecret, BYTE_BASE + 16 * i + 8) - seed64
            bb.putLong(16 * i + 0, lo)
            bb.putLong(16 * i + 8, hi)
        }
    }
}