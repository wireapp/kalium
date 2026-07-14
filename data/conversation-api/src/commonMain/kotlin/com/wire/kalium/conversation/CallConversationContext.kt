@file:OptIn(com.wire.kalium.conversation.ExperimentalConversationApi::class)

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

package com.wire.kalium.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId

@ExperimentalConversationApi
public enum class CallConversationType {
    SELF,
    ONE_TO_ONE,
    GROUP,
    CHANNEL,
    MEETING,
    CONNECTION_PENDING,
}

@ExperimentalConversationApi
public sealed interface CallConversationProtocol {
    public data object Proteus : CallConversationProtocol

    public data class Mls(public val groupId: GroupID, public val epoch: ULong?) : CallConversationProtocol

    public data class Mixed(public val groupId: GroupID, public val epoch: ULong?) : CallConversationProtocol
}

@ExperimentalConversationApi
public data class CallMember(
    public val userId: UserId,
    public val role: String,
    public val isSelf: Boolean,
    public val isService: Boolean,
)

@ExperimentalConversationApi
public data class CallClient(public val userId: UserId, public val clientId: String)

/** The minimum remote/local conversation view required by calling and MLS signalling. */
@ExperimentalConversationApi
public data class CallConversationContext(
    public val conversationId: ConversationId,
    public val type: CallConversationType,
    public val protocol: CallConversationProtocol,
    public val members: List<CallMember>,
    public val clients: List<CallClient>,
    public val teamId: String?,
)

@ExperimentalConversationApi
public sealed interface ConversationContextFailure {
    public data object NotFound : ConversationContextFailure

    public data object AccessDenied : ConversationContextFailure

    public data class Remote(public val description: String, public val cause: Throwable? = null) : ConversationContextFailure

    public data class Local(public val description: String, public val cause: Throwable? = null) : ConversationContextFailure

    public data class Invalid(public val description: String) : ConversationContextFailure
}

@ExperimentalConversationApi
public sealed interface ConversationContextResult {
    public data class Success(public val context: CallConversationContext) : ConversationContextResult

    public data class Failure(public val failure: ConversationContextFailure) : ConversationContextResult
}

@ExperimentalConversationApi
public fun interface ConversationContextProvider {
    public suspend fun getForCall(conversationId: ConversationId): ConversationContextResult
}
