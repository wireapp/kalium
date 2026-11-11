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

@file:OptIn(com.wire.kalium.util.InternalKaliumApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.AccessTokenDTO
import com.wire.kalium.network.api.model.RefreshTokenDTO
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.client.AuthTokenEntity
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.persistence.client.ProxyCredentialsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration

class UnheldAccountSessionTest {
    private val userId = UserId("self", "wire.test")
    private val root = "${NSTemporaryDirectory()}kalium-unheld-${NSUUID.UUID().UUIDString}"

    @Test
    fun refreshesOnlyWhileHoldingTheAccountAndTakesATokenItsOwnerRefreshed() = runTest {
        val tokens = Tokens()
        val api = RefreshApi {
            when (val attempt = AppleAccountRuntimeOwner(root).tryAcquire(userId)) {
                is Either.Left -> true
                is Either.Right -> false.also { attempt.value.release() }
            }
        }
        try {
            publish(prepared())
            val session = UnheldAccountSession(root, prepared(), tokens, Duration.ZERO)
            assertEquals("old-access", session.session().accessToken)

            val held = lease()
            assertFailsWith<FailureToRefreshTokenException> { session.updateToken(api, "old-refresh") }
            assertEquals(true, session.refreshBlocked)
            tokens.token = tokens.token?.copy(accessToken = "owner-access")
            assertEquals("owner-access", session.updateToken(api, "old-refresh").accessToken)
            held.release()
            assertEquals(emptyList<Boolean>(), api.whileHeld, "Nothing is refreshed while another process holds the account")

            assertEquals("new-access", session.updateToken(api, "old-refresh").accessToken)
            assertEquals(listOf(true), api.whileHeld)
            assertEquals("new-access", tokens.token?.accessToken)

            // Another client registered since: refreshing would mix its account with this session's.
            publish(prepared().copy(clientId = "new-client"))
            assertFailsWith<FailureToRefreshTokenException> { session.updateToken(api, "old-refresh") }
            assertEquals(listOf(true), api.whileHeld)
            lease().release()
        } finally {
            FileSystem.SYSTEM.deleteRecursively(root.toPath(), mustExist = false)
        }
    }

    private suspend fun lease() = assertIs<Either.Right<AccountRuntimeLease>>(AppleAccountRuntimeOwner(root).tryAcquire(userId)).value

    private suspend fun publish(prepared: PreparedAccount) {
        FileSystem.SYSTEM.createDirectories(appleAccountPath(root, userId).toPath())
        val lease = lease()
        try {
            ApplePreparedAccountStore(root, userId).publish(lease, prepared)
        } finally {
            lease.release()
        }
    }

    private fun prepared() = PreparedAccount(
        userId, "abc123",
        ServerConfig("test", ServerConfig.DUMMY, ServerConfig.MetaData(false, CommonApiVersionType.Valid(8), userId.domain)),
        null,
    )

    /** Records, for each refresh request, whether the account was held meanwhile. */
    private class RefreshApi(private val accountHeld: suspend () -> Boolean) : AccessTokenApi {
        val whileHeld = mutableListOf<Boolean>()
        override suspend fun getToken(refreshToken: String, clientId: String?): NetworkResponse<Pair<AccessTokenDTO, RefreshTokenDTO?>> {
            whileHeld += accountHeld()
            return NetworkResponse.Success(AccessTokenDTO("self", "new-access", 3600, "Bearer") to null, emptyMap(), 200)
        }
    }

    private class Tokens : AuthTokenStorage {
        var token: AuthTokenEntity? = AuthTokenEntity(UserIDEntity("self", "wire.test"), "old-access", "old-refresh", "Bearer", "login-cookie")
        override fun getToken(userId: UserIDEntity): AuthTokenEntity? = token
        override fun proxyCredentials(userId: UserIDEntity): ProxyCredentialsEntity? = null
        override fun updateToken(userId: UserIDEntity, accessToken: String, tokenType: String, refreshToken: String?): AuthTokenEntity =
            checkNotNull(token).copy(accessToken = accessToken, tokenType = tokenType, refreshToken = refreshToken ?: token!!.refreshToken)
                .also { token = it }
        override fun addOrReplace(authTokenEntity: AuthTokenEntity, proxyCredentialsEntity: ProxyCredentialsEntity?) = error("Not used")
        override fun deleteToken(userId: UserIDEntity) = error("Not used")
    }
}
