package com.mossa.pro

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يحلل MajorLogin response ويستخرج KEY/IV.
 *
 * الـ response = protobuf MyMessage:
 *   field21 (varint) = timestamp
 *   field22 (bytes 16) = KEY
 *   field23 (bytes 16) = IV
 *
 * الـ hex ممكن يبقى فيه header قبل الـ protobuf، بنتعامل معاه.
 */
object MajorLoginParser {

    const val TAG = "AkRamtcp-Parser"

    data class Result(
        val timestamp: Long,
        val key: ByteArray,
        val iv: ByteArray
    ) {
        fun keyHex(): String = bytesToHex(key)
        fun ivHex(): String = bytesToHex(iv)
        fun keyCsv(): String = key.joinToString(",") { (it.toInt() and 0xFF).toString() }
        fun ivCsv(): String = iv.joinToString(",") { (it.toInt() and 0xFF).toString() }
        fun timestampFormatted(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            return sdf.format(Date(timestamp * 1000L))
        }
    }

    fun parse(hexInput: String): Result? {
        try {
            // 1. نظّف
            val clean = hexInput.replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .trim()
            if (clean.length < 20 || clean.length % 2 != 0) {
                Log.e(TAG, "invalid hex length")
                return null
            }

            val bytes = hexToBytes(clean)

            // 2. دوّر على بداية MyMessage
            // markers: field21 = 0xA8 0x01, field22 = 0xB2 0x01, field23 = 0xBA 0x01
            val offsets = mutableListOf(0)
            for (i in 0 until minOf(500, bytes.size - 1)) {
                val b1 = bytes[i].toInt() and 0xFF
                val b2 = bytes[i + 1].toInt() and 0xFF
                if ((b1 == 0xA8 && b2 == 0x01) ||
                    (b1 == 0xB2 && b2 == 0x01) ||
                    (b1 == 0xBA && b2 == 0x01)) {
                    offsets.add(i)
                }
            }

            // 3. جرب كل offset
            for (off in offsets) {
                val result = tryParseAt(bytes, off)
                if (result != null) {
                    Log.i(TAG, "✓ Parsed at offset $off")
                    return result
                }
            }

            Log.e(TAG, "no valid MyMessage found")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "parse error: ${e.message}")
            return null
        }
    }

    private fun tryParseAt(bytes: ByteArray, start: Int): Result? {
        var pos = start
        var ts: Long? = null
        var key: ByteArray? = null
        var iv: ByteArray? = null

        while (pos < bytes.size) {
            val (tag, np) = readVarint(bytes, pos) ?: return null
            pos = np
            val fieldNum = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            when (wireType) {
                0 -> {  // varint
                    val (v, np2) = readVarint(bytes, pos) ?: return null
                    pos = np2
                    if (fieldNum == 21) ts = v
                }
                2 -> {  // length-delimited
                    val (len, np2) = readVarint(bytes, pos) ?: return null
                    pos = np2
                    val lenInt = len.toInt()
                    if (lenInt < 0 || pos + lenInt > bytes.size) break
                    val data = bytes.copyOfRange(pos, pos + lenInt)
                    pos += lenInt
                    when (fieldNum) {
                        22 -> key = data
                        23 -> iv = data
                    }
                }
                1 -> { pos += 8; if (pos > bytes.size) break }
                5 -> { pos += 4; if (pos > bytes.size) break }
                else -> break
            }
        }

        // تحقق
        if (key == null || iv == null) return null
        if (key.size != 16 || iv.size != 16) return null
        if (key.all { it == 0.toByte() }) return null
        if (iv.all { it == 0.toByte() }) return null

        return Result(
            timestamp = ts ?: (System.currentTimeMillis() / 1000),
            key = key,
            iv = iv
        )
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            pos++
            if ((b and 0x80) == 0) return Pair(result, pos)
            shift += 7
            if (shift > 63) return null
        }
        return null
    }

    private fun hexToBytes(s: String): ByteArray {
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(s[i*2], 16) shl 4) +
                     Character.digit(s[i*2+1], 16)).toByte()
        }
        return out
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x))
        return sb.toString()
    }
}
