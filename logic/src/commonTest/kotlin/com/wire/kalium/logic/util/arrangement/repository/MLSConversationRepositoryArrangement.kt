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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.Flow

internal interface MLSConversationRepositoryArrangement {
    val epochChangesObserver: EpochChangesObserver



    fun withObserveEpochChanges(result: Flow<GroupID>)
}

internal open class MLSConversationRepositoryArrangementImpl :
    MLSConversationRepositoryArrangement {

    @Mock
    override val epochChangesObserver: EpochChangesObserver = mock(EpochChangesObserver::class)

    override fun withObserveEpochChanges(result: Flow<GroupID>) {
        given(epochChangesObserver)
            .function(epochChangesObserver::observe)
            .whenInvoked()
            .thenReturn(result)
    }
}
