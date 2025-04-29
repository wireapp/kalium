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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.EpochChangesObserver

/**
 * Observes all the MLS Conversations epoch changes and call to re-check Verification status.
 */
internal interface ObserveE2EIConversationsVerificationStatusesUseCase {
    suspend operator fun invoke()
}

@Suppress("LongParameterList")
internal class ObserveE2EIConversationsVerificationStatusesUseCaseImpl(
    private val fetchMLSVerificationStatus: FetchMLSVerificationStatusUseCase,
    private val epochChangesObserver: EpochChangesObserver,
    kaliumLogger: KaliumLogger
) : ObserveE2EIConversationsVerificationStatusesUseCase {

    private val logger = kaliumLogger.withTextTag("MLSConversationsVerificationStatusesHandler")

    override suspend fun invoke() {
        logger.d("Starting to monitor")
        epochChangesObserver.observe()
            .collect { groupWithEpoch ->
                logger.d("Epoch changed for group ${groupWithEpoch.epoch}")
                fetchMLSVerificationStatus(groupWithEpoch.groupId)
            }
    }
}
