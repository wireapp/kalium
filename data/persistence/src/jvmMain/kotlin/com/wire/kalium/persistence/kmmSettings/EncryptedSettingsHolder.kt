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
import java.io.FileWriter
import java.nio.file.Paths
import java.util.Properties

private fun onModify(properties: Properties, file: File) {
    properties.store(FileWriter(file), "Store values to properties file")
}

private fun createOrLoad(rootPath: String, file: File): Properties {
    val properties = Properties()
    File(rootPath).mkdirs()
    if (!file.exists()) {
        println(file.absolutePath)
        file.createNewFile()
    }
    FileInputStream(file).use {
        properties.load(it)
    }
    return properties
}

internal actual fun buildSettings(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    val file = File(Paths.get(param.rootPath, options.fileName).toString())
    if (param.protectSettings) {
        check(options.shouldEncryptData) { "Encryption is required for protected desktop settings" }
        return DesktopProtectedSettings(file.toPath(), param.identity ?: file.toPath())
    }
    val properties = createOrLoad(param.rootPath, file)

    return PropertiesSettings(properties) { onModify(it, file) }
}

internal actual class EncryptedSettingsPlatformParam(
    val rootPath: String,
    val protectSettings: Boolean = false,
    val identity: java.nio.file.Path? = null
)
