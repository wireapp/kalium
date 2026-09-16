/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.FileNameUtil
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncryptedSettingsFileTest {

    private val rootDirectory = Files.createTempDirectory("encrypted-settings").toFile()
    private val settingsFile = File(rootDirectory, "app-preference")
    private val keyFile = File(rootDirectory, SettingsMasterKeys.KEY_FILE_NAME)
    private val pendingKeyFile = File(rootDirectory, SettingsMasterKeys.PENDING_KEY_FILE_NAME)
    private val user = UserIDEntity("user", "domain")
    private val userSettingsFile = File(rootDirectory, FileNameUtil.userPrefFile(user))
    private val keyStore = InMemoryMasterKeyStore()

    @AfterTest
    fun cleanUp() {
        rootDirectory.deleteRecursively()
    }

    @Test
    fun givenEncryption_whenSettingsAreWritten_thenTheFileHidesKeysAndValuesAndReadsBack() {
        appSettings().putString(SECRET_NAME, SECRET_VALUE)

        val content = settingsFile.readText(Charsets.ISO_8859_1)
        assertTrue(content.startsWith("kalium-encrypted-settings-v1\n"))
        assertFalse(content.contains(SECRET_NAME))
        assertFalse(content.contains(SECRET_VALUE))
        assertEquals(SECRET_VALUE, appSettings(SettingsMasterKeys { keyStore }).getStringOrNull(SECRET_NAME))
    }

    @Test
    fun givenAppAndUserSettingsInOneFolder_whenBothAreEncrypted_thenTheyShareOneMasterKey() {
        val masterKeys = SettingsMasterKeys { keyStore }
        appSettings(masterKeys).putString(SECRET_NAME, SECRET_VALUE)
        buildSettings(SettingOptions.UserSettings(true, UserIDEntity("user", "domain")), param(masterKeys)).putString("flag", "on")

        assertEquals(1, keyStore.keys.size)
        assertTrue(keyFile.readText().contains(keyStore.keys.keys.single()))
    }

    @Test
    fun givenMissingKeyFile_whenSettingsExist_thenLoadingFailsInsteadOfCreatingANewKey() {
        appSettings().putString(SECRET_NAME, SECRET_VALUE)
        keyFile.delete()

        assertFailsWith<SettingsEncryptionException> { appSettings() }
        assertEquals(1, keyStore.keys.size)
    }

    @Test
    fun givenKeyMissingFromTheKeyStore_whenSettingsAreLoaded_thenLoadingFails() {
        appSettings().putString(SECRET_NAME, SECRET_VALUE)

        assertFailsWith<SettingsEncryptionException> { appSettings(SettingsMasterKeys { InMemoryMasterKeyStore() }) }
    }

    @Test
    fun givenKeyFileOfAnotherKeyStore_whenSettingsAreLoaded_thenLoadingFails() {
        appSettings().putString(SECRET_NAME, SECRET_VALUE)

        assertFailsWith<SettingsEncryptionException> { appSettings(SettingsMasterKeys { InMemoryMasterKeyStore(name = "other") }) }
    }

    @Test
    fun givenChangedFile_whenSettingsAreLoaded_thenLoadingFails() {
        appSettings().putString(SECRET_NAME, SECRET_VALUE)
        val bytes = settingsFile.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        settingsFile.writeBytes(bytes)

        assertFailsWith<SettingsEncryptionException> { appSettings(SettingsMasterKeys { keyStore }) }
    }

    @Test
    fun givenPlaintextAppAndUserSettings_whenEncryptionStarts_thenBothAreEncryptedWithTheirValues() {
        appSettings(shouldEncryptData = false).putString(SECRET_NAME, SECRET_VALUE)
        userSettings(shouldEncryptData = false).putString(USER_SETTING_NAME, USER_SETTING_VALUE)

        appSettings()

        assertTrue(SettingsFileCipher.isEncrypted(settingsFile))
        assertTrue(SettingsFileCipher.isEncrypted(userSettingsFile))
        assertTrue(keyFile.exists())
        assertFalse(pendingKeyFile.exists())
        assertEquals(SECRET_VALUE, appSettings().getStringOrNull(SECRET_NAME))
        assertEquals(USER_SETTING_VALUE, userSettings().getStringOrNull(USER_SETTING_NAME))
    }

    @Test
    fun givenInterruptedMigration_whenSettingsAreLoaded_thenItFinishesWithTheSameKey() {
        appSettings(shouldEncryptData = false).putString(SECRET_NAME, SECRET_VALUE)
        appSettings()
        // As if the process ended after encrypting the app settings, before the user settings.
        keyFile.renameTo(pendingKeyFile)
        userSettings(shouldEncryptData = false).putString(USER_SETTING_NAME, USER_SETTING_VALUE)

        assertEquals(USER_SETTING_VALUE, userSettings().getStringOrNull(USER_SETTING_NAME))
        assertEquals(SECRET_VALUE, appSettings().getStringOrNull(SECRET_NAME))
        assertTrue(keyFile.exists())
        assertFalse(pendingKeyFile.exists())
        assertEquals(1, keyStore.keys.size)
    }

    @Test
    fun givenMasterKey_whenASettingsFileIsPlaintext_thenLoadingFails() {
        appSettings().putString(SECRET_NAME, SECRET_VALUE)
        userSettings(shouldEncryptData = false).putString(USER_SETTING_NAME, USER_SETTING_VALUE)

        assertFailsWith<SettingsEncryptionException> { userSettings() }
    }

    @Test
    fun givenEncryptionOff_whenSettingsAreWritten_thenTheFileStaysPlaintextWithoutMasterKey() {
        appSettings(shouldEncryptData = false).putString(SECRET_NAME, SECRET_VALUE)

        assertTrue(settingsFile.readText(Charsets.ISO_8859_1).contains(SECRET_VALUE))
        assertFalse(keyFile.exists())
        assertTrue(keyStore.keys.isEmpty())
    }

    @Test
    fun givenKeyOfWrongSize_whenCipherIsCreated_thenItIsRejected() {
        assertFailsWith<IllegalArgumentException> { SettingsFileCipher(ByteArray(16)) }
    }

    private fun appSettings(
        masterKeys: SettingsMasterKeys = SettingsMasterKeys { keyStore },
        shouldEncryptData: Boolean = true
    ) = buildSettings(SettingOptions.AppSettings(shouldEncryptData), param(masterKeys))

    private fun userSettings(shouldEncryptData: Boolean = true) =
        buildSettings(SettingOptions.UserSettings(shouldEncryptData, user), param(SettingsMasterKeys { keyStore }))

    private fun param(masterKeys: SettingsMasterKeys) = EncryptedSettingsPlatformParam(rootDirectory.path, masterKeys)

    private class InMemoryMasterKeyStore(override val name: String = "in-memory") : MasterKeyStore {
        val keys = HashMap<String, ByteArray>()

        override fun store(key: ByteArray): String = UUID.randomUUID().toString().also { keys[it] = key.copyOf() }

        override fun load(reference: String): ByteArray =
            keys[reference]?.copyOf() ?: throw SettingsEncryptionException("No key for $reference")
    }

    private companion object {
        const val SECRET_NAME = "user_db_secret_alias_v2_user@domain"
        // No characters that Properties escapes, so the plaintext check can search for it directly.
        const val SECRET_VALUE = "c2VjcmV0LWtleS1tYXRlcmlhbA"
        const val USER_SETTING_NAME = "file_sharing"
        const val USER_SETTING_VALUE = "on"
    }
}
