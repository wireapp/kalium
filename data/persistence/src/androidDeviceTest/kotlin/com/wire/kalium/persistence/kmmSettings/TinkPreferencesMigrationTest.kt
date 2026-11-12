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

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.integration.android.AndroidKeystore
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TinkPreferencesMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeTest
    fun before() = cleanUp()

    @AfterTest
    fun after() = cleanUp()

    @Test
    fun givenLegacyPreferences_whenMigrating_thenAllValuesAreCopiedAndMigrationIsCompleted() {
        val legacy = legacyPreferences().apply { fillWithSampleValues() }
        val expected = legacy.all

        val migrated = migrateToTink(legacy, TinkPreferencesStore(context, LEGACY_FILE))

        assertNotNull(migrated)
        assertEquals(expected, migrated.all)
        assertTrue(TinkPreferencesStore(context, LEGACY_FILE).isMigrationCompleted())
    }

    @Test
    fun givenAMigration_whenItCompletes_thenTheLegacyFileIsLeftUntouched() {
        val legacy = legacyPreferences().apply { fillWithSampleValues() }
        val rawLegacyBefore = rawPreferences(LEGACY_FILE).all

        migrateToTink(legacy, TinkPreferencesStore(context, LEGACY_FILE))

        assertEquals(rawLegacyBefore, rawPreferences(LEGACY_FILE).all)
    }

    @Test
    fun givenACompletedMigration_whenReopeningTheStore_thenValuesAreReadBackThroughTheKeystore() {
        val legacy = legacyPreferences().apply { fillWithSampleValues() }
        val expected = legacy.all
        migrateToTink(legacy, TinkPreferencesStore(context, LEGACY_FILE))

        val reopened = TinkPreferencesStore(context, LEGACY_FILE).open()

        assertEquals(expected, reopened.all)
        assertEquals("token", reopened.getString("string", null))
        assertEquals(setOf("a", "b", ""), reopened.getStringSet("set", null))
        assertEquals(42, reopened.getInt("int", 0))
        assertEquals(Long.MAX_VALUE, reopened.getLong("long", 0))
        assertEquals(1.5f, reopened.getFloat("float", 0f))
        assertTrue(reopened.getBoolean("boolean", false))
    }

    @Test
    fun givenAFailingLegacyStore_whenMigrating_thenMigrationIsDiscardedAndNotCompleted() {
        val legacy = legacyPreferences().apply { fillWithSampleValues() }
        val failingLegacy = object : SharedPreferences by legacy {
            override fun getAll(): Map<String, *> = throw SecurityException("Simulated legacy failure")
        }
        val store = TinkPreferencesStore(context, LEGACY_FILE)

        val migrated = migrateToTink(failingLegacy, store)

        assertNull(migrated)
        assertFalse(store.isMigrationCompleted())
        assertTrue(rawPreferences(DATA_FILE).all.isEmpty())
        assertTrue(rawPreferences(METADATA_FILE).all.isEmpty())
        assertFalse(AndroidKeystore.hasKey(MASTER_KEY_ALIAS))
    }

    @Test
    fun givenAnUnfinishedMigration_whenMigratingAgain_thenItRestartsFromScratch() {
        val legacy = legacyPreferences().apply { fillWithSampleValues() }
        // Simulate a process killed after copying but before the marker was written.
        TinkPreferencesStore(context, LEGACY_FILE).createForMigration().edit().putString("stale", "value").commit()

        val migrated = migrateToTink(legacy, TinkPreferencesStore(context, LEGACY_FILE))

        assertNotNull(migrated)
        assertEquals(legacy.all, migrated.all)
        assertFalse(migrated.contains("stale"))
    }

    @Test
    fun givenACompletedMigration_whenTryingToResetIt_thenItIsRefused() {
        migrateToTink(legacyPreferences(), TinkPreferencesStore(context, LEGACY_FILE))
        val store = TinkPreferencesStore(context, LEGACY_FILE)

        assertFailsWith<IllegalStateException> { store.createForMigration() }
        store.discardUnfinishedMigration()
        assertTrue(store.isMigrationCompleted())
        assertTrue(AndroidKeystore.hasKey(MASTER_KEY_ALIAS))
    }

    @Test
    fun givenTinkPreferences_whenStored_thenNeitherKeysNorValuesAreInPlaintext() {
        val preferences = TinkPreferencesStore(context, LEGACY_FILE).createForMigration()
        preferences.edit().putString("auth_token_key", "secret_token_value").commit()

        val raw = rawPreferences(DATA_FILE).all
        assertEquals(1, raw.size)
        raw.forEach { (key, value) ->
            assertFalse(key.contains("auth_token_key"))
            assertFalse((value as String).contains("secret_token_value"))
        }
        rawPreferences(METADATA_FILE).all.values.forEach { assertFalse((it as String).contains("secret_token_value")) }
    }

    @Test
    fun givenAnEncryptedValue_whenMovedToAnotherKey_thenReadingItFails() {
        val preferences = TinkPreferencesStore(context, LEGACY_FILE).createForMigration()
        preferences.edit().putString("a", "value_a").putString("b", "value_b").commit()
        val raw = rawPreferences(DATA_FILE)
        val (encryptedKeyA, encryptedKeyB) = raw.all.keys.toList()
        raw.edit().putString(encryptedKeyA, raw.getString(encryptedKeyB, null)).commit()

        assertFailsWith<SecurityException> { preferences.all }
    }

    @Test
    fun givenTinkPreferences_whenPuttingNullOrClearing_thenEntriesAreRemoved() {
        val preferences = TinkPreferencesStore(context, LEGACY_FILE).createForMigration()
        preferences.edit().putString("a", "1").putString("b", "2").commit()

        preferences.edit().putString("a", null).commit()
        assertFalse(preferences.contains("a"))
        assertTrue(preferences.contains("b"))

        preferences.edit().clear().commit()
        assertTrue(preferences.all.isEmpty())
    }

    @Test
    fun givenAValueOfAnotherType_whenReadingWithTheWrongGetter_thenItThrowsClassCastException() {
        val preferences = TinkPreferencesStore(context, LEGACY_FILE).createForMigration()
        preferences.edit().putInt("int", 1).commit()

        assertFailsWith<ClassCastException> { preferences.getString("int", null) }
    }

    @Test
    fun givenLegacyUserSettings_whenBuildingThemWithTinkEnabled_thenTheyAreMigratedAndTheLegacyFileIsKept() {
        val options = SettingOptions.UserSettings(shouldEncryptData = true, userIDEntity = UserIDEntity("tink-test-user", "wire.test"))
        val legacyMasterKeyAlias = "_${options.fileName}_master_key_"
        val userStore = TinkPreferencesStore(context, options.fileName)
        try {
            val legacy = buildSharedPreferences(options, EncryptedSettingsPlatformParam(context)).apply { fillWithSampleValues() }
            val expected = legacy.all
            val rawLegacyBefore = rawPreferences(options.fileName).all

            val preferences = buildMigratingSharedPreferences(options, EncryptedSettingsPlatformParam(context), useTinkEncryptedPreferences = true)

            assertTrue(preferences is TinkEncryptedSharedPreferences)
            assertEquals(expected, preferences.all)
            assertTrue(userStore.isMigrationCompleted())
            assertEquals(rawLegacyBefore, rawPreferences(options.fileName).all)
        } finally {
            listOf(options.fileName, "${options.fileName}_tink", "${options.fileName}_tink_metadata").forEach {
                rawPreferences(it).edit().clear().commit()
                context.deleteSharedPreferences(it)
            }
            AndroidKeystore.deleteKey("_${options.fileName}_tink_master_key_")
            AndroidKeystore.deleteKey(legacyMasterKeyAlias)
        }
    }

    @Test
    fun givenNoLegacyData_whenBuildingWithTinkEnabled_thenATinkStoreIsCreatedWithoutALegacyStoreOrKey() {
        val options = SettingOptions.UserSettings(shouldEncryptData = true, userIDEntity = UserIDEntity("tink-fresh-user", "wire.test"))
        val legacyMasterKeyAlias = "_${options.fileName}_master_key_"
        try {
            val preferences = buildMigratingSharedPreferences(options, EncryptedSettingsPlatformParam(context), useTinkEncryptedPreferences = true)
            preferences.edit().putString("token", "value").commit()

            assertTrue(preferences is TinkEncryptedSharedPreferences)
            assertTrue(TinkPreferencesStore(context, options.fileName).isMigrationCompleted())
            assertTrue(rawPreferences(options.fileName).all.isEmpty())
            assertFalse(AndroidKeystore.hasKey(legacyMasterKeyAlias))
        } finally {
            listOf(options.fileName, "${options.fileName}_tink", "${options.fileName}_tink_metadata").forEach {
                rawPreferences(it).edit().clear().commit()
                context.deleteSharedPreferences(it)
            }
            AndroidKeystore.deleteKey("_${options.fileName}_tink_master_key_")
            AndroidKeystore.deleteKey(legacyMasterKeyAlias)
        }
    }

    @Test
    fun givenEveryValueType_whenEncodedAndDecoded_thenTheValueIsUnchanged() {
        listOf<Any>("", "text ✓", emptySet<String>(), setOf("x", "", "✓"), Int.MIN_VALUE, Long.MIN_VALUE, Float.NaN, -0.5f, true, false)
            .forEach { assertEquals(it, ValueCodec.decode(ValueCodec.encode(it))) }
    }

    private fun SharedPreferences.fillWithSampleValues() {
        edit()
            .putString("string", "token")
            .putStringSet("set", setOf("a", "b", ""))
            .putInt("int", 42)
            .putLong("long", Long.MAX_VALUE)
            .putFloat("float", 1.5f)
            .putBoolean("boolean", true)
            .commit()
    }

    private fun legacyPreferences(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        LEGACY_FILE,
        MasterKey.Builder(context, LEGACY_MASTER_KEY_ALIAS).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun rawPreferences(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun cleanUp() {
        listOf(LEGACY_FILE, DATA_FILE, METADATA_FILE).forEach {
            rawPreferences(it).edit().clear().commit()
            context.deleteSharedPreferences(it)
        }
        AndroidKeystore.deleteKey(MASTER_KEY_ALIAS)
        AndroidKeystore.deleteKey(LEGACY_MASTER_KEY_ALIAS)
    }

    private companion object {
        const val LEGACY_FILE = "tink_migration_test_prefs"
        const val DATA_FILE = "${LEGACY_FILE}_tink"
        const val METADATA_FILE = "${LEGACY_FILE}_tink_metadata"
        const val MASTER_KEY_ALIAS = "_${LEGACY_FILE}_tink_master_key_"
        const val LEGACY_MASTER_KEY_ALIAS = "_tink_migration_test_legacy_master_key_"
    }
}
