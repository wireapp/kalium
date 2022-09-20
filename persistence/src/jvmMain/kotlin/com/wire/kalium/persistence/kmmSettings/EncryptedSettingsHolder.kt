package com.wire.kalium.persistence.kmmSettings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.JvmPropertiesSettings
import com.russhwolf.settings.Settings
import java.io.File
import java.io.FileInputStream
import java.io.FileWriter
import java.nio.file.Paths
import java.util.Properties

/**
 * the java implementation is not yet encrypted
 */

@OptIn(ExperimentalSettingsImplementation::class)
internal actual class EncryptedSettingsHolder(
    rootPath: String,
    options: SettingOptions
) {

    val file: File = File(Paths.get(rootPath, options.fileName).toString())
    val properties = createOrLoad(rootPath, file)

    // TODO(jvm): JvmPreferencesSettings is not encrypted
    actual val encryptedSettings: Settings = JvmPropertiesSettings(properties) { onModify(it, file) }

    private fun createOrLoad(rootPath: String, file: File): Properties {
        val properties = Properties()
        File(rootPath).mkdirs()
        if (!file.exists()) {
            System.out.println(file.absolutePath)
            file.createNewFile()
        }
        properties.load(FileInputStream(file))
        return properties
    }

    private fun onModify(properties: Properties, file: File) {
        properties.store(FileWriter(file), "Store values to properties file")
    }
}
