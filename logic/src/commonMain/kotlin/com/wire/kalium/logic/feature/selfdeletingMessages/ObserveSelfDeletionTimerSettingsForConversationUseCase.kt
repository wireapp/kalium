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
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * When invoked, this use case will start observing on a given conversation, the currently applied [SelfDeletionTimer]
 */
interface ObserveSelfDeletionTimerSettingsForConversationUseCase {
    /**
     * @param conversationId the conversation id to observe
     */
    suspend operator fun invoke(conversationId: ConversationId, includeSelfSettings: Boolean): Flow<SelfDeletionTimer>
}

class ObserveSelfDeletionTimerSettingsForConversationUseCaseImpl internal constructor(
    private val userConfigRepository: UserConfigRepository,
    private val conversationRepository: ConversationRepository
) : ObserveSelfDeletionTimerSettingsForConversationUseCase {

    override suspend fun invoke(conversationId: ConversationId, includeSelfSettings: Boolean): Flow<SelfDeletionTimer> =
        userConfigRepository.observeTeamSettingsSelfDeletingStatus()
            .flatMapRightWithEither {
                kaliumLogger.d("KBX ${it.enforcedSelfDeletionTimer}")
                when (it.enforcedSelfDeletionTimer) {
                    SelfDeletionTimer.Disabled -> flowOf(
                        Either.Right(
                            SelfDeletionTimer.Disabled
                        )
                    )

                    is SelfDeletionTimer.Enforced -> flowOf(Either.Right(it.enforcedSelfDeletionTimer))
                    is SelfDeletionTimer.Enabled -> conversationRepository.observeById(conversationId)
                        .flatMapRight { conversation ->
                            if (conversation.messageTimer != null) {
                                kaliumLogger.d("KBX timer group ${conversation.messageTimer}")
                                flowOf(
                                    SelfDeletionTimer.Enforced.ByGroup(
                                        conversation.messageTimer.toDuration(DurationUnit.MILLISECONDS)
                                    )
                                )
                            } else if (includeSelfSettings) {
                                kaliumLogger.d("KBX user")
                                userConfigRepository.observeConversationSelfDeletionTimer(conversationId)
                                    .map { selfDeletionStatusEither ->
                                        selfDeletionStatusEither.fold({
                                            SelfDeletionTimer.Enabled(ZERO)
                                        }, { selfDeletionStatus ->
                                            selfDeletionStatus.selfDeletionTimer
                                        })
                                    }
                            } else {
                                kaliumLogger.d("KBX enabled")
                                flowOf(SelfDeletionTimer.Enabled(ZERO))
                            }
                        }
                }
            }
            .map { selfDeletionTimerEither ->
                selfDeletionTimerEither.fold({ SelfDeletionTimer.Disabled }, { it })
            }
}
