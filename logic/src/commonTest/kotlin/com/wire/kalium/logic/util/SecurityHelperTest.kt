package com.wire.kalium.logic.util

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import kotlin.test.BeforeTest
import com.russhwolf.settings.MockSettings
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.kmm_settings.KaliumPreferencesSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityHelperTest {
    private lateinit var kaliumPreferences: KaliumPreferences

    @BeforeTest
    fun setup() {
        kaliumPreferences = KaliumPreferencesSettings(MockSettings())
    }

    @Test
    fun whenCallingGlobalDBSecretTwice_thenTheSameValueIsReturned() {
        val securityHelper = SecurityHelper(kaliumPreferences)
        val secret1 = securityHelper.globalDBSecret()
        val secret2 = securityHelper.globalDBSecret()
        assertTrue(secret1.value.contentEquals(secret2.value))
    }

    @Test
    fun whenCallingUserDBSecretTwice_thenTheSameValueIsReturned() {
        val securityHelper = SecurityHelper(kaliumPreferences)
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val secret1 = securityHelper.userDBSecret(userId)
        val secret2 = securityHelper.userDBSecret(userId)
        assertTrue(secret1.value.contentEquals(secret2.value))
    }

    @Test
    fun whenCallingUserDBSecretFor2DifferentUsers_thenEachHasADifferentSecret() {
        val securityHelper = SecurityHelper(kaliumPreferences)
        val user1 = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val user2 = UserId("dcc8387c-0997-469e-bba5-c6a9e9f5cae3", "wire.com")
        val secret1 = securityHelper.userDBSecret(user1)
        val secret2 = securityHelper.userDBSecret(user2)
        assertFalse(secret1.value.contentEquals(secret2.value))
    }

    @Test
    fun whenCallingMlsDBSecretTwiceForTheSameUser_thenTheSameValueIsReturned() {
        val securityHelper = SecurityHelper(kaliumPreferences)
        val userId = UserId("df8703fb-bbab-4b10-a369-0ef781a17cf5", "wire.com")
        val secret1 = securityHelper.mlsDBSecret(userId)
        val secret2 = securityHelper.mlsDBSecret(userId)
        assertEquals(secret1.value, secret2.value)
    }
}
