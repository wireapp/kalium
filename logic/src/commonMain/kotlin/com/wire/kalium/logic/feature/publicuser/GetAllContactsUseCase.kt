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

package com.wire.kalium.logic.feature.publicuser

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Operation that fetches all known users
 *
 * @return GetAllContactsResult with list of known users
 */
interface GetAllContactsUseCase {
    suspend operator fun invoke(): Flow<GetAllContactsResult>
}

internal class GetAllContactsUseCaseImpl internal constructor(
    private val userRepository: UserRepository
) : GetAllContactsUseCase {

    override suspend fun invoke(): Flow<GetAllContactsResult> =
        userRepository.observeAllKnownUsers()
            .map { it.fold(GetAllContactsResult::Failure, GetAllContactsResult::Success) }

}

sealed class GetAllContactsResult {
    data class Success(val allContacts: List<OtherUser>) : GetAllContactsResult()
    data class Failure(val storageFailure: StorageFailure) : GetAllContactsResult()
}
