package com.wire.kalium.persistence

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.kmmSettings.EncryptedSettingsPlatformParam
import com.wire.kalium.persistence.kmmSettings.SettingOptions
import com.wire.kalium.persistence.kmmSettings.encryptedSettingsBuilder
import org.junit.Test
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptedSettingsBuilderTest {

    @Test
    fun givenJvmPropertiesSettings_WhenUserSettingsAreChanged_ThenItIsStoredInFile() {
        val rootPath = Files.createTempDirectory("test-rootPath").toString()
        val userIDEntity = QualifiedIDEntity("user", "domain")

        val encryptedSettings = encryptedSettingsBuilder(
            SettingOptions.UserSettings(
                shouldEncryptData = false,
                userIDEntity
            ),
            EncryptedSettingsPlatformParam(rootPath)
        )
        encryptedSettings.putString("test-key", "test-value")

        val expectedSettingsFile = Paths.get(rootPath, "user-pref-${userIDEntity.value}-${userIDEntity.domain}").toFile()
        assertTrue(expectedSettingsFile.exists(), "User settings file was not created")
        val actualProperties = Properties()
        actualProperties.load(FileInputStream(expectedSettingsFile))
        assertEquals(actualProperties.getProperty("test-key"), "test-value", "User settings file contains wrong property")
    }

    @Test
    fun givenJvmPropertiesSettings_WhenAppSettingsAreChanged_ThenItIsStoredInFile() {
        val rootPath = Files.createTempDirectory("test-rootPath").toString()

        val encryptedSettings = encryptedSettingsBuilder(
            SettingOptions.AppSettings(
                shouldEncryptData = false
            ),
            EncryptedSettingsPlatformParam(rootPath)
        )
        encryptedSettings.putString("test-key", "test-value")

        val expectedSettingsFile = Paths.get(rootPath, "app-preference").toFile()
        assertTrue(expectedSettingsFile.exists(), "App settings file was not created")
        val actualProperties = Properties()
        actualProperties.load(FileInputStream(expectedSettingsFile))
        assertEquals(actualProperties.getProperty("test-key"), "test-value", "App settings file contains wrong property")
    }

}
