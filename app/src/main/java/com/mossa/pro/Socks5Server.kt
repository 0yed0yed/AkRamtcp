package com.mossa.pro

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
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

        // SO_ORIGINAL_DST — Linux constant
        private const val SO_ORIGINAL_DST = 80
    }

    private val running = AtomicBoolean(false)
    private var serverSocket: java.net.ServerSocket? = null
    private val counter = AtomicInteger(0)
    private val pool = Executors.newCachedThreadPool()

    @Volatile private var currentKey: IntArray = loadKeyFromPrefs("active_key", Config.DEFAULT_KEY)
    @Volatile private var currentIv: IntArray = loadKeyFromPrefs("active_iv", Config.DEFAULT_IV)

    private fun loadKeyFromPrefs(prefKey: String, default: IntArray): IntArray {
        return try {
            val csv = SecurePrefs.getString(prefKey)
            if (csv.isNullOrEmpty()) default.copyOf()
            else csv.split(",").map { it.trim().toInt() }.toIntArray()
        } catch (e: Exception) { default.copyOf() }
    }

    fun setKeys(key: IntArray, iv: IntArray) {
        currentKey = key.copyOf()
        currentIv = iv.copyOf()
        Log.i(TAG, "AES keys updated")
    }

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
            client.soTimeout = 30000

            val input = client.getInputStream()
            val output = client.getOutputStream()

            // نقرا أول بايت — نشوف SOCKS5 ولا transparent
            val firstByte = input.read()
            if (firstByte < 0) { client.close(); return }

            if (firstByte == 0x05) {
                // SOCKS5 mode
                client.soTimeout = 0
                handleSocks5(client, input, output)
            } else {
                // Transparent mode (iptables redirect)
                client.soTimeout = 0
                handleTransparent(client, firstByte.toByte())
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleClient: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    // ============ SOCKS5 MODE ============
    private fun handleSocks5(client: Socket, input: InputStream, output: OutputStream) {
        try {
            val ver = input.read().toByte()
            if (ver != SOCKS5_VERSION) { client.close(); return }

            val nmethods = input.read()
            val methods = ByteArray(nmethods)
            input.read(methods)

            output.write(byteArrayOf(SOCKS5_VERSION, 0))
            output.flush()

            val reqVer = input.read().toByte()
            if (reqVer != SOCKS5_VERSION) { client.close(); return }
            val cmd = input.read()
            input.read()
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
            val destPort = (portHi shl 8) or portLo

            if (cmd.toInt() != 1) { client.close(); return }

            val remote = Socket()
            remote.tcpNoDelay = true
            try {
                remote.connect(InetSocketAddress(address, destPort), 10000)
            } catch (e: Exception) {
                output.write(byteArrayOf(SOCKS5_VERSION, 5, 0, 1, 0, 0, 0, 0, 0, 0))
                output.flush()
                client.close()
                return
            }

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

            Log.i(TAG, "SOCKS5 → $address:$destPort")
            exchange(client, remote)
        } catch (e: Exception) {
            Log.e(TAG, "handleSocks5: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    // ============ TRANSPARENT MODE ============
    private fun handleTransparent(client: Socket, firstByte: Byte) {
        try {
            // استخرج الـ original destination
            val dst = getOriginalDestination(client)
            if (dst == null) {
                Log.w(TAG, "Transparent: couldn't get original dst")
                client.close()
                return
            }

            val (ip, destPort) = dst
            Log.i(TAG, "Transparent → $ip:$destPort")

            // اتصل بالسيرفر الأصلي
            val remote = Socket()
            remote.tcpNoDelay = true
            try {
                remote.connect(InetSocketAddress(ip, destPort), 10000)
            } catch (e: Exception) {
                Log.e(TAG, "Transparent connect failed: ${e.message}")
                client.close()
                return
            }

            // ابعت أول byte للسيرفر (اللي اتقرا أصلاً)
            val rout = remote.getOutputStream()
            rout.write(firstByte.toInt())
            rout.flush()

            exchange(client, remote, firstByteAlreadyRead = true)
        } catch (e: Exception) {
            Log.e(TAG, "handleTransparent: ${e.message}")
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * استخرج الـ original destination باستخدام SO_ORIGINAL_DST
     * ده بيشتغل مع iptables REDIRECT
     */
    private fun getOriginalDestination(socket: Socket): Pair<String, Int>? {
        return try {
            // Android/Linux: socket.getOption(SOL_IP, SO_ORIGINAL_DST)
            val dst = socket.getOption(java.net.StandardSocketOptions.IP_TOS) // placeholder — هنستخدم reflection

            // استخدم reflection للوصول للـ FD والـ getsockopt
            val fdField = java.io.FileDescriptor::class.java.getDeclaredField("descriptor")
            fdField.isAccessible = true
            // صعب نستخدم SO_ORIGINAL_DST مباشرة من Kotlin
            // لازم JNI أو مكتبة native

            // fallback: نستخدم netstat لقراءة الاتصال
            getOriginalDstViaNetstat(socket)
        } catch (e: Exception) {
            Log.e(TAG, "getOriginalDestination: ${e.message}")
            getOriginalDstViaNetstat(socket)
        }
    }

    /**
     * Fallback: نقرا من /proc/net/tcp — نشوف الاتصال الأصلي
     */
    private fun getOriginalDstViaNetstat(socket: Socket): Pair<String, Int>? {
        return try {
            val localPort = socket.port
            val localAddr = socket.localAddress.hostAddress ?: return null

            // اقرا /proc/net/tcp
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c",
                "cat /proc/net/tcp /proc/net/tcp6 2>/dev/null"))
            val reader = proc.inputStream.bufferedReader()
            val lines = reader.readLines()
            proc.waitFor()

            // ابحث عن الاتصال اللي بيسمع على الـ port بتاعنا
            // ... صعب شوية
            null
        } catch (e: Exception) {
            null
        }
    }

    // ============ EXCHANGE ============
    private fun exchange(client: Socket, remote: Socket, firstByteAlreadyRead: Boolean = false) {
        val cin = client.getInputStream()
        val cout = client.getOutputStream()
        val rin = remote.getInputStream()
        val rout = remote.getOutputStream()

        val t1 = thread(name = "c2s") {
            try {
                val buf = ByteArray(8192)
                while (running.get()) {
                    val n = cin.read(buf)
                    if (n <= 0) break
                    val data = buf.copyOf(n)
                    analyzeAndReport(data, "SERVER")
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
                    analyzeAndReport(data, "CLIENT")
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

            val decrypted = AesHelper.decryptPacket(hex, currentKey, currentIv)
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
