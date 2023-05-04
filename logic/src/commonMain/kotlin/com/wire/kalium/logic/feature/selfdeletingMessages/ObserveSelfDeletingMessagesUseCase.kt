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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * When invoked, this use case will start observing on a given conversation, the currently applied [SelfDeletionTimer]
 */
interface ObserveSelfDeletingMessagesUseCase {
    /**
     * @param conversationId the conversation id to observe
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<SelfDeletionTimer>
}

class ObserveSelfDeletingMessagesUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository
) : ObserveSelfDeletingMessagesUseCase {
    override suspend fun invoke(conversationId: ConversationId): Flow<SelfDeletionTimer> =
        userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            .combine(userConfigRepository.observeConversationSelfDeletionTimer(conversationId))
            .distinctUntilChanged()
            .map { (teamSettingsSelfDeletionStatus, conversationSelfDeletionTimer) ->
                when {
                    // If both are left, we can't do anything so we default to Disabled
                    teamSettingsSelfDeletionStatus.isLeft() && conversationSelfDeletionTimer.isLeft() -> {
                        kaliumLogger.e("There was an error when fetching both team settings and conversation self deletion timer")
                        SelfDeletionTimer.Disabled
                    }
                    // If there is error when fetching team settings, we default to conversation self deletion timer
                    teamSettingsSelfDeletionStatus.isLeft() && conversationSelfDeletionTimer.isRight() -> {
                        kaliumLogger.e("There was an error when fetching team settings self deletion timer")
                        conversationSelfDeletionTimer.value.selfDeletionTimer
                    }
                    // If there is error when fetching conversation self deletion timer, we default to team settings
                    conversationSelfDeletionTimer.isLeft() && teamSettingsSelfDeletionStatus.isRight() -> {
                        kaliumLogger.e("There was an error when fetching conversation self deletion timer")
                        teamSettingsSelfDeletionStatus.value.enforcedSelfDeletionTimer
                    }

                    // If no errors, team settings have priority over conversation self deletion timer
                    else -> {
                        var selfDeletionTimer: SelfDeletionTimer = SelfDeletionTimer.Disabled
                        teamSettingsSelfDeletionStatus.map { teamSettingsStatus ->
                            val teamSettingsTimer = teamSettingsStatus.enforcedSelfDeletionTimer
                            selfDeletionTimer = if (!teamSettingsTimer.isEnabled || teamSettingsTimer.isEnforced) {
                                teamSettingsStatus.enforcedSelfDeletionTimer
                            } else {
                                (conversationSelfDeletionTimer as Either.Right).value.selfDeletionTimer
                            }
                        }
                        selfDeletionTimer
                    }
                }
            }
}
