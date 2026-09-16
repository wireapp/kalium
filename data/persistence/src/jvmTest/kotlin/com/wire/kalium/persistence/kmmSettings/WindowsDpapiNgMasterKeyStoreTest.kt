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
import java.nio.file.Files
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Runs on Windows only, with DPAPI-NG of the user running the tests; outside a domain, that is the local user. */
class WindowsDpapiNgMasterKeyStoreTest {

    private val directory = Files.createTempDirectory("kalium-dpapi-ng").toFile()
    private val store = WindowsDpapiNgMasterKeyStore()

    @BeforeTest
    fun requireWindows() {
        assumeTrue("Windows only", System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true))
    }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun givenProtectedKey_whenUnprotected_thenTheSameKeyComesBack() {
        val key = ByteArray(32) { (it * 13).toByte() }

        val reference = store.store(key)

        assertFalse(Base64.getDecoder().decode(reference).contentEquals(key))
        assertContentEquals(key, store.load(reference))
    }

    @Test
    fun givenReferenceThatIsNoProtectedKey_whenUnprotected_thenItFails() {
        assertFailsWith<SettingsEncryptionException> { store.load(Base64.getEncoder().encodeToString(ByteArray(64))) }
    }

    @Test
    fun givenDamagedReference_whenUnprotected_thenItFails() {
        assertFailsWith<SettingsEncryptionException> { store.load("not base64 !") }
    }

    @Test
    fun givenDpapiNgKeyStore_whenEncryptedSettingsAreWrittenAndReopened_thenTheyReadBack() {
        buildSettings(SettingOptions.AppSettings(true), param()).putString("secret", "value")

        assertEquals("value", buildSettings(SettingOptions.AppSettings(true), param()).getStringOrNull("secret"))
    }

    @Test
    fun givenWindows_whenThePlatformKeyStoreIsChosen_thenItIsDpapiNg() {
        assertEquals("windows-dpapi-ng", platformMasterKeyStore().name)
    }

    private fun param() = EncryptedSettingsPlatformParam(directory.path, SettingsMasterKeys { store })
}
