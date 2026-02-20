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

package com.wire.kalium.usernetwork.di

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer

public data class UserAuthenticatedNetworkApis(public val container: AuthenticatedNetworkContainer)

/**
 * Provides in-memory caching for [UserAuthenticatedNetworkApis] keyed by [UserId].
 *
 * Cache scope is controlled by the shared provider policy [USE_GLOBAL_PROVIDER_CACHE]:
 * - `true`: all [UserAuthenticatedNetworkProvider] instances share one cache map.
 * - `false`: each [UserAuthenticatedNetworkProvider] instance owns a private cache map.
 */
public abstract class UserAuthenticatedNetworkProvider {
    private companion object {
        val globalInMemoryUserAuthenticatedNetworkApis: ConcurrentMutableMap<UserId, UserAuthenticatedNetworkApis> = ConcurrentMutableMap()
    }

    private val inMemoryUserAuthenticatedNetworkApis: ConcurrentMutableMap<UserId, UserAuthenticatedNetworkApis> =
        if (USE_GLOBAL_PROVIDER_CACHE) globalInMemoryUserAuthenticatedNetworkApis else ConcurrentMutableMap()

    public fun get(userId: UserId): UserAuthenticatedNetworkApis? = inMemoryUserAuthenticatedNetworkApis[userId]

    public fun getOrCreate(
        userId: UserId,
        creator: () -> UserAuthenticatedNetworkApis
    ): UserAuthenticatedNetworkApis = inMemoryUserAuthenticatedNetworkApis.computeIfAbsent(userId) {
        creator()
    }

    public fun remove(userId: UserId): UserAuthenticatedNetworkApis? = inMemoryUserAuthenticatedNetworkApis.remove(userId)
}

public class PlatformUserAuthenticatedNetworkProvider : UserAuthenticatedNetworkProvider()
