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

@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.session.FailureToRefreshTokenException
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.persistence.client.AuthTokenStorage
import com.wire.kalium.usernetwork.ExistingAccountSessionManager
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration

/**
 * Authenticated networking for a prepared account this process does not hold, for work that touches neither the account's
 * database nor CoreCrypto, such as uploading a shared file. Each request reads the stored token, so it follows a refresh
 * by the account's owner. Refreshing replaces the stored token, which the app and the extensions do only while they hold
 * the account, so a refresh here holds it for that moment.
 */
@InternalKaliumApi
public class UnheldAccountSession internal constructor(
    private val rootPath: String,
    private val prepared: PreparedAccount,
    tokens: AuthTokenStorage,
    /** How long a refresh waits while another process holds the account. */
    private val refreshWait: Duration,
) : SessionManager {
    private val stored = ExistingAccountSessionManager(prepared.userId, ClientId(prepared.clientId), prepared.serverConfig, tokens)
    private val refreshMutex = Mutex()
    private val handedOut = AtomicReference<String?>(null)
    private val blocked = AtomicBoolean(false)

    /** A refresh failed because another process kept the account: the work may be handed to the app. */
    public val refreshBlocked: Boolean get() = blocked.load()

    override suspend fun session(): SessionDTO = stored.session().also { handedOut.store(it.accessToken) }
    override fun serverConfig(): ServerConfigDTO = stored.serverConfig()
    override fun proxyCredentials(): ProxyCredentialsDTO? = stored.proxyCredentials()

    override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO = refreshMutex.withLock {
        val session = refreshedElsewhere() ?: refreshHoldingAccount(accessTokenApi, oldRefreshToken)
        session.also { handedOut.store(it.accessToken) }
    }

    private suspend fun refreshHoldingAccount(accessTokenApi: AccessTokenApi, oldRefreshToken: String?): SessionDTO {
        val lease = when (val acquired = AppleAccountRuntimeOwner(rootPath).tryAcquire(prepared.userId, refreshWait)) {
            is Either.Left -> return refreshedElsewhere() ?: run {
                if (acquired.value == AccountRuntimeFailure.BUSY) blocked.store(true)
                throw FailureToRefreshTokenException("Account ${acquired.value.name} while refreshing")
            }
            is Either.Right -> acquired.value
        }
        return try {
            // Logging out, or registering another client, ends the session this one was prepared from.
            if (ApplePreparedAccountStore(rootPath, prepared.userId).read(lease)?.clientId != prepared.clientId) {
                throw FailureToRefreshTokenException("Prepared account changed")
            }
            refreshedElsewhere() ?: stored.updateToken(accessTokenApi, oldRefreshToken)
        } finally {
            lease.release()
        }
    }

    /** The stored session, when another process refreshed it since this one last handed out a token. */
    private suspend fun refreshedElsewhere(): SessionDTO? = stored.session().takeIf { it.accessToken != handedOut.load() }
}
