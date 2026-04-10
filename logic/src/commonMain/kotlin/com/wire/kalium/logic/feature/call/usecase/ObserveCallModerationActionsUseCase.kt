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
package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallModerationAction
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe the [CallModerationAction]s for a given [ConversationId].
 * This will emit all the actions that have been added for that conversation, including those that were added before observing.
 */
public interface ObserveCallModerationActionsUseCase {
    public operator fun invoke(conversationId: ConversationId): Flow<CallModerationAction>
}

internal class ObserveCallModerationActionsUseCaseImpl(
    private val callModerationActionsRepository: CallModerationActionsRepository
) : ObserveCallModerationActionsUseCase {

    override fun invoke(conversationId: ConversationId): Flow<CallModerationAction> =
        callModerationActionsRepository.observeActions(conversationId)
}
