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

package com.wire.kalium.persistence.globalDB

import com.wire.kalium.persistence.GlobalDBBaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalAuthSessionEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalDbSecretEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalProxyCredentialsEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalPushRegistrationEntity
import com.wire.kalium.persistence.daokaliumdb.GlobalSecretsDAO
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GlobalSecretsDAOTest : GlobalDBBaseTest() {

    private lateinit var globalSecretsDAO: GlobalSecretsDAO

    @BeforeTest
    fun setUp() = runTest {
        deleteDatabase()
        globalSecretsDAO = createDatabase().globalSecretsDAO
    }

    @Test
    fun givenAuthSession_whenUpsertingAndDeleting_thenSessionIsPersistedAndDeleted() = runTest {
        val authSession = GlobalAuthSessionEntity(
            userId = USER_ID,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "Bearer",
            cookieLabel = "cookie-label",
            updatedAt = 1L
        )

        globalSecretsDAO.upsertAuthSession(authSession)
        assertEquals(authSession, globalSecretsDAO.authSession(USER_ID))

        globalSecretsDAO.upsertAuthSession(authSession.copy(accessToken = "new-access-token", updatedAt = 2L))
        assertEquals("new-access-token", globalSecretsDAO.authSession(USER_ID)?.accessToken)

        globalSecretsDAO.deleteAuthSession(USER_ID)
        assertNull(globalSecretsDAO.authSession(USER_ID))
    }

    @Test
    fun givenProxyCredentials_whenUpsertingAndDeleting_thenCredentialsArePersistedAndDeleted() = runTest {
        val proxyCredentials = GlobalProxyCredentialsEntity(
            userId = USER_ID,
            username = "proxy-user",
            password = "proxy-password",
            updatedAt = 1L
        )

        globalSecretsDAO.upsertProxyCredentials(proxyCredentials)
        assertEquals(proxyCredentials, globalSecretsDAO.proxyCredentials(USER_ID))

        globalSecretsDAO.upsertProxyCredentials(proxyCredentials.copy(password = "new-proxy-password", updatedAt = 2L))
        assertEquals("new-proxy-password", globalSecretsDAO.proxyCredentials(USER_ID)?.password)

        globalSecretsDAO.deleteProxyCredentials(USER_ID)
        assertNull(globalSecretsDAO.proxyCredentials(USER_ID))
    }

    @Test
    fun givenPushRegistration_whenUpsertingAndDeleting_thenRegistrationIsPersistedAndDeleted() = runTest {
        val pushRegistration = GlobalPushRegistrationEntity(
            token = "push-token",
            transport = "GCM",
            applicationId = "application-id",
            updatedAt = 1L
        )

        globalSecretsDAO.upsertPushRegistration(pushRegistration)
        assertEquals(pushRegistration, globalSecretsDAO.pushRegistration())

        globalSecretsDAO.upsertPushRegistration(pushRegistration.copy(token = "new-push-token", updatedAt = 2L))
        assertEquals("new-push-token", globalSecretsDAO.pushRegistration()?.token)

        globalSecretsDAO.deletePushRegistration()
        assertNull(globalSecretsDAO.pushRegistration())
    }

    @Test
    fun givenDbSecret_whenUpsertingExistingAlias_thenSecretIsReplacedAndCreatedAtIsPreserved() = runTest {
        val firstSecret = GlobalDbSecretEntity(
            alias = SECRET_ALIAS,
            secret = byteArrayOf(1, 2, 3),
            version = 1,
            createdAt = 1L,
            updatedAt = 1L
        )
        val secondSecret = firstSecret.copy(
            secret = byteArrayOf(4, 5, 6),
            version = 2,
            createdAt = 3L,
            updatedAt = 4L
        )

        globalSecretsDAO.upsertDbSecret(firstSecret)
        globalSecretsDAO.upsertDbSecret(secondSecret)

        val persistedSecret = globalSecretsDAO.dbSecret(SECRET_ALIAS)
        assertEquals(1L, persistedSecret?.createdAt)
        assertEquals(4L, persistedSecret?.updatedAt)
        assertEquals(2, persistedSecret?.version)
        assertContentEquals(byteArrayOf(4, 5, 6), persistedSecret?.secret)
    }

    @Test
    fun givenDbSecret_whenDeleting_thenSecretIsDeleted() = runTest {
        globalSecretsDAO.upsertDbSecret(
            GlobalDbSecretEntity(
                alias = SECRET_ALIAS,
                secret = byteArrayOf(1, 2, 3),
                version = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        globalSecretsDAO.deleteDbSecret(SECRET_ALIAS)

        assertNull(globalSecretsDAO.dbSecret(SECRET_ALIAS))
    }

    @Test
    fun givenEmptyDbSecret_whenUpserting_thenThrows() = runTest {
        assertFailsWith<IllegalArgumentException> {
            globalSecretsDAO.upsertDbSecret(
                GlobalDbSecretEntity(
                    alias = SECRET_ALIAS,
                    secret = ByteArray(0),
                    version = 1,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
    }

    @Test
    fun givenAllGlobalSecrets_whenLoadingStartupSecrets_thenReturnsSingleSnapshot() = runTest {
        val authSession = GlobalAuthSessionEntity(
            userId = USER_ID,
            accessToken = "access-token",
            refreshToken = "refresh-token",
            tokenType = "Bearer",
            cookieLabel = "cookie-label",
            updatedAt = 1L
        )
        val proxyCredentials = GlobalProxyCredentialsEntity(
            userId = USER_ID,
            username = "proxy-user",
            password = "proxy-password",
            updatedAt = 2L
        )
        val pushRegistration = GlobalPushRegistrationEntity(
            token = "push-token",
            transport = "GCM",
            applicationId = "application-id",
            updatedAt = 3L
        )
        val dbSecret = GlobalDbSecretEntity(
            alias = SECRET_ALIAS,
            secret = byteArrayOf(1, 2, 3),
            version = 1,
            createdAt = 4L,
            updatedAt = 5L
        )

        globalSecretsDAO.upsertAuthSession(authSession)
        globalSecretsDAO.upsertProxyCredentials(proxyCredentials)
        globalSecretsDAO.upsertPushRegistration(pushRegistration)
        globalSecretsDAO.upsertDbSecret(dbSecret)

        val snapshot = globalSecretsDAO.startupSecrets()

        assertEquals(authSession, snapshot.authSessions[USER_ID])
        assertEquals(proxyCredentials, snapshot.proxyCredentials[USER_ID])
        assertEquals(pushRegistration, snapshot.pushRegistration)
        assertEquals(dbSecret.copy(secret = byteArrayOf()), snapshot.dbSecrets[SECRET_ALIAS]?.copy(secret = byteArrayOf()))
        assertContentEquals(dbSecret.secret, snapshot.dbSecrets[SECRET_ALIAS]?.secret)
    }

    private companion object {
        val USER_ID = UserIDEntity("user-id", "domain.example")
        const val SECRET_ALIAS = "user_db_secret_alias_user-id"
    }
}
