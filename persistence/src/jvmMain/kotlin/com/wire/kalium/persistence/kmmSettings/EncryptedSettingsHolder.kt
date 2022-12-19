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

// TODO(jvm): JvmPreferencesSettings is not encrypted
/**
 * the java implementation is not yet encrypted
 */
internal actual fun encryptedSettingsBuilder(
    options: SettingOptions,
    param: EncryptedSettingsPlatformParam
): Settings {
    val file: File = File(Paths.get(param.rootPath, options.fileName).toString())
    val properties = createOrLoad(param.rootPath, file)

    return PropertiesSettings(properties) { onModify(it, file) }
}

internal actual class EncryptedSettingsPlatformParam(val rootPath: String)
