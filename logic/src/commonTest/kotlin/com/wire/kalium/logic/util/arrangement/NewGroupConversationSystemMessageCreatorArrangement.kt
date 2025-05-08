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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

internal interface NewGroupConversationSystemMessageCreatorArrangement {
    val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator

    suspend fun withPersistUnverifiedWarningMessageSuccess(): NewGroupConversationSystemMessageCreatorArrangement
}

internal class NewGroupConversationSystemMessageCreatorArrangementImpl : NewGroupConversationSystemMessageCreatorArrangement {
    override val newGroupConversationSystemMessagesCreator: NewGroupConversationSystemMessagesCreator =
        mock(NewGroupConversationSystemMessagesCreator::class)

    val persistMessage = mock(PersistMessageUseCase::class)

    override suspend fun withPersistUnverifiedWarningMessageSuccess() = apply {
        coEvery {
            newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(any(), any())
        }.returns(Either.Right(Unit))
    }

}
