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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.user.IsE2EIEnabledUseCase
import com.wire.kalium.logic.functional.fold

/**
 * This use case is used to get the e2ei certificate status of specific user
 */
interface IsOtherUserE2EIVerifiedUseCase {
    suspend operator fun invoke(userId: UserId): Boolean
}

class IsOtherUserE2EIVerifiedUseCaseImpl internal constructor(
    private val mlsConversationRepository: MLSConversationRepository,
    private val isE2EIEnabledUseCase: IsE2EIEnabledUseCase
) : IsOtherUserE2EIVerifiedUseCase {
    override suspend operator fun invoke(userId: UserId): Boolean =
        if (isE2EIEnabledUseCase()) {
            mlsConversationRepository.getUserIdentity(userId).fold({ false }, { it.isUserMLSVerified() }
            )
        } else {
            false
        }
}
