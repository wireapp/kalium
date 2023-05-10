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
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMapRight
import com.wire.kalium.logic.functional.flatMapRightWithEither
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.ZERO

/**
 * When invoked, this use case will start observing on a given conversation, the currently applied [SelfDeletionTimer]
 */
interface ObserveSelfDeletionTimerSettingsForConversationUseCase {
    /**
     * @param conversationId the conversation id to observe
     */
    suspend operator fun invoke(conversationId: ConversationId, considerSelfUserSettings: Boolean): Flow<SelfDeletionTimer>
}

class ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository
) : ObserveSelfDeletionTimerSettingsForConversationUseCase {

    override suspend fun invoke(conversationId: ConversationId, considerSelfUserSettings: Boolean): Flow<SelfDeletionTimer> =
        userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            .flatMapRightWithEither {
                when (it.enforcedSelfDeletionTimer) {
                    SelfDeletionTimer.Disabled -> flowOf(Either.Right(SelfDeletionTimer.Disabled))
                    is SelfDeletionTimer.Enforced -> flowOf(Either.Right(it.enforcedSelfDeletionTimer))
                    is SelfDeletionTimer.Enabled -> {
                        conversationRepository.observeById(conversationId)
                            .flatMapRight { conversation ->
                                when {
                                    conversation.messageTimer != null -> flowOf(
                                        SelfDeletionTimer.Enforced.ByGroup(
                                            conversation.messageTimer
                                        )
                                    )

                                    considerSelfUserSettings -> userConfigRepository.observeConversationSelfDeletionTimer(conversationId)
                                        .map { selfDeletionStatusEither ->
                                            selfDeletionStatusEither.fold({
                                                SelfDeletionTimer.Enabled(ZERO)
                                            }, { selfDeletionStatus ->
                                                selfDeletionStatus.selfDeletionTimer
                                            })
                                        }

                                    else -> flowOf(SelfDeletionTimer.Enabled(ZERO))
                                }
                            }
                    }
                }
            }
            .map { selfDeletionTimerEither ->
                selfDeletionTimerEither.fold({ SelfDeletionTimer.Disabled }, { it })
            }
}
