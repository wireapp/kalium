@file:OptIn(com.wire.kalium.conversation.ExperimentalConversationApi::class)
@file:Suppress("TooGenericExceptionCaught")

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

package com.wire.kalium.conversation.local

import com.wire.kalium.conversation.ConversationContextFailure
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.conversation.ExperimentalConversationApi
import com.wire.kalium.logic.data.id.ConversationId
import kotlin.coroutines.cancellation.CancellationException

/** Adapter seam for the existing SQLDelight-backed client conversation repository. */
@ExperimentalConversationApi
public fun interface LocalConversationContextDataSource {
    public suspend fun load(conversationId: ConversationId): ConversationContextResult
}

@ExperimentalConversationApi
public class LocalConversationContextProvider(
    private val dataSource: LocalConversationContextDataSource,
) : ConversationContextProvider {
    override suspend fun getForCall(conversationId: ConversationId): ConversationContextResult = try {
        dataSource.load(conversationId)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        ConversationContextResult.Failure(
            ConversationContextFailure.Local("Local conversation context lookup failed", failure),
        )
    }
}
