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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.common.functional.getOrElse

internal interface IsMessageSentInSelfConversationUseCase {
    suspend operator fun invoke(message: Message): Boolean
}

internal class IsMessageSentInSelfConversationUseCaseImpl internal constructor(
    private val selfConversationIdProvider: SelfConversationIdProvider
) : IsMessageSentInSelfConversationUseCase {

    override suspend fun invoke(message: Message): Boolean {
        val selfConversationIds = selfConversationIdProvider().getOrElse(emptyList())
        return selfConversationIds.contains(message.conversationId)
    }

}
