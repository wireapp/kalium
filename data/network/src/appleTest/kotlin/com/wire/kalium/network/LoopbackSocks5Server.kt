/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import okio.ByteString.Companion.encodeUtf8
import platform.posix.AF_INET
import platform.posix.SHUT_RDWR
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.shutdown
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval

/** A real SOCKS5 endpoint. It never resolves or connects to the requested destination. */
@OptIn(ExperimentalForeignApi::class)
internal class LoopbackSocks5Server(
    private val requireAuthentication: Boolean,
    private val webSocket: Boolean = false,
) {
    private val listener = socket(AF_INET, SOCK_STREAM, 0).also { check(it >= 0) }
    val port: Int = memScoped {
        val address = alloc<sockaddr_in>()
        address.sin_len = sizeOf<sockaddr_in>().convert()
        address.sin_family = AF_INET.convert()
        // Supported Apple targets are little endian; these bytes are 127.0.0.1 in network order.
        address.sin_addr.s_addr = 0x0100007fu
        address.sin_port = 0u
        check(bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0)
        check(listen(listener, 4) == 0)
        configureTimeout(listener)
        val length = alloc<socklen_tVar>()
        length.value = sizeOf<sockaddr_in>().convert()
        check(getsockname(listener, address.ptr.reinterpret(), length.ptr) == 0)
        address.sin_port.toInt().let { ((it and 0xff) shl 8) or ((it ushr 8) and 0xff) }
    }

    fun acceptOne(scope: CoroutineScope): Deferred<Handshake> = scope.async(Dispatchers.Default) {
        val peer = accept(listener, null, null)
        check(peer >= 0) { "SOCKS fixture accept failed" }
        try {
            configureTimeout(peer)
            val greeting = read(peer, 2)
            check(greeting[0] == 5.toByte())
            val methods = read(peer, greeting[1].unsigned()).map { it.unsigned() }
            val selectedMethod = if (requireAuthentication) 2 else 0
            write(peer, byteArrayOf(5, if (selectedMethod in methods) selectedMethod.toByte() else 0xff.toByte()))
            if (selectedMethod !in methods) return@async Handshake(methods)

            var credentials: Pair<String, String>? = null
            if (requireAuthentication) {
                val auth = read(peer, 2)
                check(auth[0] == 1.toByte())
                val username = read(peer, auth[1].unsigned()).decodeToString()
                val password = read(peer, read(peer, 1)[0].unsigned()).decodeToString()
                credentials = username to password
                val valid = credentials == ("proxy-user" to "proxy-password")
                write(peer, byteArrayOf(1, if (valid) 0 else 1))
                if (!valid) return@async Handshake(methods, credentials)
            }

            val connect = read(peer, 4)
            check(connect.take(3) == listOf<Byte>(5, 1, 0))
            val host = when (connect[3].unsigned()) {
                3 -> read(peer, read(peer, 1)[0].unsigned()).decodeToString()
                1 -> read(peer, 4).joinToString(".") { it.unsigned().toString() }
                else -> error("Expected domain name or IPv4 destination")
            }
            val destinationPort = read(peer, 2).let { it[0].unsigned() * 256 + it[1].unsigned() }
            write(peer, byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 80))
            val request = StringBuilder()
            while (!request.endsWith("\r\n\r\n")) {
                check(request.length < 8192) { "HTTP request too large" }
                request.append(read(peer, 1)[0].unsigned().toChar())
            }
            if (webSocket) {
                val key = request.lines().first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                    .substringAfter(':').trim()
                val accept = (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").encodeUtf8().sha1().base64()
                write(peer, ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\n" +
                    "Connection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n").encodeToByteArray())
                write(peer, byteArrayOf(0x81.toByte(), 8) + "proxy-ok".encodeToByteArray())
                write(peer, byteArrayOf(0x88.toByte(), 2, 3, 0xe8.toByte()))
            } else {
                write(peer, "HTTP/1.1 200 OK\r\nContent-Length: 8\r\nConnection: close\r\n\r\nproxy-ok".encodeToByteArray())
            }
            Handshake(methods, credentials, host, destinationPort, request.toString())
        } finally {
            close(peer)
        }
    }

    fun stop() {
        shutdown(listener, SHUT_RDWR)
        close(listener)
    }

    private fun configureTimeout(peer: Int) = memScoped {
        val timeout = alloc<timeval>()
        timeout.tv_sec = 5
        timeout.tv_usec = 0
        check(setsockopt(peer, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert()) == 0)
        val enabled = alloc<kotlinx.cinterop.IntVar>()
        enabled.value = 1
        check(setsockopt(peer, SOL_SOCKET, SO_NOSIGPIPE, enabled.ptr, sizeOf<kotlinx.cinterop.IntVar>().convert()) == 0)
    }

    private fun read(peer: Int, length: Int): ByteArray = ByteArray(length).also { bytes ->
        if (length == 0) return@also
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < length) {
                val count = recv(peer, pinned.addressOf(offset), (length - offset).convert(), 0).toInt()
                check(count > 0) { "SOCKS fixture received EOF or timed out" }
                offset += count
            }
        }
    }

    private fun write(peer: Int, bytes: ByteArray) = bytes.usePinned { pinned ->
        var offset = 0
        while (offset < bytes.size) {
            val count = send(peer, pinned.addressOf(offset), (bytes.size - offset).convert(), 0).toInt()
            check(count > 0) { "SOCKS fixture could not send response" }
            offset += count
        }
    }

    private fun Byte.unsigned() = toInt() and 0xff

    data class Handshake(
        val offeredMethods: List<Int>,
        val credentials: Pair<String, String>? = null,
        val destination: String? = null,
        val destinationPort: Int? = null,
        val request: String? = null,
    )
}
