package com.mossa.pro

/**
 * Protobuf decoder متقدم — يدعم nesting متعدد المستويات
 */
object ProtobufDecoder {

    fun decode(hex: String): Map<Int, Any>? {
        return try {
            var clean = hex.replace(" ", "").replace("\n", "")
            
            // ✅ محاذاة على أول "08" (field 1 tag) — زي Python script
            // نتخطى أي header داخلي
            var startIdx = 0
            for (i in 0 until clean.length - 1 step 2) {
                if (clean.substring(i, i + 2) == "08") {
                    startIdx = i
                    break
                }
            }
            if (startIdx > 0) {
                clean = clean.substring(startIdx)
            }
            
            val data = hexToBytes(clean)
            parseMessage(data, 0, data.size, depth = 0)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMessage(data: ByteArray, start: Int, end: Int, depth: Int): Map<Int, Any>? {
        if (depth > 10) return null  // منع recursion لانهائي

        val result = LinkedHashMap<Int, Any>()
        var pos = start

        while (pos < end) {
            // اقرأ tag
            val (tag, np1) = readVarint(data, pos) ?: break
            pos = np1

            val fieldNum = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            if (fieldNum < 1 || fieldNum > 536870911) break

            when (wireType) {
                0 -> {  // varint
                    val (value, np) = readVarint(data, pos) ?: break
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
                    val (len, np) = readVarint(data, pos) ?: break
                    pos = np
                    val lenInt = len.toInt()
                    if (lenInt < 0 || pos + lenInt > end) break

                    val bytes = data.copyOfRange(pos, pos + lenInt)
                    pos += lenInt

                    // حاول نفكها:
                    // 1. nested message
                    val nested = tryParseNested(bytes, depth + 1)
                    if (nested != null && nested.isNotEmpty()) {
                        result[fieldNum] = nested
                    } else {
                        // 2. نص
                        val text = tryDecodeString(bytes)
                        if (text != null) {
                            result[fieldNum] = text
                        } else {
                            // 3. bytes
                            result[fieldNum] = bytesToHex(bytes)
                        }
                    }
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

                else -> break
            }
        }

        return if (result.isEmpty()) null else result
    }

    private fun tryParseNested(bytes: ByteArray, depth: Int): Map<Int, Any>? {
        if (bytes.isEmpty()) return null
        return try {
            val nested = parseMessage(bytes, 0, bytes.size, depth)
            // لازم كل الـ field numbers تكون صالحة
            if (nested != null && nested.isNotEmpty() &&
                nested.keys.all { it in 1..100000 }) nested else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryDecodeString(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        // لازم كل الـ bytes تكون printable ASCII أو UTF-8 صالح
        try {
            val text = String(bytes, Charsets.UTF_8)
            // لازم كل حرف يكون printable (مش control characters)
            if (text.all { it.code in 0x09..0x7E || it.code in 0x0600..0x06FF || it.code in 0x00A0..0x00FF }) {
                return text
            }
        } catch (e: Exception) {}
        return null
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var pos = start
        var bytesRead = 0

        while (pos < data.size && bytesRead < 10) {
            val b = data[pos].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            pos++
            bytesRead++

            if ((b and 0x80) == 0) return Pair(result, pos)
            shift += 7
            if (shift > 63) return null
        }
        return null
    }

    private fun hexToBytes(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("\n", "")
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

    /**
     * Formatted output للـ UI
     */
    fun format(data: Map<Int, Any>?, indent: Int = 0): String {
        if (data == null || data.isEmpty()) return "{}"
        val sb = StringBuilder()
        sb.append("{\n")
        val spaces = "  ".repeat(indent + 1)
        data.forEach { entry ->
            val k = entry.key
            val v = entry.value
            sb.append(spaces).append(k).append(": ")
            when {
                v is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    sb.append(format(v as Map<Int, Any>, indent + 1))
                }
                v is String -> sb.append("\"").append(v).append("\"")
                v is Long -> {
                    // لو الرقم سالب كـ 64-bit (زي -1) نعرضه كـ 18446744073709551615
                    sb.append(v.toString())
                }
                else -> sb.append(v.toString())
            }
            sb.append(",\n")
        }
        sb.append("  ".repeat(indent)).append("}")
        return sb.toString()
    }
}
