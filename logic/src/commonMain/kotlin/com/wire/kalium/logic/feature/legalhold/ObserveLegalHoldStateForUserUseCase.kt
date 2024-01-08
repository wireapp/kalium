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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case that allows to observe the legal hold state for a given user.
 */
interface ObserveLegalHoldStateForUserUseCase {
    suspend operator fun invoke(userId: UserId): Flow<LegalHoldState>
}

internal class ObserveLegalHoldStateForUserUseCaseImpl internal constructor(
    private val clientRepository: ClientRepository
) : ObserveLegalHoldStateForUserUseCase {
    override suspend fun invoke(userId: UserId): Flow<LegalHoldState> =
        clientRepository.observeClientsByUserId(userId).map {
            it.fold(
                {
                    LegalHoldState.Disabled
                },
                { clients ->
                    val isLegalHoldEnabled = clients.any { otherUserClient ->
                        otherUserClient.deviceType == DeviceType.LegalHold
                    }
                    if (isLegalHoldEnabled) {
                        LegalHoldState.Enabled
                    } else {
                        LegalHoldState.Disabled
                    }
                }
            )
        }
}

sealed class LegalHoldState {
    data object Enabled : LegalHoldState()
    data object Disabled : LegalHoldState()
}
