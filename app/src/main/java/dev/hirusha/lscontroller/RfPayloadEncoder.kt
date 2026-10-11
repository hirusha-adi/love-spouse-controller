// SPDX-FileCopyrightText: 2026 Hirusha Adikari
// SPDX-License-Identifier: MIT

package dev.hirusha.lscontroller

object RfPayloadEncoder {

    private const val LFSR_KEY_PASS1 = 0x3F
    private const val LFSR_KEY_PASS2 = 0x25
    private val PREAMBLE = intArrayOf(0x71, 0x0F, 0x55)

    fun encode(address: ByteArray, payload: ByteArray): ByteArray {
        val al = address.size
        val pl = payload.size
        val bs = al + 18 + pl + 2
        val buf = IntArray(bs)

        buf[15] = PREAMBLE[0]
        buf[16] = PREAMBLE[1]
        buf[17] = PREAMBLE[2]

        for (i in 0 until al) {
            buf[al - 1 + 18 - i] = address[i].toInt() and 0xFF
        }

        val d = al + 18
        for (i in 0 until pl) {
            buf[d + i] = payload[i].toInt() and 0xFF
        }

        for (j in 0 until al + 3) {
            buf[15 + j] = bitReverse8(buf[15 + j])
        }

        val crc = crc16(address, payload)
        val cp = d + pl
        buf[cp] = crc and 0xFF
        buf[cp + 1] = (crc shr 8) and 0xFF

        lfsr(buf, al + 2 + pl, lfsrExpand(LFSR_KEY_PASS1), 18)
        lfsr(buf, bs, lfsrExpand(LFSR_KEY_PASS2), 0)

        return ByteArray(bs - 15) { buf[15 + it].toByte() }
    }

    fun encode(address: ByteArray, commandByte: Int): ByteArray {
        return encode(address, byteArrayOf(commandByte.toByte()))
    }

    private fun bitReverse8(v: Int): Int {
        var x = v and 0xFF
        var r = 0
        for (i in 0 until 8) {
            r = (r shl 1) or (x and 1)
            x = x shr 1
        }
        return r
    }

    private fun bitReverse16(v: Int): Int {
        var x = v and 0xFFFF
        var r = 0
        for (i in 0 until 16) {
            r = (r shl 1) or (x and 1)
            x = x shr 1
        }
        return r
    }

    private fun crcStep(v: Int): Int {
        var x = v and 0xFFFF
        for (i in 0 until 8) {
            x = if (x and 0x8000 != 0) {
                ((x shl 1) xor 0x1021) and 0xFFFF
            } else {
                (x shl 1) and 0xFFFF
            }
        }
        return x
    }

    private fun crc16(addr: ByteArray, pay: ByteArray): Int {
        var c = 0xFFFF
        for (i in addr.size - 1 downTo 0) {
            c = crcStep(((addr[i].toInt() and 0xFF) shl 8) xor c)
        }
        for (b in pay) {
            c = crcStep(c xor (bitReverse8(b.toInt() and 0xFF) shl 8))
        }
        return bitReverse16(c).inv() and 0xFFFF
    }

    private fun lfsrExpand(k: Int): IntArray {
        return intArrayOf(
            1,
            (k shr 5) and 1,
            (k shr 4) and 1,
            (k shr 3) and 1,
            (k shr 2) and 1,
            (k shr 1) and 1,
            k and 1
        )
    }

    private fun lfsr(buf: IntArray, n: Int, st: IntArray, off: Int) {
        for (i in 0 until n) {
            val s0 = st[0]; val s1 = st[1]; val s2 = st[2]; val s3 = st[3]
            val s4 = st[4]; val s5 = st[5]; val s6 = st[6]

            val v12 = s2 xor s5
            val v14 = s1 xor s4
            val v15 = s6 xor s3
            val v16 = v15 xor s0
            val v17 = v12 xor s6

            st[0] = v17; st[1] = v16; st[2] = v14; st[3] = v12
            st[4] = s3 xor v12; st[5] = v16 xor s4; st[6] = v14 xor s5

            val d = buf[i + off] and 0xFF
            buf[i + off] =
                ((d and 0x80) xor ((v17 and 1) shl 7)) or
                ((d and 0x40) xor ((v16 and 1) shl 6)) or
                ((d and 0x20) xor ((v14 and 1) shl 5)) or
                ((d and 0x10) xor ((v12 and 1) shl 4)) or
                ((d and 0x08) xor ((v15 and 1) shl 3)) or
                ((d and 0x04) xor ((s4 and 1) shl 2)) or
                ((d and 0x02) xor ((s5 and 1) shl 1)) or
                ((d and 0x01) xor (s6 and 1))
        }
    }
}
