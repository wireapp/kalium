package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.kmm_settings.EncryptedSettingsHolder
import com.wire.kalium.persistence.kmm_settings.SettingOptions
import org.junit.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptedSettingsHolderTest {

    @Test
    fun givenJvmPropertiesSettings_WhenUserSettingsAreChanged_ThenItIsStoredInFile() {
        val rootPath = Files.createTempDirectory("test-rootPath").toString()
        val userIDEntity = QualifiedIDEntity("user", "domain")

        val encryptedSettingsHolder = EncryptedSettingsHolder(
            rootPath,
            SettingOptions.UserSettings(
                shouldEncryptData = false,
                userIDEntity
            )
        )
        encryptedSettingsHolder.encryptedSettings.putString("test-key", "test-value")

        val expectedSettingsFile = Paths.get(rootPath, "user-pref-${userIDEntity.value}-${userIDEntity.domain}").toFile()
        assertTrue(expectedSettingsFile.exists(), "User settings file was not created")
        val actualProperties = Properties()
        actualProperties.load(FileInputStream(expectedSettingsFile))
        assertEquals(actualProperties.getProperty("test-key"), "test-value", "User settings file contains wrong property")
    }

    @Test
    fun givenJvmPropertiesSettings_WhenAppSettingsAreChanged_ThenItIsStoredInFile() {
        val rootPath = Files.createTempDirectory("test-rootPath").toString()

        val encryptedSettingsHolder = EncryptedSettingsHolder(
            rootPath,
            SettingOptions.AppSettings(
                shouldEncryptData = false
            )
        )
        encryptedSettingsHolder.encryptedSettings.putString("test-key", "test-value")

        val expectedSettingsFile = Paths.get(rootPath, "app-preference").toFile()
        assertTrue(expectedSettingsFile.exists(), "App settings file was not created")
        val actualProperties = Properties()
        actualProperties.load(FileInputStream(expectedSettingsFile))
        assertEquals(actualProperties.getProperty("test-key"), "test-value", "App settings file contains wrong property")
    }

}
