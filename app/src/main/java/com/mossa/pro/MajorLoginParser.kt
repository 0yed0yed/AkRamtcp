package com.mossa.pro

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * يستخرج KEY/IV من MajorLogin response.
 *
 * الطريقة: يدور على markers في الـ bytes:
 *   B2 01 <len> <KEY bytes>   ← field22 = KEY
 *   BA 01 <len> <IV bytes>    ← field23 = IV
 *   A8 01 <varint>            ← field21 = timestamp
 *
 * مش محتاجين نحلل الـ protobuf كامل — بس ندوّر على markers.
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
            val clean = hexInput.replace(" ", "")
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .trim()
            if (clean.length < 20 || clean.length % 2 != 0) {
                Log.e(TAG, "invalid hex length: ${clean.length}")
                return null
            }

            val bytes = hexToBytes(clean)
            Log.i(TAG, "parsing ${bytes.size} bytes")

            // دوّر على field23 (IV) — آخر marker
            val iv = findBytesField(bytes, 0xBA)
            if (iv == null || iv.size != 16) {
                Log.e(TAG, "IV not found or wrong size: ${iv?.size}")
                return null
            }

            // دوّر على field22 (KEY)
            val key = findBytesField(bytes, 0xB2)
            if (key == null || key.size != 16) {
                Log.e(TAG, "KEY not found or wrong size: ${key?.size}")
                return null
            }

            // دوّر على field21 (timestamp) — varint
            val ts = findVarintField(bytes, 0xA8)

            Log.i(TAG, "✓ KEY=${bytesToHex(key)}")
            Log.i(TAG, "✓ IV=${bytesToHex(iv)}")
            Log.i(TAG, "✓ TS=${ts}")

            return Result(
                timestamp = ts ?: (System.currentTimeMillis() / 1000),
                key = key,
                iv = iv
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse error: ${e.message}", e)
            return null
        }
    }

    /**
     * يدور على pattern: <marker> 0x01 <varint length> <bytes>
     * marker مثلاً 0xB2 → bytes pattern [0xB2, 0x01, <len>, <data...>]
     */
    private fun findBytesField(bytes: ByteArray, marker: Int): ByteArray? {
        var i = 0
        while (i < bytes.size - 2) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 == marker && b1 == 0x01) {
                // اقرأ length كـ varint من i+2
                var pos = i + 2
                var len = 0L
                var shift = 0
                var lenBytes = 0
                while (pos < bytes.size && lenBytes < 5) {
                    val b = bytes[pos].toInt() and 0xFF
                    len = len or ((b and 0x7F).toLong() shl shift)
                    pos++
                    lenBytes++
                    if ((b and 0x80) == 0) break
                    shift += 7
                }
                val lenInt = len.toInt()
                if (lenInt > 0 && lenInt <= 64 && pos + lenInt <= bytes.size) {
                    val data = bytes.copyOfRange(pos, pos + lenInt)
                    // تحقق: مش كله صفر + مش printable ascii
                    if (!data.all { it == 0.toByte() } && !isPrintableAscii(data)) {
                        return data
                    }
                }
            }
            i++
        }
        return null
    }

    private fun findVarintField(bytes: ByteArray, marker: Int): Long? {
        var i = 0
        while (i < bytes.size - 2) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            if (b0 == marker && b1 == 0x01) {
                var pos = i + 2
                var result = 0L
                var shift = 0
                while (pos < bytes.size && shift < 64) {
                    val b = bytes[pos].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    pos++
                    if ((b and 0x80) == 0) return result
                    shift += 7
                }
            }
            i++
        }
        return null
    }

    private fun isPrintableAscii(b: ByteArray): Boolean =
        b.all { it.toInt() in 0x20..0x7E }

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
