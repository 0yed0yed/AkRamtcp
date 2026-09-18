package com.mossa.pro

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mossa.pro.databinding.ActivityPacketDetailBinding

class PacketDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NUMBER = "packet_number"
    }

    private lateinit var binding: ActivityPacketDetailBinding
    private var packet: PacketInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPacketDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val number = intent.getIntExtra(EXTRA_NUMBER, -1)
        packet = PacketRegistry.get(number)

        if (packet == null) {
            Toast.makeText(this, "Packet not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        bind(packet!!)

        binding.btnSendTg.setOnClickListener {
            TelegramBridge.sendPacket(packet!!)
            Toast.makeText(this, "Sent to Telegram", Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            val path = PacketStore.save(this, packet!!)
            if (path != null) {
                Toast.makeText(this, "Saved:\n$path", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("packet", packet!!.hex))
            Toast.makeText(this, "HEX copied", Toast.LENGTH_SHORT).show()
        }

        binding.btnShare.setOnClickListener {
            val p = packet!!
            val text = buildString {
                append("Packet #${p.number} — ${p.type}\n")
                append("Direction: ${p.direction}\n")
                append("Time: ${p.datetime}\n")
                append("Size: ${p.hex.length / 2} bytes\n\n")
                append("HEX:\n${p.hex}\n\n")
                if (p.decrypted != null) append("Decrypted:\n${p.decrypted}\n\n")
                if (p.decoded != null) append("Decoded:\n${ProtobufDecoder.format(p.decoded)}\n")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(intent, "Share packet"))
        }
    }

    private fun bind(p: PacketInfo) {
        binding.tvHeader.text = "#${p.number}  ${p.type} (${PacketTypes.name(p.type)})"
        binding.tvHeader.setTextColor(android.graphics.Color.parseColor(PacketTypes.color(p.type)))

        binding.tvMeta.text = buildString {
            append("Direction: ${p.direction}\n")
            append("Time: ${p.datetime}\n")
            append("Size: ${p.hex.length / 2} bytes\n")
        }

        binding.tvHex.text = formatHex(p.hex)

        binding.tvDecrypted.text = if (p.decrypted != null) {
            formatHex(p.decrypted)
        } else {
            "— no decrypted data —"
        }

        binding.tvDecoded.text = if (p.decoded != null) {
            ProtobufDecoder.format(p.decoded)
        } else {
            "— no decoded data —"
        }
    }

    private fun formatHex(hex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < hex.length) {
            val end = minOf(i + 64, hex.length)
            sb.append(hex.substring(i, end)).append("\n")
            i = end
        }
        return sb.toString().trimEnd()
    }
}
