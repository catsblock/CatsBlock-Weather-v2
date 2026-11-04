package com.catsblock.weather.desktop

import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.thread

object SingleInstance {

    private val lockFile = File(AppPaths.dir, "instance.lock")
    private val infoFile = File(AppPaths.dir, "instance.port")
    private var channel: FileChannel? = null
    private var lock: FileLock? = null
    private var server: ServerSocket? = null

    fun acquire(silent: Boolean, onShow: () -> Unit): Boolean {
        AppPaths.dir.mkdirs()
        val ch = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val held = try {
            ch.tryLock()
        } catch (e: OverlappingFileLockException) {
            null
        } catch (e: IOException) {
            null
        }
        if (held == null) {
            ch.close()
            signalExisting(if (silent) "PING" else "SHOW")
            return false
        }
        channel = ch
        lock = held
        startServer(onShow)
        return true
    }

    private fun startServer(onShow: () -> Unit) {
        val socket = ServerSocket(0, 8, InetAddress.getLoopbackAddress())
        server = socket
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        infoFile.writeText("${socket.localPort}\n$token\n")
        runCatching {
            infoFile.setReadable(false, false)
            infoFile.setReadable(true, true)
            infoFile.setWritable(false, false)
            infoFile.setWritable(true, true)
        }
        thread(isDaemon = true, name = "single-instance") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                runCatching { handle(client, token, onShow) }
                runCatching { client.close() }
            }
        }
    }

    private fun readLine(client: Socket): String {
        val input = client.getInputStream()
        val sb = StringBuilder()
        while (sb.length < 128) {
            val b = input.read()
            if (b < 0 || b == '\n'.code) break
            sb.append(b.toChar())
        }
        return sb.toString().trim()
    }

    private fun handle(client: Socket, token: String, onShow: () -> Unit) {
        client.soTimeout = 2000
        val received = readLine(client)
        val command = readLine(client)
        if (!MessageDigest.isEqual(received.toByteArray(), token.toByteArray())) return
        if (command == "SHOW") onShow()
        client.getOutputStream().apply { write("OK\n".toByteArray()); flush() }
    }

    private fun signalExisting(command: String) {
        repeat(20) {
            val info = runCatching { infoFile.readLines() }.getOrNull()
            val port = info?.getOrNull(0)?.trim()?.toIntOrNull()
            val token = info?.getOrNull(1)?.trim()
            if (port != null && token != null) {
                val ok = runCatching {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500)
                        s.soTimeout = 2000
                        s.getOutputStream().apply { write("$token\n$command\n".toByteArray()); flush() }
                        readLine(s) == "OK"
                    }
                }.getOrDefault(false)
                if (ok) return
            }
            Thread.sleep(150)
        }
    }
}

object AppEvents {
    val showRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun show() {
        showRequests.tryEmit(Unit)
    }
}
