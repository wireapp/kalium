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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.feature.message.ephemeral.DeleteEphemeralMessagesAfterEndDateUseCase
import com.wire.kalium.logic.framework.TestConversationDetails
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NotifyConversationIsOpenUseCaseTest {

    @Test
    fun givenConversationId_whenInvoking_thenShouldGetDetailsFromRepository() = runTest {
        val details = TestConversationDetails.CONVERSATION_GROUP
        val (arrangement, notifyConversationIsOpenUseCase) = arrange {
            withObserveConversationDetailsByIdReturning(
                Either.Right(details)
            )

            withDeleteEphemeralMessageEndDateSuccess()
        }
        notifyConversationIsOpenUseCase.invoke(details.conversation.id)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::observeConversationDetailsById)
            .with(eq(details.conversation.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenGroupConversationId_whenInvoking_thenShouldNotUseResolver() = runTest {
        val details = TestConversationDetails.CONVERSATION_GROUP
        val (arrangement, notifyConversationIsOpenUseCase) = arrange {
            withObserveConversationDetailsByIdReturning(
                Either.Right(details)
            )

            withDeleteEphemeralMessageEndDateSuccess()
        }
        notifyConversationIsOpenUseCase.invoke(details.conversation.id)

        verify(arrangement.oneOnOneResolver)
            .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUser)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenOneOnOneConversationId_whenInvoking_thenShouldResolveProtocolForUser() = runTest {
        val details = TestConversationDetails.CONVERSATION_ONE_ONE
        val (arrangement, notifyConversationIsOpenUseCase) = arrange {
            withObserveConversationDetailsByIdReturning(
                Either.Right(details)
            )
            withResolveOneOnOneConversationWithUserReturning(
                Either.Right(details.conversation.id)
            )

            withDeleteEphemeralMessageEndDateSuccess()
        }
        notifyConversationIsOpenUseCase.invoke(details.conversation.id)

        verify(arrangement.oneOnOneResolver)
            .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUser)
            .with(eq(details.otherUser))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(
        private val configure: Arrangement.() -> Unit
    ) : OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        @Mock
        private val deleteEphemeralMessageEndDate = mock(classOf<DeleteEphemeralMessagesAfterEndDateUseCase>())

        fun withDeleteEphemeralMessageEndDateSuccess() {
            given(deleteEphemeralMessageEndDate)
                .suspendFunction(deleteEphemeralMessageEndDate::invoke)
                .whenInvoked()
                .thenReturn(Unit)
        }

        fun arrange(): Pair<Arrangement, NotifyConversationIsOpenUseCase> = run {
            configure()
            this@Arrangement to NotifyConversationIsOpenUseCaseImpl(
                oneOnOneResolver = oneOnOneResolver,
                conversationRepository = conversationRepository,
                kaliumLogger = kaliumLogger,
                deleteEphemeralMessageEndDate = deleteEphemeralMessageEndDate
            )
        }
    }

    private companion object {
        fun arrange(configure: Arrangement.() -> Unit) = Arrangement(configure).arrange()
    }
}
