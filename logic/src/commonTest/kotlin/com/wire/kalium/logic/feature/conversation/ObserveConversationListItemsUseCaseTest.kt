/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.conversation

import androidx.paging.PagingConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ObserveConversationListItemsUseCaseTest {
    private val useCase = ObserveConversationListItemsUseCaseImpl(mock<ConversationRepository>())

    @Test
    fun placeholdersAreRejectedBeforeOpeningTheRepository() {
        assertFailsWith<IllegalArgumentException> { useCase(PagingConfig(20)) }
    }

    @Test
    fun jumpingIsRejectedBeforeOpeningTheRepository() {
        assertFailsWith<IllegalArgumentException> { useCase(PagingConfig(20, enablePlaceholders = false, jumpThreshold = 100)) }
    }
}
