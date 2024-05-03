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

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaliumPreferencesTest {
    private val mockSettings: Settings = MapSettings()

    private val kaliumPreferences = KaliumPreferencesSettings(mockSettings)

    @BeforeTest
    fun clearSettings() {
        mockSettings.clear()
    }

    @Test
    fun givenAString_WhenCallingPutString_ThenItCanBeRetriedCorrectly() {
        val testString = "some cool text"
        kaliumPreferences.putString(KEY1, testString)
        assertEquals(kaliumPreferences.getString(KEY1), testString)
        assertTrue(kaliumPreferences.hasValue(KEY1))
        assertFalse(kaliumPreferences.hasValue(KEY2))
    }


    @Test
    fun givenAString_WhenRemovingAStoredValue_ThenItCanBeRetriedCorrectly() {
        val testString = "some cool text"
        kaliumPreferences.putString(KEY1, testString)
        assertEquals(kaliumPreferences.getString(KEY1), testString)
        kaliumPreferences.remove(KEY1)
        assertNull(kaliumPreferences.getString(KEY1))
    }

    @Test
    fun givenAComplexObject_WhenCallingPutSerializable_ThenItCanBeRetriedCorrectly() {
        val TEST_LIST = listOf("test_text", "another_test_text", "3_is_enough")
        val testValue = TestValueClass(listOf("test_text", "another_test_text", "3_is_enough"))

        kaliumPreferences.putSerializable(KEY1, testValue, TestValueClass.serializer())

        assertEquals(kaliumPreferences.getSerializable(KEY1, TestValueClass.serializer()), testValue)
        assertEquals(kaliumPreferences.getSerializable(KEY1, TestValueClass.serializer())?.values, TEST_LIST)
    }


    private companion object {
        const val KEY1 = "key_1"
        const val KEY2 = "key_2"
    }
}

@Serializable
@JvmInline
private value class TestValueClass(
    @SerialName("s") val values: List<String>
)
