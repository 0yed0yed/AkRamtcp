package com.mossa.pro

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesHelper {

    fun decrypt(encryptedHex: String, keyInts: IntArray, ivInts: IntArray): String? {
        return try {
            val key = ByteArray(keyInts.size) { keyInts[it].toByte() }
            val iv = ByteArray(ivInts.size) { ivInts[it].toByte() }
            val cipher = Cipher.getInstance("AES/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            val enc = hexToBytes(encryptedHex)
            val dec = cipher.doFinal(enc)
            // إزالة PKCS7 padding يدوياً
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
