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
package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.self.ChangeHandleRequest
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.self.UserUpdateRequest
import com.wire.kalium.persistence.dao.UserDAO

internal interface AccountRepository {
    suspend fun deleteAccount(password: String?): Either<NetworkFailure, Unit>
    suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit>
    suspend fun updateSelfDisplayName(displayName: String): Either<CoreFailure, Unit>

    /**
     * Updates the self user's email address.
     * @param email the new email address
     * @return [Either.Right] with [Boolean] true if the verify email was sent and false if there are no change,
     * otherwise [Either.Left] with [NetworkFailure]
     */
    suspend fun updateSelfEmail(email: String): Either<NetworkFailure, Boolean>
    suspend fun updateLocalSelfUserHandle(handle: String): Either<CoreFailure, Unit>
    suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus): Either<StorageFailure, Unit>
}

internal class AccountRepositoryImpl(
    private val selfApi: SelfApi,
    private val userDAO: UserDAO,
    private val selfUserId: UserId,
    private val availabilityStatusMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper()
) : AccountRepository {
    override suspend fun deleteAccount(password: String?): Either<NetworkFailure, Unit> = wrapApiRequest {
        selfApi.deleteAccount(password)
    }

    override suspend fun updateSelfHandle(handle: String): Either<NetworkFailure, Unit> = wrapApiRequest {
        selfApi.changeHandle(ChangeHandleRequest(handle))
    }

    override suspend fun updateSelfDisplayName(displayName: String): Either<CoreFailure, Unit> = wrapApiRequest {
        selfApi.updateSelf(UserUpdateRequest(displayName, null, null))
    }.flatMap {
        wrapStorageRequest {
            userDAO.updateUserDisplayName(selfUserId.toDao(), displayName)
        }
    }

    override suspend fun updateSelfEmail(email: String): Either<NetworkFailure, Boolean> = wrapApiRequest {
        selfApi.updateEmailAddress(email)
    }

    override suspend fun updateLocalSelfUserHandle(handle: String) = wrapStorageRequest {
        userDAO.updateUserHandle(selfUserId.toDao(), handle)
    }

    override suspend fun updateSelfUserAvailabilityStatus(status: UserAvailabilityStatus) = wrapStorageRequest {
        userDAO.updateUserAvailabilityStatus(
            selfUserId.toDao(),
            availabilityStatusMapper.fromModelAvailabilityStatusToDao(status)
        )
    }

}
