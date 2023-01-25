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

package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.functional.fold

/**
 * This use case will return true if the current user needs to register a client.
 */
interface NeedsToRegisterClientUseCase {
    suspend operator fun invoke(): Boolean
}

class NeedsToRegisterClientUseCaseImpl(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val sessionRepository: SessionRepository,
    private val selfUserId: UserId
) : NeedsToRegisterClientUseCase {
    override suspend fun invoke(): Boolean =
        sessionRepository.userAccountInfo(selfUserId).fold(
            { false },
            {
                when (it) {
                    is AccountInfo.Invalid -> false
                    is AccountInfo.Valid -> currentClientIdProvider().fold({ true }, { false })
                }
            }
        )
}
