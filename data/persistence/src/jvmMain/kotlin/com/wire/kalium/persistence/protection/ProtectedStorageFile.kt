/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.protection

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.HexFormat

private const val STORAGE_KEY_BYTES = 32
private val processLock = Any()

internal fun <T> withProtectedFileLock(file: Path, action: () -> T): T = synchronized(processLock) {
    Files.createDirectories(file.parent)
    val lock = file.resolveSibling("${file.fileName}.protection.lock")
    FileChannel.open(lock, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
        channel.lock().use { action() }
    }
}

internal fun loadProtectedFileKey(store: JvmStorageKeyStore, file: Path, allowCreation: Boolean): ByteArray {
    val identity = "Kalium JVM file v1/${file.toFile().canonicalPath}".encodeToByteArray()
    val keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity))
    val key = store.load(keyId, allowCreation)
    if (key.size != STORAGE_KEY_BYTES) {
        key.fill(0)
        error("Invalid protected storage key length")
    }
    return key
}
