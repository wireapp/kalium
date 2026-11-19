/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.DWORD
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

internal fun atomicWrite(file: Path, bytes: ByteArray) {
    val temp = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
    try {
        FileOutputStream(temp.toFile()).use {
            it.write(bytes)
            it.fd.sync()
        }
        replaceDurably(temp, file)
    } finally {
        Files.deleteIfExists(temp)
    }
}

/** Source and target are siblings on the same filesystem. */
internal fun replaceDurably(source: Path, target: Path) {
    if (Platform.isWindows()) {
        val flags = DWORD((WinBase.MOVEFILE_WRITE_THROUGH or WinBase.MOVEFILE_REPLACE_EXISTING).toLong())
        if (!Kernel32.INSTANCE.MoveFileEx(source.toString(), target.toString(), flags)) {
            throw Win32Exception(Kernel32.INSTANCE.GetLastError())
        }
    } else {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        FileChannel.open(target.parent, StandardOpenOption.READ).use { it.force(true) }
    }
}
