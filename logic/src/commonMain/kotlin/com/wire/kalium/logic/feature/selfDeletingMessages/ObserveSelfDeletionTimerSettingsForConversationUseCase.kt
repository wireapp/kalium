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
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.TeamSelfDeleteTimer
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.isPositiveNotNull
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * When invoked, this use case will start observing on a given conversation, the currently applied [SelfDeletionTimer]
 */
interface ObserveSelfDeletionTimerSettingsForConversationUseCase {
    /**
     * @param conversationId the conversation id to observe
     * @param considerSelfUserSettings if true, the user settings will be considered,
     *          otherwise only the team and conversation settings will be considered
     */
    suspend operator fun invoke(conversationId: ConversationId, considerSelfUserSettings: Boolean): Flow<SelfDeletionTimer>
}

class ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveSelfDeletionTimerSettingsForConversationUseCase {

    override suspend fun invoke(conversationId: ConversationId, considerSelfUserSettings: Boolean): Flow<SelfDeletionTimer> =
        withContext(dispatcher.io) {
            userConfigRepository.observeTeamSettingsSelfDeletingStatus()
                .combine(
                    conversationRepository.observeConversationById(conversationId)
                ) { teamSettings, conversationDetailsEither ->
                    teamSettings.fold({
                        onTeamEnabled(conversationDetailsEither, considerSelfUserSettings)
                    }, {
                        when (val deletionTimer = it.enforcedSelfDeletionTimer) {
                            TeamSelfDeleteTimer.Disabled -> SelfDeletionTimer.Disabled
                            TeamSelfDeleteTimer.Enabled -> onTeamEnabled(conversationDetailsEither, considerSelfUserSettings)
                            is TeamSelfDeleteTimer.Enforced -> SelfDeletionTimer.Enforced.ByTeam(
                                deletionTimer.enforcedDuration
                            )
                        }
                    })
                }
        }

    private fun onTeamEnabled(conversation: Either<StorageFailure, Conversation>, considerSelfUserSettings: Boolean): SelfDeletionTimer =
        conversation.fold({
            SelfDeletionTimer.Enabled(null)
        }, {
            val messageTimer = it.messageTimer
            when {
                messageTimer.isPositiveNotNull() -> SelfDeletionTimer.Enforced.ByGroup(messageTimer)
                considerSelfUserSettings && it.userMessageTimer.isPositiveNotNull() -> SelfDeletionTimer.Enabled(it.userMessageTimer)
                else -> SelfDeletionTimer.Enabled(null)
            }
        })
}
