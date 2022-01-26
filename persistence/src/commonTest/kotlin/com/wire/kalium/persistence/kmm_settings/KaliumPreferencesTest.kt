package com.wire.kalium.persistence.kmm_settings

import com.russhwolf.settings.MockSettings
import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaliumPreferencesTest {
    private val mockSettings: Settings = MockSettings()

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
        assertTrue(kaliumPreferences.exitsValue(KEY1))
        assertFalse(kaliumPreferences.exitsValue(KEY2))
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
        assertEquals(kaliumPreferences.getSerializable(KEY1, TestValueClass.serializer())?.s, TEST_LIST)
    }


    private companion object {
        const val KEY1 = "key_1"
        const val KEY2 = "key_2"
    }
}

@Serializable
@JvmInline
private value class TestValueClass(val s: List<String>)
