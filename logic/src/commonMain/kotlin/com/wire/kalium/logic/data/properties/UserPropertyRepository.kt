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

package com.wire.kalium.logic.data.properties

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.api.base.authenticated.properties.PropertyKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

interface UserPropertyRepository {
    suspend fun getReadReceiptsStatus(): Boolean
    suspend fun observeReadReceiptsStatus(): Flow<Either<CoreFailure, Boolean>>
    suspend fun setReadReceiptsEnabled(): Either<CoreFailure, Unit>
    suspend fun deleteReadReceiptsProperty(): Either<CoreFailure, Unit>
    suspend fun getTypingIndicatorStatus(): Boolean
    suspend fun observeTypingIndicatorStatus(): Flow<Either<CoreFailure, Boolean>>
    suspend fun setTypingIndicatorEnabled(): Either<CoreFailure, Unit>
    suspend fun removeTypingIndicatorProperty(): Either<CoreFailure, Unit>
}

internal class UserPropertyDataSource(
    private val propertiesApi: PropertiesApi,
    private val userConfigRepository: UserConfigRepository,
) : UserPropertyRepository {
    override suspend fun getReadReceiptsStatus(): Boolean =
        userConfigRepository.isReadReceiptsEnabled()
            .firstOrNull()
            ?.fold({ false }, { it }) ?: false

    override suspend fun observeReadReceiptsStatus(): Flow<Either<CoreFailure, Boolean>> = userConfigRepository.isReadReceiptsEnabled()

    override suspend fun setReadReceiptsEnabled(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.setProperty(PropertyKey.WIRE_RECEIPT_MODE, 1)
    }.flatMap {
        userConfigRepository.setReadReceiptsStatus(true)
    }

    override suspend fun deleteReadReceiptsProperty(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.deleteProperty(PropertyKey.WIRE_RECEIPT_MODE)
    }.flatMap {
        userConfigRepository.setReadReceiptsStatus(false)
    }

    override suspend fun getTypingIndicatorStatus(): Boolean =
        userConfigRepository.isTypingIndicatorEnabled()
            .firstOrNull()
            ?.fold({ false }, { it }) ?: true

    override suspend fun observeTypingIndicatorStatus(): Flow<Either<CoreFailure, Boolean>> =
        userConfigRepository.isTypingIndicatorEnabled()

    override suspend fun setTypingIndicatorEnabled(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.deleteProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE)
    }.flatMap {
        userConfigRepository.setTypingIndicatorStatus(true)
    }

    override suspend fun removeTypingIndicatorProperty(): Either<CoreFailure, Unit> = wrapApiRequest {
        propertiesApi.setProperty(PropertyKey.WIRE_TYPING_INDICATOR_MODE, 0)
    }.flatMap {
        userConfigRepository.setTypingIndicatorStatus(false)
    }
}
