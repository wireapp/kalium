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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.client.ProteusClientProvider
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.common.functional.fold

/**
 * This use case will return true if the current user needs to register a client.
 */
interface NeedsToRegisterClientUseCase {
    suspend operator fun invoke(): Boolean
}

internal class NeedsToRegisterClientUseCaseImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val sessionRepository: SessionRepository,
    private val proteusClientProvider: ProteusClientProvider,
    private val selfUserId: UserId
) : NeedsToRegisterClientUseCase {
    override suspend fun invoke(): Boolean =
        sessionRepository.userAccountInfo(selfUserId).fold(
            { false },
            {
                when (it) {
                    is AccountInfo.Invalid -> false
                    is AccountInfo.Valid -> onValidAccount()
                }
            }
        )

    private suspend fun onValidAccount(): Boolean =
        proteusClientProvider.getOrError().fold({ true }, {
            currentClientIdProvider().fold({ true }, { false })
        })
}
