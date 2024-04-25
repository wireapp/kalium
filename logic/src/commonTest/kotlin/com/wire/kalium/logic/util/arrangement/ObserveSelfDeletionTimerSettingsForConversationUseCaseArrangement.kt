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
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

interface ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement {
    @Mock
    val observeSelfDeletionTimerSettingsForConversation: ObserveSelfDeletionTimerSettingsForConversationUseCase

    suspend fun withConversationTimer(
        result: Flow<SelfDeletionTimer>,
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()),
        considerSelfUserSettings: Matcher<Boolean> = AnyMatcher(valueOf())
    )
}

open class ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangementImpl :
    ObserveSelfDeletionTimerSettingsForConversationUseCaseArrangement {
    @Mock
    override val observeSelfDeletionTimerSettingsForConversation: ObserveSelfDeletionTimerSettingsForConversationUseCase =
        mock(ObserveSelfDeletionTimerSettingsForConversationUseCase::class)

    override suspend fun withConversationTimer(
        result: Flow<SelfDeletionTimer>,
        conversationId: Matcher<ConversationId>,
        considerSelfUserSettings: Matcher<Boolean>
    ) {
        coEvery {
            observeSelfDeletionTimerSettingsForConversation(
                matches { conversationId.matches(it) },
                matches { considerSelfUserSettings.matches(it) }
            )
        }.returns(result)
    }
}
