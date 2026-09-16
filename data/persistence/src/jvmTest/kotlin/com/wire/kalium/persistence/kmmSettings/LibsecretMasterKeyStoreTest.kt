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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Needs Linux with a running, unlocked Secret Service, so it only runs when
 * `KALIUM_TEST_SECRET_SERVICE=true` is set, for example in a container with gnome-keyring.
 * Keys go into the in-memory `session` collection, never the persistent login keyring.
 */
class LibsecretMasterKeyStoreTest {

    private val directory = Files.createTempDirectory("kalium-libsecret").toFile()
    private val store = LibsecretMasterKeyStore(collection = "session")

    @BeforeTest
    fun requireSecretService() {
        val isLinux = System.getProperty("os.name").orEmpty().startsWith("Linux", ignoreCase = true)
        assumeTrue("Linux with a Secret Service only", isLinux && System.getenv("KALIUM_TEST_SECRET_SERVICE") == "true")
    }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun givenStoredKey_whenLoadedByItsReference_thenTheSameKeyComesBack() {
        val key = ByteArray(32) { (it * 11).toByte() }

        val reference = store.store(key)

        assertContentEquals(key, store.load(reference))
    }

    @Test
    fun givenUnknownReference_whenLoaded_thenItFails() {
        assertFailsWith<SettingsEncryptionException> { store.load("no-such-entry") }
    }

    @Test
    fun givenSecretServiceKeyStore_whenEncryptedSettingsAreWrittenAndReopened_thenTheyReadBack() {
        buildSettings(SettingOptions.AppSettings(true), param()).putString("secret", "value")

        assertEquals("value", buildSettings(SettingOptions.AppSettings(true), param()).getStringOrNull("secret"))
    }

    @Test
    fun givenLinux_whenThePlatformKeyStoreIsChosen_thenItIsTheSecretService() {
        assertEquals("linux-secret-service", platformMasterKeyStore().name)
    }

    private fun param() = EncryptedSettingsPlatformParam(directory.path, SettingsMasterKeys { store })
}
