package com.mossa.pro

import android.content.Context
import java.io.File

object PacketStore {

    fun save(context: Context, packet: PacketInfo): String? {
        return try {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            val folder = File(base, "packets_${packet.type}")
            if (!folder.exists()) folder.mkdirs()

            val filename = "packet_${packet.number}_${System.currentTimeMillis()}.txt"
            val f = File(folder, filename)
            f.writeText(formatPacket(packet))
            f.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun formatPacket(p: PacketInfo): String {
        val sb = StringBuilder()
        sb.append("# Packet Type: ${p.type} (${PacketTypes.name(p.type)})\n")
        sb.append("# Packet Number: ${p.number}\n")
        sb.append("# Direction: ${p.direction}\n")
        sb.append("# Time: ${p.datetime}\n")
        sb.append("# Size: ${p.hex.length / 2} bytes\n\n")

        sb.append("HEX = \"${p.hex}\"\n\n")

        if (p.decrypted != null) {
            sb.append("DECRYPTED_HEX = \"${p.decrypted}\"\n\n")
        }

        if (p.decoded != null) {
            sb.append("# Decoded:\n")
            sb.append("DECODED = ${ProtobufDecoder.format(p.decoded)}\n")
        }

        return sb.toString()
    }
}
