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
import com.wire.kalium.logic.PrepareUserSessionResult
import com.wire.kalium.logic.UserSessionPreparationState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.GlobalCallManager
import com.wire.kalium.logic.toUserSessionPreparationFailure
import com.wire.kalium.userstorage.di.PlatformUserStorageProperties
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.userstorage.di.UserStoragePreparationResult
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.userstorage.di.UserStorageState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface UserSessionScopeProvider {
    fun get(userId: UserId): UserSessionScope?
    fun getOrCreate(userId: UserId): UserSessionScope
    fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    suspend fun prepare(userId: UserId): PrepareUserSessionResult
    fun observePreparation(userId: UserId): Flow<UserSessionPreparationState>
    suspend fun delete(userId: UserId)
}

internal data class UserStorageSessionParameters(
    val platformProperties: PlatformUserStorageProperties,
    val shouldEncryptData: Boolean,
    val dbInvalidationControlEnabled: Boolean,
)

internal data class PreparedUserStorage(
    val storage: UserStorage,
    val parameters: UserStorageSessionParameters,
)

internal abstract class UserSessionScopeProviderCommon(
    private val globalCallManager: GlobalCallManager,
    private val userStorageProvider: UserStorageProvider,
    private val removeAuthenticatedNetworkForUser: suspend (UserId) -> Unit,
    protected val userAgent: String,
) : UserSessionScopeProvider {

    private val userScopeStorage: ConcurrentMutableMap<UserId, UserSessionScope> by lazy {
        ConcurrentMutableMap()
    }

    override fun getOrCreate(userId: UserId): UserSessionScope {
        return userScopeStorage.computeIfAbsent(userId) {
            val parameters = storageParameters(userId)
            val storage = userStorageProvider.getOrCreate(
                userId,
                parameters.platformProperties,
                parameters.shouldEncryptData,
                parameters.dbInvalidationControlEnabled,
            )
            create(userId, PreparedUserStorage(storage, parameters))
        }
    }

    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T = getOrCreate(userId).action()

    override fun get(userId: UserId): UserSessionScope? = userScopeStorage.get(userId)

    override suspend fun prepare(userId: UserId): PrepareUserSessionResult {
        get(userId)?.let {
            return PrepareUserSessionResult.Success(it)
        }

        val parameters = storageParameters(userId)
        return when (
            val result = userStorageProvider.prepare(
                userId,
                parameters.platformProperties,
                parameters.shouldEncryptData,
                parameters.dbInvalidationControlEnabled,
            )
        ) {
            is UserStoragePreparationResult.Success -> {
                val scope = userScopeStorage.computeIfAbsent(userId) {
                    create(userId, PreparedUserStorage(result.storage, parameters))
                }
                PrepareUserSessionResult.Success(scope)
            }

            is UserStoragePreparationResult.Failure ->
                PrepareUserSessionResult.Failure(result.reason.toUserSessionPreparationFailure())
        }
    }

    override fun observePreparation(userId: UserId): Flow<UserSessionPreparationState> =
        userStorageProvider.observe(userId).map { state ->
            when (state) {
                UserStorageState.NotStarted -> UserSessionPreparationState.NotStarted
                UserStorageState.OpeningDatabase -> UserSessionPreparationState.OpeningDatabase
                UserStorageState.MigratingDatabase -> UserSessionPreparationState.MigratingDatabase
                is UserStorageState.Ready -> UserSessionPreparationState.Ready
                is UserStorageState.Failed ->
                    UserSessionPreparationState.Failed(state.reason.toUserSessionPreparationFailure())
            }
        }

    override suspend fun delete(userId: UserId) {
        globalCallManager.removeInMemoryCallingManagerForUser(userId)
        userScopeStorage.remove(userId)
        userStorageProvider.remove(userId)
        removeAuthenticatedNetworkForUser(userId)
    }

    internal abstract fun storageParameters(userId: UserId): UserStorageSessionParameters

    internal abstract fun create(userId: UserId, preparedStorage: PreparedUserStorage): UserSessionScope
}

internal expect class UserSessionScopeProviderImpl : UserSessionScopeProvider {
    override fun get(userId: UserId): UserSessionScope?
    override fun getOrCreate(userId: UserId): UserSessionScope
    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    override suspend fun prepare(userId: UserId): PrepareUserSessionResult
    override fun observePreparation(userId: UserId): Flow<UserSessionPreparationState>
    override suspend fun delete(userId: UserId)
}
