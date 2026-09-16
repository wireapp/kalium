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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties

private val fileWriteLock = Any()

/**
 * Writes the whole file to a temporary sibling, syncs it to disk and moves it over the old one.
 *
 * The file holds the auth tokens and the keys of the CoreCrypto keystores. Rewriting it in place
 * could leave a truncated file after a crash, and with it keystores that can no longer be decrypted.
 */
private fun onModify(properties: Properties, file: File) = synchronized(fileWriteLock) {
    val temporaryFile = File(file.parentFile, "${file.name}.tmp")
    FileOutputStream(temporaryFile).use { output ->
        properties.store(output, "Store values to properties file")
        output.fd.sync()
    }
    Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
}

private fun createOrLoad(rootPath: String, file: File): Properties {
    val properties = Properties()
    File(rootPath).mkdirs()
    if (!file.exists()) {
        file.createNewFile()
    }
    FileInputStream(file).use {
        properties.load(it)
    }
    return properties
}

// TODO(jvm): JvmPreferencesSettings is not encrypted
/**
 * the java implementation is not yet encrypted
 */
internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    val file = File(Paths.get(param.rootPath, options.fileName).toString())
    val properties = createOrLoad(param.rootPath, file)

    return PropertiesSettings(properties) { onModify(it, file) }
}

internal actual class EncryptedSettingsPlatformParam(val rootPath: String)
