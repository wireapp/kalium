/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.selfdeletingMessages

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.configuration.SelfDeletingMessagesStatus
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Observe self deleting messages feature status
 */
interface ObserveSelfDeletingMessagesUseCase {
    suspend operator fun invoke(): Flow<SelfDeletingMessagesStatus>
}

class ObserveSelfDeletingMessagesUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository
) : ObserveSelfDeletingMessagesUseCase {
    override suspend fun invoke(): Flow<SelfDeletingMessagesStatus> =
        userConfigRepository.observeSelfDeletingMessagesStatus().map { selfDeletingMessagesStatus ->
            selfDeletingMessagesStatus.fold({
                when (it) {
                    is StorageFailure.DataNotFound -> {
                        kaliumLogger.e("Data not found for ObserveSelfDeletingMessagesUseCase")
                    }

                    is StorageFailure.Generic -> {
                        kaliumLogger.e("Storage Error : ${it.rootCause} in ObserveSelfDeletingMessagesUseCase", it.rootCause)
                    }
                }
                SelfDeletingMessagesStatus(false, null, null)
            }, {
                it
            })
        }
}
