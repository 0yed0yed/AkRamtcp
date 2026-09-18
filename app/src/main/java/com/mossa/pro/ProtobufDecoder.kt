package com.mossa.pro

/**
 * Protobuf decoder بسيط (wire format فقط)
 * يدعم: varint (0), 64-bit (1), length-delimited (2), 32-bit (5)
 */
object ProtobufDecoder {

    fun decode(hex: String): Map<Int, Any>? {
        return try {
            val data = hexToBytes(hex)
            parseMessage(data, 0, data.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMessage(data: ByteArray, start: Int, end: Int): Map<Int, Any> {
        val result = LinkedHashMap<Int, Any>()
        var pos = start
        while (pos < end) {
            val (tag, newPos) = readVarint(data, pos)
            pos = newPos
            val fieldNum = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            when (wireType) {
                0 -> {  // varint
                    val (value, np) = readVarint(data, pos)
                    pos = np
                    result[fieldNum] = value
                }
                1 -> {  // 64-bit
                    if (pos + 8 > end) break
                    var v = 0L
                    for (i in 0 until 8) {
                        v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                    }
                    pos += 8
                    result[fieldNum] = v
                }
                2 -> {  // length-delimited
                    val (len, np) = readVarint(data, pos)
                    pos = np
                    val lenInt = len.toInt()
                    if (pos + lenInt > end) break
                    val bytes = data.copyOfRange(pos, pos + lenInt)
                    pos += lenInt

                    // حاول نفكها كـ message متداخلة، لو فشلت خزّنها كنص/bytes
                    val nested = tryNested(bytes)
                    result[fieldNum] = nested ?: bytesToHex(bytes)
                }
                5 -> {  // 32-bit
                    if (pos + 4 > end) break
                    var v = 0
                    for (i in 0 until 4) {
                        v = v or ((data[pos + i].toInt() and 0xFF) shl (8 * i))
                    }
                    pos += 4
                    result[fieldNum] = v
                }
                else -> break  // unsupported
            }
        }
        return result
    }

    private fun tryNested(bytes: ByteArray): Map<Int, Any>? {
        // لو أول بايت مش tag صالح، مش message
        if (bytes.isEmpty()) return null
        return try {
            val nested = parseMessage(bytes, 0, bytes.size)
            if (nested.isNotEmpty() && nested.keys.all { it in 1..1000 }) nested else null
        } catch (e: Exception) {
            null
        }
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var pos = start
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            pos++
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 63) break
        }
        return Pair(result, pos)
    }

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) +
                     Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x))
        return sb.toString()
    }

    fun format(data: Map<Int, Any>?, indent: Int = 0): String {
        if (data == null || data.isEmpty()) return "{}"
        val sb = StringBuilder()
        sb.append("{\n")
        val spaces = "    ".repeat(indent + 1)
        data.forEach { (k, v) ->
            sb.append(spaces).append(k).append(": ")
            when (v) {
                is Map<*, * -> {
                    @Suppress("UNCHECKED_CAST")
                    sb.append(format(v as Map<Int, Any>, indent + 1))
                }
                is String -> sb.append("\"").append(v).append("\"")
                else -> sb.append(v.toString())
            }
            sb.append(",\n")
        }
        sb.append("    ".repeat(indent)).append("}")
        return sb.toString()
    }
}
