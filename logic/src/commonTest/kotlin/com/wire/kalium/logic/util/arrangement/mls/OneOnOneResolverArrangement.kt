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
package com.wire.kalium.logic.util.arrangement.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.Job

interface OneOnOneResolverArrangement {

    val oneOnOneResolver: OneOnOneResolver

    fun withScheduleResolveOneOnOneConversationWithUserId()
    fun withResolveOneOnOneConversationWithUserIdReturning(result: Either<CoreFailure, ConversationId>)
    fun withResolveOneOnOneConversationWithUserReturning(result: Either<CoreFailure, ConversationId>)
    fun withResolveAllOneOnOneConversationsReturning(result: Either<CoreFailure, Unit>)

}

class OneOnOneResolverArrangementImpl : OneOnOneResolverArrangement {

    @Mock
    override val oneOnOneResolver = mock(OneOnOneResolver::class)
    override fun withScheduleResolveOneOnOneConversationWithUserId() {
        given(oneOnOneResolver)
            .suspendFunction(oneOnOneResolver::scheduleResolveOneOnOneConversationWithUserId)
            .whenInvokedWith(any(), any())
            .thenReturn(Job())
    }

    override fun withResolveOneOnOneConversationWithUserIdReturning(result: Either<CoreFailure, ConversationId>) {
        given(oneOnOneResolver)
            .suspendFunction(oneOnOneResolver::resolveOneOnOneConversationWithUserId)
            .whenInvokedWith(any(), eq(true))
            .thenReturn(result)
    }

    override fun withResolveOneOnOneConversationWithUserReturning(result: Either<CoreFailure, ConversationId>) {
        given(oneOnOneResolver)
            .suspendFunction(oneOnOneResolver::resolveOneOnOneConversationWithUser)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withResolveAllOneOnOneConversationsReturning(result: Either<CoreFailure, Unit>) {
        given(oneOnOneResolver)
            .suspendFunction(oneOnOneResolver::resolveAllOneOnOneConversations)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

}



