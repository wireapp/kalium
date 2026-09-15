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

package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.PropertiesSettings
import com.russhwolf.settings.Settings
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties

private val fileWriteLock = Any()

private const val MOVE_ATTEMPTS = 5
private const val MOVE_RETRY_DELAY_MILLIS = 50L

/**
 * Writes the whole file to a temporary sibling, syncs it to disk and moves it over the old one.
 *
 * The settings hold the auth tokens and the keys of the CoreCrypto keystores. Rewriting a file in
 * place could leave it truncated after a crash, and with it keystores that can no longer be decrypted.
 */
internal fun writeAtomically(
    file: File,
    content: ByteArray,
    move: (Path, Path) -> Unit = ::replaceAtomically
) = synchronized(fileWriteLock) {
    val temporaryFile = File(file.parentFile, "${file.name}.tmp")
    FileOutputStream(temporaryFile).use { output ->
        output.write(content)
        output.fd.sync()
    }
    retryWhileLocked { move(temporaryFile.toPath(), file.toPath()) }
}

private fun replaceAtomically(source: Path, target: Path) {
    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

/**
 * Runs [action] up to [attempts] times while it fails with a [FileSystemException].
 *
 * On Windows a virus scanner or the search indexer can hold a file open for a moment, and replacing
 * it fails until they let go. The last failure is rethrown.
 */
@Suppress("SwallowedException")
internal fun retryWhileLocked(
    attempts: Int = MOVE_ATTEMPTS,
    pause: (attempt: Int) -> Unit = { Thread.sleep(MOVE_RETRY_DELAY_MILLIS * it) },
    action: () -> Unit
) {
    repeat(attempts - 1) { attempt ->
        try {
            action()
            return
        } catch (exception: FileSystemException) {
            pause(attempt + 1)
        }
    }
    action()
}

private fun Properties.toBytes(): ByteArray =
    ByteArrayOutputStream().also { store(it, "Store values to properties file") }.toByteArray()

private fun loadPlaintext(file: File): Properties {
    if (!file.exists()) {
        file.createNewFile()
    }
    return Properties().apply { FileInputStream(file).use { load(it) } }
}

/**
 * Settings are plain properties files unless [SettingOptions.shouldEncryptData] is on. Then the whole
 * file is encrypted with a master key from the system key store, see [SettingsFileCipher] and
 * [SettingsMasterKeys].
 */
internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    File(param.rootPath).mkdirs()
    val file = File(Paths.get(param.rootPath, options.fileName).toString())
    val cipher = if (options.shouldEncryptData) SettingsFileCipher(param.masterKeys.forSettings(file)) else null
    val properties = cipher?.load(file) ?: loadPlaintext(file)

    return PropertiesSettings(properties) {
        val content = it.toBytes()
        writeAtomically(file, cipher?.encrypt(content) ?: content)
    }
}

/**
 * @param masterKeys where the master key of encrypted settings comes from; tests replace the system
 * key store.
 */
internal actual class EncryptedSettingsPlatformParam(
    val rootPath: String,
    val masterKeys: SettingsMasterKeys = SettingsMasterKeys.platform
)
