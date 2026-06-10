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

package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.DatabaseBackedGlobalSecretsCache
import com.wire.kalium.persistence.daokaliumdb.GlobalAuthSessionEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalDbSecretEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalProxyCredentialsEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalPushRegistrationEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalSecretsSnapshot
import com.wire.kalium.persistence.daokaliumdb.GlobalSecretsDAO
import com.wire.kalium.persistence.dbPassphrase.DatabaseBackedPassphraseStorage
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DatabaseBackedGlobalStorageTest {

    private lateinit var authTokenStorage: AuthTokenStorage
    private lateinit var tokenStorage: TokenStorage
    private lateinit var passphraseStorage: PassphraseStorage
    private lateinit var globalSecretsDAO: FakeGlobalSecretsDAO

    @BeforeTest
    fun setUp() {
        globalSecretsDAO = FakeGlobalSecretsDAO()
        val globalSecretsCache = DatabaseBackedGlobalSecretsCache(globalSecretsDAO)
        authTokenStorage = DatabaseBackedAuthTokenStorage(globalSecretsCache)
        tokenStorage = DatabaseBackedTokenStorage(globalSecretsCache)
        passphraseStorage = DatabaseBackedPassphraseStorage(globalSecretsCache)
    }

    @Test
    fun givenAuthToken_whenStoringInDatabaseBackedStorage_thenTokenAndProxyCredentialsAreReadable() {
        authTokenStorage.addOrReplace(TEST_AUTH_TOKEN, TEST_PROXY_CREDENTIALS)

        assertEquals(TEST_AUTH_TOKEN, authTokenStorage.getToken(TEST_USER_ID))
        assertEquals(TEST_PROXY_CREDENTIALS, authTokenStorage.proxyCredentials(TEST_USER_ID))
    }

    @Test
    fun givenAuthToken_whenUpdatingInDatabaseBackedStorage_thenRefreshTokenIsPreservedWhenNull() {
        val expectedToken = TEST_AUTH_TOKEN.copy(accessToken = "new-access-token", tokenType = "New")

        authTokenStorage.addOrReplace(TEST_AUTH_TOKEN, null)
        authTokenStorage.updateToken(TEST_USER_ID, expectedToken.accessToken, expectedToken.tokenType, null)

        assertEquals(expectedToken, authTokenStorage.getToken(TEST_USER_ID))
    }

    @Test
    fun givenAuthToken_whenDeletingFromDatabaseBackedStorage_thenTokenAndProxyCredentialsAreDeleted() {
        authTokenStorage.addOrReplace(TEST_AUTH_TOKEN, TEST_PROXY_CREDENTIALS)

        authTokenStorage.deleteToken(TEST_USER_ID)

        assertNull(authTokenStorage.getToken(TEST_USER_ID))
        assertNull(authTokenStorage.proxyCredentials(TEST_USER_ID))
    }

    @Test
    fun givenPushToken_whenStoringInDatabaseBackedStorage_thenTokenIsReadable() {
        val expectedToken = NotificationTokenEntity("push-token", "GCM", "application-id")

        tokenStorage.saveToken(expectedToken.token, expectedToken.transport, expectedToken.applicationId)

        assertEquals(expectedToken, tokenStorage.getToken())
    }

    @Test
    fun givenPassphrase_whenStoringInDatabaseBackedStorage_thenPassphraseIsReadableAndClearable() {
        passphraseStorage.setPassphrase(TEST_PASSPHRASE_ALIAS, TEST_PASSPHRASE)

        assertEquals(TEST_PASSPHRASE, passphraseStorage.getPassphrase(TEST_PASSPHRASE_ALIAS))

        passphraseStorage.clearPassphrase(TEST_PASSPHRASE_ALIAS)
        assertNull(passphraseStorage.getPassphrase(TEST_PASSPHRASE_ALIAS))
    }

    @Test
    fun givenEmptyPassphrase_whenStoringInDatabaseBackedStorage_thenThrows() {
        assertFailsWith<IllegalArgumentException> {
            passphraseStorage.setPassphrase(TEST_PASSPHRASE_ALIAS, "")
        }
    }

    @Test
    fun givenSharedDatabaseBackedStorages_whenReadingStartupSecrets_thenLoadsSnapshotOnce() {
        val expectedToken = NotificationTokenEntity("push-token", "GCM", "application-id")

        authTokenStorage.addOrReplace(TEST_AUTH_TOKEN, TEST_PROXY_CREDENTIALS)
        tokenStorage.saveToken(expectedToken.token, expectedToken.transport, expectedToken.applicationId)
        passphraseStorage.setPassphrase(TEST_PASSPHRASE_ALIAS, TEST_PASSPHRASE)

        assertEquals(TEST_AUTH_TOKEN, authTokenStorage.getToken(TEST_USER_ID))
        assertEquals(TEST_PROXY_CREDENTIALS, authTokenStorage.proxyCredentials(TEST_USER_ID))
        assertEquals(expectedToken, tokenStorage.getToken())
        assertEquals(TEST_PASSPHRASE, passphraseStorage.getPassphrase(TEST_PASSPHRASE_ALIAS))
        assertEquals(1, globalSecretsDAO.startupSecretsCallCount)
    }

    private class FakeGlobalSecretsDAO : GlobalSecretsDAO {
        var startupSecretsCallCount = 0
            private set

        private val authSessions = mutableMapOf<UserIDEntity, GlobalAuthSessionEntity>()
        private val proxyCredentials = mutableMapOf<UserIDEntity, GlobalProxyCredentialsEntity>()
        private var pushRegistration: GlobalPushRegistrationEntity? = null
        private val dbSecrets = mutableMapOf<String, GlobalDbSecretEntity>()

        override suspend fun startupSecrets(): GlobalSecretsSnapshot {
            startupSecretsCallCount++
            return GlobalSecretsSnapshot(
                authSessions = authSessions.toMap(),
                proxyCredentials = proxyCredentials.toMap(),
                pushRegistration = pushRegistration,
                dbSecrets = dbSecrets.toMap()
            )
        }

        override suspend fun upsertAuthSession(authSession: GlobalAuthSessionEntity) {
            authSessions[authSession.userId] = authSession
        }

        override suspend fun authSession(userId: UserIDEntity): GlobalAuthSessionEntity? = authSessions[userId]

        override suspend fun deleteAuthSession(userId: UserIDEntity) {
            authSessions.remove(userId)
        }

        override suspend fun upsertProxyCredentials(proxyCredentials: GlobalProxyCredentialsEntity) {
            this.proxyCredentials[proxyCredentials.userId] = proxyCredentials
        }

        override suspend fun proxyCredentials(userId: UserIDEntity): GlobalProxyCredentialsEntity? = proxyCredentials[userId]

        override suspend fun deleteProxyCredentials(userId: UserIDEntity) {
            proxyCredentials.remove(userId)
        }

        override suspend fun upsertPushRegistration(pushRegistration: GlobalPushRegistrationEntity) {
            this.pushRegistration = pushRegistration
        }

        override suspend fun pushRegistration(): GlobalPushRegistrationEntity? = pushRegistration

        override suspend fun deletePushRegistration() {
            pushRegistration = null
        }

        override suspend fun upsertDbSecret(dbSecret: GlobalDbSecretEntity) {
            dbSecrets[dbSecret.alias] = dbSecret
        }

        override suspend fun dbSecret(alias: String): GlobalDbSecretEntity? = dbSecrets[alias]

        override suspend fun deleteDbSecret(alias: String) {
            dbSecrets.remove(alias)
        }
    }

    private companion object {
        val TEST_USER_ID = UserIDEntity("user-id", "domain.example")
        val TEST_AUTH_TOKEN = AuthTokenEntity(
            userId = TEST_USER_ID,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "Bearer",
            cookieLabel = "cookie-label"
        )
        val TEST_PROXY_CREDENTIALS = ProxyCredentialsEntity(
            username = "proxy-user",
            password = "proxy-password"
        )
        const val TEST_PASSPHRASE_ALIAS = "user_db_secret_alias_user-id"
        const val TEST_PASSPHRASE = "base64-passphrase"
    }
}
