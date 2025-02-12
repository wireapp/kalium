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

import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock

internal interface SelfConversationIdProviderArrangement {
    @Mock
    val selfConversationIdProvider: SelfConversationIdProvider

    suspend fun withSelfConversationIds(conversationIds: List<ConversationId>)
}

internal open class SelfConversationIdProviderArrangementImpl : SelfConversationIdProviderArrangement {
    @Mock
    override val selfConversationIdProvider: SelfConversationIdProvider = mock(SelfConversationIdProvider::class)

    override suspend fun withSelfConversationIds(conversationIds: List<ConversationId>) {
        coEvery {
            selfConversationIdProvider.invoke()
        }.returns(Either.Right(conversationIds))
    }
}
