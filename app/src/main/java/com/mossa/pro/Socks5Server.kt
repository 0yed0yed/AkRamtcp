package com.mossa.pro

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class Socks5Server(
    private val port: Int,
    private val onPacket: (PacketInfo) -> Unit
) {
    companion object {
        const val TAG = "AkRamtcp"
        const val SOCKS5_VERSION: Byte = 5
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: java.net.ServerSocket? = null
    private val counter = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool()

    @Volatile var currentKey: IntArray = Config.DEFAULT_KEY.copyOf()
    @Volatile var currentIv: IntArray = Config.DEFAULT_IV.copyOf()

    fun start() {
        if (running.get()) return
        try {
            serverSocket = java.net.ServerSocket()
            serverSocket?.reuseAddress = true
            serverSocket?.bind(java.net.InetSocketAddress(Config.PROXY_HOST, port))
            running.set(true)
            Log.i(TAG, "SOCKS5 listening on $port")

            thread(name = "socks-accept") {
                while (running.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        pool.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (running.get()) Log.e(TAG, "accept: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "start: ${e.message}")
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // --- handshake ---
            val ver = input.read().toByte()
            if (ver != SOCKS5_VERSION) { client.close(); return }
            val nmethods = input.read()
            val methods = ByteArray(nmethods)
            input.read(methods)
            // رد: طريقة 0 (بدون auth)
            output.write(byteArrayOf(SOCKS5_VERSION, 0))
            output.flush()

            // --- request ---
            val reqVer = input.read().toByte()
            if (reqVer != SOCKS5_VERSION) { client.close(); return }
            val cmd = input.read()
            input.read()  // RSV
            val atyp = input.read()

            val address: String = when (atyp) {
                1 -> {
                    val b = ByteArray(4); input.read(b)
                    "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.${b[3].toInt() and 0xFF}"
                }
                3 -> {
                    val len = input.read()
                    val b = ByteArray(len); input.read(b)
                    String(b, Charsets.UTF_8)
                }
                4 -> {
                    val b = ByteArray(16); input.read(b)
                    InetAddress.getByAddress(b).hostAddress ?: "0.0.0.0"
                }
                else -> { client.close(); return }
            }

            val portHi = input.read()
            val portLo = input.read()
            val port = (portHi shl 8) or portLo

            if (cmd.toInt() != 1) {  // CONNECT فقط
                client.close(); return
            }

            // --- connect remote ---
            val remote = Socket()
            remote.tcpNoDelay = true
            try {
                remote.connect(java.net.InetSocketAddress(address, port), 10000)
            } catch (e: Exception) {
                output.write(byteArrayOf(SOCKS5_VERSION, 5, 0, 1, 0, 0, 0, 0, 0, 0))
                output.flush()
                client.close()
                return
            }

            // --- reply success ---
            val bindAddr = remote.localAddress.address
            val bindPort = remote.localPort
            val reply = byteArrayOf(
                SOCKS5_VERSION, 0, 0, 1,
                bindAddr[0], bindAddr[1], bindAddr[2], bindAddr[3],
                ((bindPort shr 8) and 0xFF).toByte(),
                (bindPort and 0xFF).toByte()
            )
            output.write(reply)
            output.flush()

            // --- exchange loop ---
            exchange(client, remote, input, output)
        } catch (e: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun exchange(client: Socket, remote: Socket, cin: InputStream, cout: OutputStream) {
        val rin = remote.getInputStream()
        val rout = remote.getOutputStream()

        val t1 = thread(name = "c2s") {
            try {
                val buf = ByteArray(8192)
                while (running.get()) {
                    val n = cin.read(buf)
                    if (n <= 0) break
                    val data = buf.copyOf(n)
                    analyzeAndReport(data, "SERVER")   // من العميل (اللعبة) → السيرفر
                    rout.write(data); rout.flush()
                }
            } catch (_: Exception) {}
            try { remote.close() } catch (_: Exception) {}
        }

        val t2 = thread(name = "s2c") {
            try {
                val buf = ByteArray(8192)
                while (running.get()) {
                    val n = rin.read(buf)
                    if (n <= 0) break
                    val data = buf.copyOf(n)
                    analyzeAndReport(data, "CLIENT")   // من السيرفر → اللعبة
                    cout.write(data); cout.flush()
                }
            } catch (_: Exception) {}
            try { client.close() } catch (_: Exception) {}
        }

        t1.join()
        t2.join()
    }

    private fun analyzeAndReport(data: ByteArray, direction: String) {
        try {
            val hex = bytesToHex(data)
            val type = if (hex.length >= 4) hex.substring(0, 4).uppercase() else "0000"

            val decrypted = AesHelper.decrypt(hex, currentKey, currentIv)
            val decoded = if (decrypted != null) ProtobufDecoder.decode(decrypted)
                          else ProtobufDecoder.decode(hex)

            val info = PacketInfo(
                number = counter.incrementAndGet(),
                hex = hex,
                direction = direction,
                type = type,
                decrypted = decrypted,
                decoded = decoded,
                timestamp = System.currentTimeMillis(),
                datetime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
            )
            onPacket(info)
        } catch (e: Exception) {
            Log.e(TAG, "analyze: ${e.message}")
        }
    }

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02x", x))
        return sb.toString()
    }
}
