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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.ZERO

/**
 * When invoked, this use case will start observing on a given conversation, the currently applied [SelfDeletionTimer]
 */
interface ObserveSelfDeletionTimerSettingsForConversationUseCase {
    /**
     * @param conversationId the conversation id to observe
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<SelfDeletionTimer>
}

class ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository
) : ObserveSelfDeletionTimerSettingsForConversationUseCase {
    override suspend fun invoke(conversationId: ConversationId): Flow<SelfDeletionTimer> =
        userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            .combine(userConfigRepository.observeConversationSelfDeletionTimer(conversationId))
            .distinctUntilChanged()
            .map { (teamSettingsSelfDeletionStatus, conversationSelfDeletionTimer) ->
                teamSettingsSelfDeletionStatus.fold({
                    kaliumLogger.e("There was an error when fetching team settings self deletion timer")
                    defaultToConversationTimer(conversationSelfDeletionTimer)
                }, { teamSettingsStatus ->
                    conversationSelfDeletionTimer.fold({
                        kaliumLogger.e("There was an error when fetching conversation self deletion timer")
                        SelfDeletionTimer.Enabled(ZERO)
                    }, { conversationTimer ->
                        val teamSettingsTimer = teamSettingsStatus.enforcedSelfDeletionTimer
                        if (teamSettingsTimer.isDisabled || teamSettingsTimer.isEnforced) {
                            teamSettingsTimer
                        } else {
                            conversationTimer.selfDeletionTimer
                        }
                    })
                })
            }

    private fun defaultToConversationTimer(
        conversationSelfDeletionStatus: Either<StorageFailure, ConversationSelfDeletionStatus>
    ): SelfDeletionTimer = conversationSelfDeletionStatus.fold({
        kaliumLogger.e("There was an error when fetching conversation self deletion timer")
        SelfDeletionTimer.Enabled(ZERO)
    }, {
        it.selfDeletionTimer
    })
}
