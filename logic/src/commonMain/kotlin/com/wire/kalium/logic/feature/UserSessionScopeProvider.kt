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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.session.StoreSessionParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.userstorage.di.UserStorageProvider
import com.wire.kalium.logic.feature.call.GlobalCallManager

internal interface UserSessionScopeProvider {
    suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean = false)
    suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit>
    fun get(userId: UserId): UserSessionScope?
    suspend fun open(userId: UserId): UserSessionScope
    suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T
    fun getOrCreate(userId: UserId): UserSessionScope
    fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean
    suspend fun delete(userId: UserId)
    suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
    suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
}

internal abstract class UserSessionScopeProviderCommon(
    private val globalCallManager: GlobalCallManager,
    private val userStorageProvider: UserStorageProvider,
    private val removeAuthenticatedNetworkForUser: suspend (UserId) -> Unit,
    protected val userAgent: String
) : UserSessionScopeProvider {

    override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean) = Unit

    override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit> = Either.Right(Unit)

    private val userScopeStorage: ConcurrentMutableMap<UserId, UserSessionScope> by lazy {
        ConcurrentMutableMap()
    }

    override suspend fun open(userId: UserId): UserSessionScope = getOrCreate(userId)

    override suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T = open(userId).action()

    override fun getOrCreate(userId: UserId): UserSessionScope =
        userScopeStorage.computeIfAbsent(userId) {
            create(userId)
        }

    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T = getOrCreate(userId).action()

    override fun get(userId: UserId): UserSessionScope? = userScopeStorage.get(userId)

    override suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit> =
        sessionRepository.storeSession(session)

    override suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit> =
        sessionRepository.deleteSession(userId).onSuccess {
            get(userId)?.cancel()
            delete(userId)
        }

    override suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean {
        prepare()
        get(userId)?.stopAccountWork()
        clearLocalData()
        delete(userId)
        return true
    }

    override suspend fun delete(userId: UserId) {
        globalCallManager.removeInMemoryCallingManagerForUser(userId)
        userScopeStorage.remove(userId)
        userStorageProvider.remove(userId)
        removeAuthenticatedNetworkForUser(userId)
    }

    internal abstract fun create(userId: UserId): UserSessionScope
}

internal expect class UserSessionScopeProviderImpl : UserSessionScopeProvider {
    override suspend fun invalidateReceiving(userId: UserId, originatingScope: CoroutineScope, cryptoReplacement: Boolean)
    override suspend fun prepareReceiving(userId: UserId, originatingScope: CoroutineScope): Either<CoreFailure, Unit>
    override fun get(userId: UserId): UserSessionScope?
    override suspend fun open(userId: UserId): UserSessionScope
    override suspend fun <T> open(userId: UserId, action: UserSessionScope.() -> T): T
    override fun getOrCreate(userId: UserId): UserSessionScope
    override fun <T> getOrCreate(userId: UserId, action: UserSessionScope.() -> T): T
    override suspend fun logout(
        userId: UserId,
        originatingScope: CoroutineScope,
        prepare: suspend () -> Unit,
        clearLocalData: suspend () -> Unit,
    ): Boolean
    override suspend fun delete(userId: UserId)
    override suspend fun deleteSession(userId: UserId, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
    override suspend fun storeSession(session: StoreSessionParam, sessionRepository: SessionRepository): Either<StorageFailure, Unit>
}
