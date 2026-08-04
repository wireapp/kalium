/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileSystemOperations
import org.gradle.process.ExecOperations
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val AVS_CACHE_LAYOUT_VERSION: Int = 1
internal const val AVS_CACHE_READY_MARKER: String = ".kalium-avs-cache-ready"

/**
 * Maintains the content-addressed AVS cache shared by every module and concurrent Gradle build.
 *
 * The archive is hashed, never trusted by file metadata, and the extracted framework slices are only
 * ever produced from an archive that matched the checksum baked into this plugin.
 */
internal object AppleAvsRuntimeCacheService {
    fun prepareArchive(
        archiveUrl: String,
        expectedSha256: String,
        localArchive: File?,
        destination: File,
    ) {
        withCacheLock(destination) {
            if (destination.isFile && destination.sha256() == expectedSha256) return@withCacheLock

            destination.parentFile.mkdirs()
            val temporary = destination.resolveSibling(
                "${destination.name}.part-${UUID.randomUUID()}"
            )
            try {
                if (localArchive != null) {
                    Files.copy(
                        localArchive.toPath(),
                        temporary.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } else {
                    download(archiveUrl, temporary)
                }

                verifyChecksum(temporary, expectedSha256)
                moveAtomically(temporary, destination)
            } finally {
                Files.deleteIfExists(temporary.toPath())
            }
        }
    }

    fun extractFrameworks(
        archive: File,
        destination: File,
        expectedSha256: String,
        archiveOperations: ArchiveOperations,
        fileSystemOperations: FileSystemOperations,
        execOperations: ExecOperations,
    ) {
        withCacheLock(destination) {
            if (isFrameworkCacheReady(destination, expectedSha256)) return@withCacheLock
            verifyCachedArchive(archive, expectedSha256)

            val temporary = destination.resolveSibling(
                "${destination.name}.part-${UUID.randomUUID()}"
            )
            try {
                fileSystemOperations.delete { delete(temporary) }
                fileSystemOperations.sync {
                    from(archiveOperations.zipTree(archive)) {
                        include("avs.xcframework/Info.plist")
                        AvsApplePlatform.entries.forEach { platform ->
                            include(
                                "avs.xcframework/${platform.xcframeworkSlice}/avs.framework/**"
                            )
                        }
                        includeEmptyDirs = false
                    }
                    into(temporary)
                }

                normalizeMacosFramework(temporary, execOperations)
                validateExtractedFrameworks(temporary)
                temporary.resolve(AVS_CACHE_READY_MARKER).writeText(
                    frameworkCacheMarker(temporary, expectedSha256)
                )

                fileSystemOperations.delete { delete(destination) }
                moveAtomically(temporary, destination)
            } finally {
                fileSystemOperations.delete { delete(temporary) }
            }
        }
    }

    private fun download(archiveUrl: String, destination: File) {
        val uri = try {
            URI(archiveUrl)
        } catch (exception: Exception) {
            throw GradleException("Invalid archive URL: $archiveUrl", exception)
        }

        // Restrict downloads to approved HTTPS hosts to prevent SSRF.
        // Update this allowlist only for trusted release hosts used by this plugin.
        val allowedHosts = setOf(
            "github.com",
            "githubusercontent.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        val host = uri.host?.lowercase()
            ?: throw GradleException("Unsupported archive URL: missing host")

        if (
            uri.scheme != "https" ||
            uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443) ||
            host !in allowedHosts
        ) {
            throw GradleException("Unsupported archive URL: $archiveUrl")
        }

        val connection = uri.toURL().openConnection().apply {
            connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MILLIS
            readTimeout = DOWNLOAD_READ_TIMEOUT_MILLIS
            setRequestProperty("User-Agent", "kalium-apple-avs-runtime-gradle-plugin")
        }
        if (connection is HttpURLConnection) {
            connection.instanceFollowRedirects = true
            val responseCode = connection.responseCode
            if (responseCode !in HTTP_SUCCESS_RANGE) {
                throw GradleException(
                    "Failed to download AVS XCFramework: HTTP $responseCode from $archiveUrl"
                )
            }
        }
        connection.getInputStream().use { input ->
            Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun normalizeMacosFramework(
        extractedDirectory: File,
        execOperations: ExecOperations,
    ) {
        val macosInfoPlist = extractedDirectory.resolve(
            "avs.xcframework/${AvsApplePlatform.MACOS.xcframeworkSlice}/" +
                "avs.framework/Info.plist"
        )

        execOperations.exec {
            commandLine(
                "/usr/bin/plutil",
                "-replace",
                "CFBundleSupportedPlatforms",
                "-json",
                "[\"MacOSX\"]",
                macosInfoPlist.absolutePath,
            )
        }

        IOS_ONLY_MACOS_PLIST_KEYS.forEach { key ->
            execOperations.exec {
                isIgnoreExitValue = true
                commandLine("/usr/bin/plutil", "-remove", key, macosInfoPlist.absolutePath)
            }
        }
    }

    private fun validateExtractedFrameworks(extractedDirectory: File) {
        AvsApplePlatform.entries.forEach { platform ->
            val framework = extractedDirectory.resolve(
                "avs.xcframework/${platform.xcframeworkSlice}/avs.framework"
            )
            if (!framework.resolve("avs").isFile || !framework.resolve("Info.plist").isFile) {
                throw GradleException(
                    "AVS framework slice '${platform.xcframeworkSlice}' is incomplete"
                )
            }
        }
    }

    private fun withCacheLock(cacheEntry: File, action: () -> Unit) {
        val cacheKeyDirectory = cacheEntry.parentFile
        cacheKeyDirectory.mkdirs()
        val lockFile = cacheKeyDirectory.resolve(".cache.lock")
        val deadline = System.nanoTime() + CACHE_LOCK_TIMEOUT_NANOS

        FileChannel.open(
            lockFile.toPath(),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            acquireCacheLock(channel, lockFile, deadline).use {
                action()
            }
        }
    }

    private fun acquireCacheLock(
        channel: FileChannel,
        lockFile: File,
        deadline: Long,
    ): FileLock {
        while (true) {
            tryAcquireCacheLock(channel)?.let { return it }
            if (System.nanoTime() >= deadline) {
                throw GradleException(
                    "Timed out waiting for the shared AVS cache lock: ${lockFile.absolutePath}"
                )
            }
            waitBeforeRetryingCacheLock()
        }
    }

    private fun tryAcquireCacheLock(channel: FileChannel): FileLock? =
        try {
            channel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }

    private fun waitBeforeRetryingCacheLock() {
        try {
            Thread.sleep(CACHE_LOCK_RETRY_MILLIS)
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw GradleException("Interrupted while waiting for the AVS cache lock", failure)
        }
    }

    private const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 30_000
    private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 300_000
    private const val CACHE_LOCK_RETRY_MILLIS = 100L
    private const val CACHE_LOCK_TIMEOUT_MINUTES = 10L
    private const val HTTP_SUCCESS_MIN = 200
    private const val HTTP_SUCCESS_MAX = 299
    private val CACHE_LOCK_TIMEOUT_NANOS =
        TimeUnit.MINUTES.toNanos(CACHE_LOCK_TIMEOUT_MINUTES)
    private val HTTP_SUCCESS_RANGE = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
    private val IOS_ONLY_MACOS_PLIST_KEYS = listOf(
        "BuildMachineOSBuild",
        "DTCompiler",
        "DTPlatformBuild",
        "DTPlatformName",
        "DTPlatformVersion",
        "DTSDKBuild",
        "DTSDKName",
        "DTXcode",
        "DTXCodeBuild",
        "MinimumOSVersion",
        "UIDeviceFamily",
    )
}

internal fun normalizedChecksum(value: String): String =
    value.lowercase().removePrefix("sha256:").also { checksum ->
        require(checksum.matches(SHA256_PATTERN)) {
            "Invalid AVS XCFramework SHA-256: '$value'"
        }
    }

internal fun frameworkCacheDirectoryName(): String =
    "frameworks-v$AVS_CACHE_LAYOUT_VERSION"

internal fun cacheMarker(checksum: String): String = "sha256=$checksum\n"

internal fun isArchiveCacheReady(archive: File, checksum: String): Boolean =
    archive.isFile && runCatching { archive.sha256() == checksum }.getOrDefault(false)

internal fun isFrameworkCacheReady(directory: File, checksum: String): Boolean =
    runCatching {
        val marker = directory.resolve(AVS_CACHE_READY_MARKER)
        val allBinariesPresent = AvsApplePlatform.entries.all { platform ->
            directory.resolve(
                "avs.xcframework/${platform.xcframeworkSlice}/avs.framework/avs"
            ).isFile
        }
        marker.isFile && allBinariesPresent &&
            marker.readText() == frameworkCacheMarker(directory, checksum)
    }.getOrDefault(false)

/** Verifies a freshly downloaded or copied archive before it is published into the cache. */
private fun verifyChecksum(archive: File, expectedSha256: String) {
    val actual = archive.sha256()
    if (actual != expectedSha256) {
        throw GradleException(
            "AVS XCFramework checksum mismatch: expected $expectedSha256, got $actual"
        )
    }
}

/**
 * Verifies an archive already sitting in the cache.
 *
 * A mismatch means the cache entry cannot be trusted, so it is discarded rather than extracted;
 * [AppleAvsRuntimeCacheService.prepareArchive] then downloads it again on the next build instead of
 * leaving the build permanently broken.
 */
internal fun verifyCachedArchive(archive: File, expectedSha256: String) {
    if (!archive.isFile) {
        throw GradleException("The AVS XCFramework archive is missing: ${archive.absolutePath}")
    }
    val actual = archive.sha256()
    if (actual != expectedSha256) {
        archive.delete()
        throw GradleException(
            "Discarded the cached AVS XCFramework archive at ${archive.absolutePath} because it " +
                "does not match the expected checksum (expected $expectedSha256, got $actual). " +
                "Re-run the build to download it again."
        )
    }
}

private fun File.sha256(): String = inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private val SHA256_PATTERN = Regex("[0-9a-f]{64}")

private fun moveAtomically(source: File, destination: File) {
    destination.parentFile.mkdirs()
    Files.move(
        source.toPath(),
        destination.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
    )
}
