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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow

internal interface ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement {
        val observeSelfDeletionTimerSettingsForConversation: ObserveSelfDeletionTimerSettingsForConversationUseCase

    suspend fun withConversationTimer(
        result: Flow<SelfDeletionTimer>,
        conversationId: (ConversationId) -> Boolean = { true },
        considerSelfUserSettings: (Boolean) -> Boolean = { true }
    )
}

internal open class ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangementImpl :
    ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement {
        override val observeSelfDeletionTimerSettingsForConversation: ObserveSelfDeletionTimerSettingsForConversationUseCase =
        mock<ObserveSelfDeletionTimerSettingsForConversationUseCase>(mode = MockMode.autoUnit)

    override suspend fun withConversationTimer(
        result: Flow<SelfDeletionTimer>,
        conversationId: (ConversationId) -> Boolean,
        considerSelfUserSettings: (Boolean) -> Boolean
    ) {
        everySuspend {
            observeSelfDeletionTimerSettingsForConversation(
                matches { conversationId(it) },
                matches { considerSelfUserSettings(it) }
            )
        }.returns(result)
    }
}
