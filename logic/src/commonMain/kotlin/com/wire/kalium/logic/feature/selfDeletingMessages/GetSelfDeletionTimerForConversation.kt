/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.feature.selfDeletingMessages

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.messaging.sending.outgoingSelfDeletionTimer
import kotlinx.coroutines.flow.first

internal interface GetSelfDeletionTimerForConversation {
    suspend operator fun invoke(conversationId: ConversationId): Either<StorageFailure, SelfDeletionTimer>
}

/** A send needs a storage result, not an observer that waits for a missing conversation to appear. */
internal class GetSelfDeletionTimerForConversationImpl(
    private val conversations: ConversationRepository,
    private val userConfig: UserConfigRepository,
) : GetSelfDeletionTimerForConversation {
    override suspend fun invoke(conversationId: ConversationId): Either<StorageFailure, SelfDeletionTimer> =
        conversations.getConversationById(conversationId).flatMap { conversation ->
            conversations.isCellEnabled(conversationId).flatMap { cellsEnabled ->
                if (cellsEnabled) {
                    Either.Right(SelfDeletionTimer.Disabled)
                } else {
                    val team = userConfig.observeTeamSettingsSelfDeletingStatus().first().getOrNull()
                    Either.Right(
                        outgoingSelfDeletionTimer(
                            teamTimer = team?.enforcedSelfDeletionTimer,
                            groupTimer = conversation.messageTimer,
                            userTimer = conversation.userMessageTimer,
                            considerSelfUserSettings = true,
                        )
                    )
                }
            }
        }
}
