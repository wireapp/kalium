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

import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Runs against a temporary keychain file, never the user's login keychain. Skipped outside macOS.
 */
class MacKeychainMasterKeyStoreTest {

    private val isMac = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)
    private val directory = Files.createTempDirectory("kalium-keychain").toFile()
    private val keychain = File(directory, "kalium-test.keychain-db")

    @BeforeTest
    fun createKeychain() {
        assumeTrue("macOS only", isMac)
        security("create-keychain", "-p", KEYCHAIN_PASSWORD, keychain.path)
        security("unlock-keychain", "-p", KEYCHAIN_PASSWORD, keychain.path)
    }

    @AfterTest
    fun deleteKeychain() {
        if (isMac && keychain.exists()) security("delete-keychain", keychain.path)
        directory.deleteRecursively()
    }

    @Test
    fun givenStoredKey_whenLoadedByItsReference_thenTheSameKeyComesBack() {
        val store = MacKeychainMasterKeyStore(keychain.path)
        val key = ByteArray(32) { (it * 7).toByte() }

        val reference = store.store(key)

        assertContentEquals(key, store.load(reference))
    }

    @Test
    fun givenUnknownReference_whenLoaded_thenItFails() {
        assertFailsWith<SettingsEncryptionException> { MacKeychainMasterKeyStore(keychain.path).load("no-such-entry") }
    }

    @Test
    fun givenKeychainKeyStore_whenEncryptedSettingsAreWrittenAndReopened_thenTheyReadBack() {
        val settingsDirectory = File(directory, "settings").path

        buildSettings(SettingOptions.AppSettings(true), param(settingsDirectory)).putString("secret", "value")

        assertEquals("value", buildSettings(SettingOptions.AppSettings(true), param(settingsDirectory)).getStringOrNull("secret"))
    }

    private fun param(rootPath: String) =
        EncryptedSettingsPlatformParam(rootPath, SettingsMasterKeys { MacKeychainMasterKeyStore(keychain.path) })

    private fun security(vararg arguments: String) {
        val process = ProcessBuilder(listOf("/usr/bin/security") + arguments).redirectErrorStream(true).start()
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "security ${arguments.first()} failed: ${process.inputStream.bufferedReader().readText()}"
        }
    }

    private companion object {
        const val KEYCHAIN_PASSWORD = "kalium-test"
        const val TIMEOUT_SECONDS = 30L
    }
}
