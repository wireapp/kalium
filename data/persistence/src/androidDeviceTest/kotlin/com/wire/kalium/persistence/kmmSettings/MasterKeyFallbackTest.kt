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

import android.content.Context
import android.security.keystore.StrongBoxUnavailableException
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.wire.kalium.persistence.dao.UserIDEntity
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SdkSuppress(minSdkVersion = 28)
class MasterKeyFallbackTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val options = SettingOptions.UserSettings(true, UserIDEntity("fallback-${UUID.randomUUID()}", "test"))
    private val alias = "_${options.fileName}_master_key_"
    private val param = EncryptedSettingsPlatformParam(context)

    private fun regularKey(): MasterKey = MasterKey.Builder(context, alias)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .setRequestStrongBoxBacked(false)
        .build()

    @AfterTest
    fun cleanup() {
        context.deleteSharedPreferences(options.fileName)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test
    fun strongBoxSuccessDoesNotFallBack() {
        val requests = mutableListOf<Boolean>()
        val expected = regularKey()
        val actual = getOrCreateMasterKey(context, alias, strongBoxAvailable = true) {
            requests += it
            expected
        }
        assertSame(expected, actual)
        assertEquals(listOf(true), requests)
    }

    @Test
    fun unavailableFeatureUsesRegularKeystoreDirectly() {
        val requests = mutableListOf<Boolean>()
        getOrCreateMasterKey(context, alias, strongBoxAvailable = false) {
            requests += it
            regularKey()
        }
        assertEquals(listOf(false), requests)
    }

    @Test
    fun supportedFailuresFallBackToRealKeystore() {
        val failures = listOf(
            StrongBoxUnavailableException("test"),
            GeneralSecurityException("test", StrongBoxUnavailableException("test")),
            GeneralSecurityException("test"),
            ProviderException("test"),
            IOException("test")
        )
        failures.forEach { failure ->
            val requests = mutableListOf<Boolean>()
            getOrCreateMasterKey(context, alias, strongBoxAvailable = true) { strongBox ->
                requests += strongBox
                if (strongBox) throw failure
                regularKey()
            }
            assertEquals(listOf(true, false), requests)
        }
    }

    @Test
    fun fallbackFailurePreservesBothCauses() {
        val original = GeneralSecurityException("StrongBox failure")
        val fallback = IOException("fallback failure")
        val requests = mutableListOf<Boolean>()
        val actual = assertFailsWith<MasterKeyInitializationException> {
            getOrCreateMasterKey(context, alias, strongBoxAvailable = true) {
                requests += it
                if (it) throw original else throw fallback
            }
        }
        assertSame(fallback, actual.cause)
        assertEquals(listOf(original), actual.cause!!.suppressed.toList())
        assertEquals(listOf(true, false), requests)
    }

    @Test
    fun programmingErrorDoesNotFallBack() {
        val requests = mutableListOf<Boolean>()
        assertFailsWith<IllegalArgumentException> {
            getOrCreateMasterKey(context, alias, strongBoxAvailable = true) {
                requests += it
                throw IllegalArgumentException("invalid configuration")
            }
        }
        assertEquals(listOf(true), requests)
    }

    @Test
    fun regularKeystoreFailureIsNotRetried() {
        val requests = mutableListOf<Boolean>()
        assertFailsWith<MasterKeyInitializationException> {
            getOrCreateMasterKey(context, alias, strongBoxAvailable = false) {
                requests += it
                throw GeneralSecurityException("test")
            }
        }
        assertEquals(listOf(false), requests)
    }

    @Test
    fun terminalFailuresHaveTypedWrapperAndOriginalCause() {
        listOf(GeneralSecurityException("test"), ProviderException("test"), IOException("test")).forEach { failure ->
            val actual = assertFailsWith<MasterKeyInitializationException> {
                getOrCreateMasterKey(context, alias, strongBoxAvailable = false) { throw failure }
            }
            assertSame(failure, actual.cause)
        }
    }

    @Test
    fun fallbackPreferencesRemainReadableWhenReopened() {
        val requests = mutableListOf<Boolean>()
        val preferences = buildSharedPreferences(options, param, masterKeyBuilder = { ctx, keyAlias ->
            getOrCreateMasterKey(ctx, keyAlias, strongBoxAvailable = true) { strongBox ->
                requests += strongBox
                if (strongBox) throw GeneralSecurityException("test", StrongBoxUnavailableException("test"))
                regularKey()
            }
        })
        assertTrue(preferences.edit().putString("test-key", "test-value").commit())
        assertEquals(listOf(true, false), requests)
        val reopened = buildSharedPreferences(options, param)
        assertEquals("test-value", reopened.getString("test-key", null))
    }

    @Test
    fun preferencesRetryReusesMasterKey() {
        var keyBuilds = 0
        var preferenceBuilds = 0
        val expectedKey = regularKey()
        val expectedPreferences = context.getSharedPreferences(options.fileName, Context.MODE_PRIVATE)
        val actual = buildSharedPreferences(options, param, masterKeyBuilder = { _, _ ->
            keyBuilds++
            expectedKey
        }, preferencesBuilder = { _, _, key ->
            assertSame(expectedKey, key)
            preferenceBuilds++
            if (preferenceBuilds == 1) throw IOException("temporary preference failure")
            expectedPreferences
        })
        assertSame(expectedPreferences, actual)
        assertEquals(1, keyBuilds)
        assertEquals(2, preferenceBuilds)
    }

    @Test
    fun permanentPreferenceFailureStopsAfterOneRetry() {
        var keyBuilds = 0
        var preferenceBuilds = 0
        val failure = IOException("permanent preference failure")
        val actual = assertFailsWith<IOException> {
            buildSharedPreferences(options, param, masterKeyBuilder = { _, _ ->
                keyBuilds++
                regularKey()
            }, preferencesBuilder = { _, _, _ ->
                preferenceBuilds++
                throw failure
            })
        }
        assertSame(failure, actual)
        assertEquals(1, keyBuilds)
        assertEquals(2, preferenceBuilds)
    }
}
