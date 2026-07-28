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

package com.wire.kalium.logic.feature

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.logic.feature.call.GlobalCallManager

internal interface UserSessionScopeProvider {
    fun get(userId: UserId): UserSessionScope?
    fun getOrCreate(userId: UserId): UserSessionScope
    fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    suspend fun close(userId: UserId)
    suspend fun delete(userId: UserId)
}

internal abstract class UserSessionScopeProviderCommon(
    private val globalCallManager: GlobalCallManager,
    private val userStorageProvider: UserStorageProvider,
    private val removeAuthenticatedNetworkForUser: suspend (UserId) -> Unit,
    protected val userAgent: String
) : UserSessionScopeProvider {

    private val userScopeStorage: ConcurrentMutableMap<UserId, UserSessionScope> by lazy {
        ConcurrentMutableMap()
    }

    override fun getOrCreate(userId: UserId): UserSessionScope =
        userScopeStorage.computeIfAbsent(userId) {
            create(userId)
        }

    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T = getOrCreate(userId).action()

    override fun get(userId: UserId): UserSessionScope? = userScopeStorage.get(userId)

    override suspend fun close(userId: UserId) {
        userScopeStorage.remove(userId)?.closeAndWait()
        globalCallManager.removeInMemoryCallingManagerForUser(userId)
        userStorageProvider.remove(userId)?.database?.close()
        removeAuthenticatedNetworkForUser(userId)
    }

    override suspend fun delete(userId: UserId) = close(userId)

    internal abstract fun create(userId: UserId): UserSessionScope
}

internal expect class UserSessionScopeProviderImpl : UserSessionScopeProvider {
    override fun get(userId: UserId): UserSessionScope?
    override fun getOrCreate(userId: UserId): UserSessionScope
    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    override suspend fun close(userId: UserId)
    override suspend fun delete(userId: UserId)
}
