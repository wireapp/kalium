/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.network

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.util.AccountResourceAccess

internal fun SessionManager.guardedBy(access: AccountResourceAccess?): SessionManager {
    if (access == null) return this
    val manager = this
    return object : SessionManager by manager {
        override suspend fun session() = access.withAccess { manager.session() }
        override suspend fun updateToken(accessTokenApi: AccessTokenApi, oldRefreshToken: String?) = access.withAccess {
            val stored = manager.session()
            if (stored != null && stored.refreshToken != oldRefreshToken) stored
            else manager.updateToken(accessTokenApi, oldRefreshToken)
        }
    }
}

internal fun AccessTokenRefresher.guardedBy(
    access: AccountResourceAccess?,
    currentToken: suspend () -> String?,
): AccessTokenRefresher {
    if (access == null) return this
    val refresher = this
    return object : AccessTokenRefresher {
        override suspend fun refreshTokenAndPersistSession(currentRefreshToken: String, clientId: ClientId?) = access.withAccess {
            refresher.refreshTokenAndPersistSession(checkNotNull(currentToken()) { "Refresh token is missing" }, clientId)
        }
    }
}
