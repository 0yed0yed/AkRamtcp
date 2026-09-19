package com.mossa.pro

/**
 * Protobuf decoder v3 — يدعم:
 * - nesting متعدد المستويات
 * - repeated fields (القوائم)
 * - strings + bytes + varint + 64-bit + 32-bit
 */
object ProtobufDecoder {

    fun decode(hex: String): Map<Int, Any>? {
        return try {
            var clean = hex.replace(" ", "").replace("\n", "")

            // محاذاة على أول "08" — skip أي header داخلي
            var startIdx = 0
            for (i in 0 until clean.length - 1 step 2) {
                if (clean.substring(i, i + 2) == "08") {
                    startIdx = i
                    break
                }
            }
            if (startIdx > 0) clean = clean.substring(startIdx)

            val data = hexToBytes(clean)
            parseMessage(data, 0, data.size, depth = 0)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMessage(data: ByteArray, start: Int, end: Int, depth: Int): Map<Int, Any>? {
        if (depth > 10) return null

        val result = LinkedHashMap<Int, Any>()
        var pos = start

        while (pos < end) {
            val tagResult = readVarint(data, pos) ?: break
            val tag = tagResult.first
            pos = tagResult.second

            val fieldNum = (tag shr 3).toInt()
            val wireType = (tag and 0x7).toInt()

            if (fieldNum < 1) break

            when (wireType) {
                0 -> {  // varint
                    val r = readVarint(data, pos) ?: break
                    pos = r.second
                    addValue(result, fieldNum, r.first)
                }

                1 -> {  // 64-bit
                    if (pos + 8 > end) break
                    var v = 0L
                    for (i in 0 until 8) {
                        v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                    }
                    pos += 8
                    addValue(result, fieldNum, v)
                }

                2 -> {  // length-delimited
                    val r = readVarint(data, pos) ?: break
                    pos = r.second
                    val lenInt = r.first.toInt()
                    if (lenInt < 0 || pos + lenInt > end) break

                    val bytes = data.copyOfRange(pos, pos + lenInt)
                    pos += lenInt

                    // جرب nested
                    val nested = tryParseNested(bytes, depth + 1)
                    if (nested != null && nested.isNotEmpty()) {
                        addValue(result, fieldNum, nested)
                    } else {
                        val text = tryDecodeString(bytes)
                        if (text != null) {
                            addValue(result, fieldNum, text)
                        } else {
                            addValue(result, fieldNum, bytesToHex(bytes))
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
                    addValue(result, fieldNum, v)
                }

                else -> break
            }
        }

        return if (result.isEmpty()) null else result
    }

    /**
     * يضيف القيمة في الـ map — لو في قيمة موجودة بنفس الـ field → يحولها لـ List
     */
    private fun addValue(map: LinkedHashMap<Int, Any>, fieldNum: Int, value: Any) {
        val existing = map[fieldNum]
        when (existing) {
            null -> map[fieldNum] = value
            is MutableList<*> -> {
                @Suppress("UNCHECKED_CAST")
                (existing as MutableList<Any>).add(value)
            }
            else -> {
                val list = mutableListOf<Any>()
                list.add(existing)
                list.add(value)
                map[fieldNum] = list
            }
        }
    }

    private fun tryParseNested(bytes: ByteArray, depth: Int): Map<Int, Any>? {
        if (bytes.isEmpty()) return null
        return try {
            val nested = parseMessage(bytes, 0, bytes.size, depth)
            if (nested != null && nested.isNotEmpty() &&
                nested.keys.all { it in 1..100000 }) nested else null
        } catch (e: Exception) {
            null
        }
    }

    private fun tryDecodeString(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return try {
            val text = String(bytes, Charsets.UTF_8)
            if (text.all {
                    it.code in 0x09..0x7E ||
                    it.code in 0x0600..0x06FF ||    // Arabic
                    it.code in 0x00A0..0x00FF        // Latin-1 supplement
                }) {
                text
            } else null
        } catch (e: Exception) {
            null
        }
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

    fun format(data: Map<Int, Any>?, indent: Int = 0): String {
        if (data == null || data.isEmpty()) return "{}"
        val sb = StringBuilder()
        sb.append("{\n")
        val spaces = "  ".repeat(indent + 1)
        data.forEach { entry ->
            sb.append(spaces).append(entry.key).append(": ")
            sb.append(formatValue(entry.value, indent + 1))
            sb.append(",\n")
        }
        sb.append("  ".repeat(indent)).append("}")
        return sb.toString()
    }

    private fun formatValue(v: Any?, indent: Int): String {
        return when (v) {
            null -> "null"
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                format(v as Map<Int, Any>, indent)
            }
            is List<*> -> {
                val sb = StringBuilder()
                sb.append("[\n")
                val spaces = "  ".repeat(indent + 1)
                v.forEach { item ->
                    sb.append(spaces).append(formatValue(item, indent + 1)).append(",\n")
                }
                sb.append("  ".repeat(indent)).append("]")
                sb.toString()
            }
            is String -> "\"$v\""
            else -> v.toString()
        }
    }
}
