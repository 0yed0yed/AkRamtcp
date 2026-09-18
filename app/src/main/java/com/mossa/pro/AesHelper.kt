package com.mossa.pro

import android.util.Log
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesHelper {

    private const val TAG = "AesHelper"

    // headers معروفة لكل نوع (بالبايت)
    private val HEADER_SIZES = mapOf(
        "0515" to 6, "1200" to 6, "0500" to 6, "1215" to 6,
        "0300" to 6, "0E00" to 6, "0E15" to 6, "0F00" to 6,
        "0F15" to 6, "1600" to 6, "1D00" to 6, "2F33" to 6
    )

    // offsets نجربها لو ما عرفناش الـ header
    private val FALLBACK_OFFSETS = listOf(6, 4, 8, 10, 12, 14, 16, 20, 24, 32)

    /**
     * يفك تشفير الباكيت كامل (مع الـ header).
     * @param fullHex - الـ hex كامل بدون مسافات
     * @return decrypted hex (نص protobuf) أو null
     */
    fun decryptPacket(fullHex: String, keyInts: IntArray, ivInts: IntArray): String? {
        if (fullHex.length < 12) return null

        val type = fullHex.substring(0, 4).uppercase()
        val knownHeader = HEADER_SIZES[type]

        // 1. لو النوع معروف — جرب الـ header بتاعه
        if (knownHeader != null) {
            val result = tryDecryptAt(fullHex, knownHeader, keyInts, ivInts)
            if (result != null) return result
        }

        // 2. Fallback — جرب كل offsets
        for (off in FALLBACK_OFFSETS) {
            val result = tryDecryptAt(fullHex, off, keyInts, ivInts)
            if (result != null) {
                Log.i(TAG, "Decrypted with offset=$off (type=$type)")
                return result
            }
        }

        return null
    }

    /**
     * يجرب فك تشفير بـ offset معين.
     */
    private fun tryDecryptAt(fullHex: String, headerBytes: Int, keyInts: IntArray, ivInts: IntArray): String? {
        val headerHexChars = headerBytes * 2
        if (fullHex.length <= headerHexChars) return null

        val bodyHex = fullHex.substring(headerHexChars)

        // لازم الطول يقبل القسمة على 32 (16 بايت = 32 hex char)
        val aligned = if (bodyHex.length % 32 != 0) {
            bodyHex.substring(0, bodyHex.length - (bodyHex.length % 32))
        } else bodyHex

        if (aligned.isEmpty()) return null

        val decrypted = decrypt(aligned, keyInts, ivInts) ?: return null

        // تحقق إن ده protobuf صحيح
        if (isValidProtobuf(decrypted)) return decrypted

        return null
    }

    /**
     * يتأكد إن الـ hex الناتج يبان protobuf
     */
    private fun isValidProtobuf(hex: String): Boolean {
        if (hex.length < 4) return false

        // أول بايت لازم يكون tag صالح
        try {
            val firstByte = hex.substring(0, 2).toInt(16)

            // protobuf tags شائعة: 0x08, 0x12, 0x1A, 0x22, 0x2A, 0x42, 0x52
            // wire types: 0 (varint), 2 (length-delim), 5 (32-bit)
            val fieldNum = firstByte shr 3
            val wireType = firstByte and 0x07

            if (fieldNum < 1 || fieldNum > 30) return false
            if (wireType !in listOf(0, 1, 2, 5)) return false

            // tag شائع = 0x08 (field 1, varint)
            if (firstByte == 0x08) return true
            if (firstByte == 0x12) return true   // field 2, bytes
            if (firstByte == 0x1A) return true   // field 3, bytes
            if (firstByte == 0x22) return true   // field 4, bytes
            if (firstByte == 0x2A) return true   // field 5, bytes
            if (firstByte == 0x42) return true   // field 8, bytes

            // معايير إضافية
            if (fieldNum in 1..20 && wireType == 2) return true

            return false
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * AES-CBC decrypt (بدون header logic)
     */
    fun decrypt(encryptedHex: String, keyInts: IntArray, ivInts: IntArray): String? {
        return try {
            val key = ByteArray(keyInts.size) { keyInts[it].toByte() }
            val iv = ByteArray(ivInts.size) { ivInts[it].toByte() }
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val enc = hexToBytes(encryptedHex)
            val dec = cipher.doFinal(enc)
            val unpadded = unpadPkcs7(dec)
            bytesToHex(unpadded)
        } catch (e: Exception) {
            null
        }
    }

    fun encrypt(plainHex: String, keyInts: IntArray, ivInts: IntArray): String? {
        return try {
            val key = ByteArray(keyInts.size) { keyInts[it].toByte() }
            val iv = ByteArray(ivInts.size) { ivInts[it].toByte() }
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val plain = hexToBytes(plainHex)
            val padded = padPkcs7(plain)
            val enc = cipher.doFinal(padded)
            bytesToHex(enc)
        } catch (e: Exception) {
            null
        }
    }

    private fun padPkcs7(data: ByteArray): ByteArray {
        val padLen = 16 - (data.size % 16)
        val out = ByteArray(data.size + padLen)
        System.arraycopy(data, 0, out, 0, data.size)
        for (i in data.size until out.size) out[i] = padLen.toByte()
        return out
    }

    private fun unpadPkcs7(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val padLen = data.last().toInt() and 0xFF
        if (padLen < 1 || padLen > 16 || padLen > data.size) return data
        return data.copyOf(data.size - padLen)
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
}
