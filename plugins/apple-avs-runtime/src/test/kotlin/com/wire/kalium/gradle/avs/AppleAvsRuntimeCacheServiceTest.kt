/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.gradle.avs

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppleAvsRuntimeCacheServiceTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun givenValidLocalArchive_whenPreparing_thenPublishesVerifiedArchive() {
        val source = temporaryDirectory.resolve("source.zip").apply {
            writeBytes("official avs archive".encodeToByteArray())
        }
        val destination = temporaryDirectory.resolve("cache/avs.xcframework.zip")

        AppleAvsRuntimeCacheService.prepareArchive(
            archiveUrl = "unused",
            expectedSha256 = source.sha256(),
            localArchive = source.toFile(),
            destination = destination.toFile(),
        )

        assertContentEquals(source.readBytes(), destination.readBytes())
        assertTrue(isArchiveCacheReady(destination.toFile(), source.sha256()))
        assertTrue(destination.parent.listDirectoryEntries("*.part-*").isEmpty())
    }

    @Test
    fun givenArchiveWithWrongChecksum_whenPreparing_thenRejectsWithoutPublishingPartialContent() {
        val source = temporaryDirectory.resolve("source.zip").apply {
            writeBytes("tampered avs archive".encodeToByteArray())
        }
        val destination = temporaryDirectory.resolve("cache/avs.xcframework.zip")

        val failure = assertFailsWith<GradleException> {
            AppleAvsRuntimeCacheService.prepareArchive(
                archiveUrl = "unused",
                expectedSha256 = "0".repeat(64),
                localArchive = source.toFile(),
                destination = destination.toFile(),
            )
        }

        assertTrue(failure.message.orEmpty().contains("checksum mismatch"))
        assertFalse(destination.exists())
        assertTrue(destination.parent.listDirectoryEntries("*.part-*").isEmpty())
    }

    @Test
    fun givenCorruptedCachedArchive_whenPreparing_thenReplacesItWithVerifiedContent() {
        val source = temporaryDirectory.resolve("source.zip").apply {
            writeBytes("official avs archive".encodeToByteArray())
        }
        val destination = temporaryDirectory.resolve("cache/avs.xcframework.zip").apply {
            parent.createDirectories()
            writeBytes("corrupted cache".encodeToByteArray())
        }

        AppleAvsRuntimeCacheService.prepareArchive(
            archiveUrl = "unused",
            expectedSha256 = source.sha256(),
            localArchive = source.toFile(),
            destination = destination.toFile(),
        )

        assertContentEquals(source.readBytes(), destination.readBytes())
    }

    @Test
    fun givenVerifiedRemoteArchiveBehindRedirect_whenPreparing_thenDownloadsAndPublishesIt() {
        val archiveBytes = "downloaded official archive".encodeToByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/redirect") { exchange ->
                exchange.responseHeaders.add("Location", "/archive")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            createContext("/archive") { exchange ->
                exchange.sendResponseHeaders(200, archiveBytes.size.toLong())
                exchange.responseBody.use { it.write(archiveBytes) }
            }
            start()
        }
        val destination = temporaryDirectory.resolve("cache/avs.xcframework.zip")

        try {
            AppleAvsRuntimeCacheService.prepareArchive(
                archiveUrl = "http://127.0.0.1:${server.address.port}/redirect",
                expectedSha256 = archiveBytes.sha256(),
                localArchive = null,
                destination = destination.toFile(),
            )
        } finally {
            server.stop(0)
        }

        assertContentEquals(archiveBytes, destination.readBytes())
    }

    @Test
    fun givenRemoteServerFailure_whenPreparing_thenFailsWithoutPublishingPartialContent() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/archive") { exchange ->
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            }
            start()
        }
        val destination = temporaryDirectory.resolve("cache/avs.xcframework.zip")

        val failure = try {
            assertFailsWith<GradleException> {
                AppleAvsRuntimeCacheService.prepareArchive(
                    archiveUrl = "http://127.0.0.1:${server.address.port}/archive",
                    expectedSha256 = "0".repeat(64),
                    localArchive = null,
                    destination = destination.toFile(),
                )
            }
        } finally {
            server.stop(0)
        }

        assertTrue(failure.message.orEmpty().contains("HTTP 503"))
        assertFalse(destination.exists())
        assertTrue(destination.parent.listDirectoryEntries("*.part-*").isEmpty())
    }

    @Test
    fun givenCorruptedCachedArchive_whenVerifying_thenDeletesItBeforeFailing() {
        val archive = temporaryDirectory.resolve("avs.xcframework.zip").apply {
            writeBytes("corrupted cache".encodeToByteArray())
        }

        assertFailsWith<GradleException> {
            verifyCachedArchive(archive.toFile(), "0".repeat(64))
        }

        assertFalse(archive.exists())
    }

    @Test
    fun givenConcurrentPrepareRequests_whenRunning_thenOnlyACompleteVerifiedEntryIsPublished() {
        val source = temporaryDirectory.resolve("source.zip").apply {
            writeBytes(ByteArray(128 * 1024) { index -> (index % 251).toByte() })
        }
        val checksum = source.sha256()
        val destination = temporaryDirectory.resolve("cache/avs.xcframework.zip")
        val executor = Executors.newFixedThreadPool(4)

        try {
            val requests = List(8) {
                Callable {
                    AppleAvsRuntimeCacheService.prepareArchive(
                        archiveUrl = "unused",
                        expectedSha256 = checksum,
                        localArchive = source.toFile(),
                        destination = destination.toFile(),
                    )
                }
            }
            executor.invokeAll(requests).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }

        assertEquals(checksum, destination.sha256())
        assertTrue(destination.parent.listDirectoryEntries("*.part-*").isEmpty())
    }

    @Test
    fun givenVerifiedFrameworkCache_whenRuntimeBinaryIsModified_thenCacheIsRejected() {
        val cache = createCompleteFrameworkCache()
        val checksum = "a".repeat(64)
        cache.resolve(AVS_CACHE_READY_MARKER).writeText(
            frameworkCacheMarker(cache.toFile(), checksum)
        )
        assertTrue(isFrameworkCacheReady(cache.toFile(), checksum))

        cache.resolve(
            "avs.xcframework/${AvsApplePlatform.IOS_DEVICE.xcframeworkSlice}/avs.framework/avs"
        ).writeText("substituted executable")

        assertFalse(isFrameworkCacheReady(cache.toFile(), checksum))
    }

    @Test
    fun givenVerifiedFrameworkCache_whenInfoPlistOrMarkerIsModified_thenCacheIsRejected() {
        val cache = createCompleteFrameworkCache()
        val checksum = "b".repeat(64)
        val marker = cache.resolve(AVS_CACHE_READY_MARKER)
        marker.writeText(frameworkCacheMarker(cache.toFile(), checksum))

        cache.resolve(
            "avs.xcframework/${AvsApplePlatform.MACOS.xcframeworkSlice}/avs.framework/Info.plist"
        ).writeText("substituted plist")
        assertFalse(isFrameworkCacheReady(cache.toFile(), checksum))

        marker.writeText(frameworkCacheMarker(cache.toFile(), checksum))
        marker.writeText(marker.toFile().readText().replace("sha256=$checksum", "sha256=${"c".repeat(64)}"))
        assertFalse(isFrameworkCacheReady(cache.toFile(), checksum))
    }

    @Test
    fun givenFrameworkCacheWithEscapingSymlink_whenCreatingIntegrityMarker_thenRejectsIt() {
        val cache = createCompleteFrameworkCache()
        val outside = temporaryDirectory.resolve("outside-library").apply {
            writeText("untrusted executable")
        }
        val binary = cache.resolve(
            "avs.xcframework/${AvsApplePlatform.MACOS.xcframeworkSlice}/avs.framework/avs"
        )
        Files.delete(binary)
        Files.createSymbolicLink(binary, outside)

        val failure = assertFailsWith<IllegalArgumentException> {
            frameworkCacheMarker(cache.toFile(), "a".repeat(64))
        }

        assertTrue(failure.message.orEmpty().contains("symlink escapes"))
    }

    @Test
    fun givenUppercasePrefixedChecksum_whenNormalizing_thenReturnsLowercaseDigest() {
        val checksum = "A1".repeat(32)

        assertEquals(checksum.lowercase(), normalizedChecksum("sha256:$checksum"))
    }

    @Test
    fun givenMalformedChecksum_whenNormalizing_thenRejectsIt() {
        assertFailsWith<IllegalArgumentException> {
            normalizedChecksum("not-a-sha256")
        }
    }

    private fun createCompleteFrameworkCache(): Path =
        temporaryDirectory.resolve("framework-cache").apply {
            AvsApplePlatform.entries.forEach { platform ->
                resolve("avs.xcframework/${platform.xcframeworkSlice}/avs.framework").apply {
                    createDirectories()
                    resolve("avs").writeText("${platform.name} executable")
                    resolve("Info.plist").writeText("${platform.name} plist")
                }
            }
        }
}

private fun Path.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(readBytes())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
