package com.wire.kalium.persistence.client

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.kmmSettings.KaliumPreferencesSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class AuthTokenStorageTest {
    private val mockSettings: Settings = MapSettings()

    private val kaliumPreferences = KaliumPreferencesSettings(mockSettings)

    private lateinit var authTokenStorage: AuthTokenStorage

    @BeforeTest
    fun setup() {
        mockSettings.clear()
        authTokenStorage = AuthTokenStorage(kaliumPreferences)
    }

    @Test
    fun givenAuthToken_whenInserting_thenAllDataIsStoredCorrectly() {
        val authToken = TEST_AUTH_TOKENS
        authTokenStorage.addOrReplace(authToken, null)
        authTokenStorage.getToken(authToken.userId).also {
            assertEquals(it, authToken)
        }
    }

    @Test
    fun givenAuthTokenAlreadyStored_whenReplacing_thenAllDataIsStoredCorrectly() {
        val authToken = TEST_AUTH_TOKENS
        val newToken = TEST_AUTH_TOKENS.copy(accessToken = "new_access_token")
        authTokenStorage.addOrReplace(authToken, null)
        authTokenStorage.addOrReplace(newToken, null)

        authTokenStorage.getToken(authToken.userId).also {
            assertEquals(it, newToken)
        }
    }

    @Test
    fun givenNoTokensStoredForUser_whenUpdating_thenThrowError() {
        val expected =
            TEST_AUTH_TOKENS.copy(accessToken = "new_access_token", tokenType = "new_token_type", refreshToken = "new_refresh_token")

        assertFails {
            authTokenStorage.updateToken(expected.userId, "access_token", "token_type", "refresh_token")

        }
    }

    @Test
    fun givenTokensStoredForUser_whenUpdatingWithNullRefreshToken_thenTokenIsUpdatedAndTheOldRefreshTokenIsUsed() {
        val expected =
            TEST_AUTH_TOKENS.copy(accessToken = "new_access_token", tokenType = "new_token_type")

        authTokenStorage.addOrReplace(TEST_AUTH_TOKENS, null)
        authTokenStorage.updateToken(expected.userId, expected.accessToken, expected.tokenType, null)

        authTokenStorage.getToken(expected.userId).also {
            assertEquals(it, expected)
        }
    }

    @Test
    fun givenTokensStoredForUser_whenUpdating_thenThrowError() {
        val expected =
            TEST_AUTH_TOKENS.copy(accessToken = "new_access_token", tokenType = "new_token_type", refreshToken = "new_refresh_token")

        authTokenStorage.addOrReplace(TEST_AUTH_TOKENS, null)
        authTokenStorage.updateToken(expected.userId, expected.accessToken, expected.tokenType, expected.refreshToken)

        authTokenStorage.getToken(expected.userId).also {
            assertEquals(it, expected)
        }
    }

    private companion object {
        val TEST_AUTH_TOKENS =
            AuthTokenEntity(UserIDEntity("user_id", "user_domain"), "access_token", "refresh_token", "token_type", "label")
    }
}
