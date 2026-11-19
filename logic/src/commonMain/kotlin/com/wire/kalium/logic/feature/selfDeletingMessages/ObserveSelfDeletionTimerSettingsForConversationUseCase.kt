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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.messaging.sending.outgoingSelfDeletionTimer
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * When invoked, this use case will start observing on a given conversation, the currently applied [SelfDeletionTimer]
 */
public interface ObserveSelfDeletionTimerSettingsForConversationUseCase {
    /**
     * @param conversationId the conversation id to observe
     * @param considerSelfUserSettings if true, the user settings will be considered,
     *          otherwise only the team and conversation settings will be considered
     */
    public suspend operator fun invoke(conversationId: ConversationId, considerSelfUserSettings: Boolean): Flow<SelfDeletionTimer>

    /** Reads current settings once. Unlike observation, an absent conversation completes with a failure. */
    public suspend fun getSnapshot(conversationId: ConversationId, considerSelfUserSettings: Boolean): Snapshot

    public sealed interface Snapshot {
        public data class Success(val timer: SelfDeletionTimer) : Snapshot
        public data class Failure(val cause: StorageFailure) : Snapshot
    }
}

internal class ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveSelfDeletionTimerSettingsForConversationUseCase {

    override suspend fun getSnapshot(
        conversationId: ConversationId,
        considerSelfUserSettings: Boolean
    ): ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot = withContext(dispatcher.io) {
        conversationRepository.getConversationById(conversationId).fold({
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Failure(it)
        }, { conversation ->
            val timer = conversationRepository.isCellEnabled(conversationId).fold({
                SelfDeletionTimer.Disabled
            }, { enabled ->
                if (enabled) SelfDeletionTimer.Disabled else outgoingSelfDeletionTimer(
                    teamTimer = userConfigRepository.getTeamSettingsSelfDeletionStatus().getOrNull()?.enforcedSelfDeletionTimer,
                    groupTimer = conversation.messageTimer,
                    userTimer = conversation.userMessageTimer,
                    considerSelfUserSettings = considerSelfUserSettings,
                )
            })
            ObserveSelfDeletionTimerSettingsForConversationUseCase.Snapshot.Success(timer)
        })
    }

    override suspend fun invoke(conversationId: ConversationId, considerSelfUserSettings: Boolean): Flow<SelfDeletionTimer> =
        withContext(dispatcher.io) {
            conversationRepository.isCellEnabled(conversationId).fold({
                flowOf(SelfDeletionTimer.Disabled)
            }, { enabled ->
                if (enabled) {
                    flowOf(SelfDeletionTimer.Disabled)
                } else {
                    userConfigRepository.observeTeamSettingsSelfDeletingStatus().combine(
                        conversationRepository.observeConversationById(conversationId)
                    ) { teamSettings, conversationDetailsEither ->
                        val conversation = conversationDetailsEither.getOrNull()
                        outgoingSelfDeletionTimer(
                            teamTimer = teamSettings.getOrNull()?.enforcedSelfDeletionTimer,
                            groupTimer = conversation?.messageTimer,
                            userTimer = conversation?.userMessageTimer,
                            considerSelfUserSettings = considerSelfUserSettings,
                        )
                    }
                }
            })
        }
}
