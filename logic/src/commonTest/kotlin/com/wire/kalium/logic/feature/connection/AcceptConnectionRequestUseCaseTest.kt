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

package com.wire.kalium.logic.feature.connection

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.NewGroupConversationSystemMessageCreatorArrangement
import com.wire.kalium.logic.util.arrangement.NewGroupConversationSystemMessageCreatorArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConnectionRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.FetchConversationUseCaseArrangementImpl
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AcceptConnectionRequestUseCaseTest {

    @Test
    fun givenSuccess_whenInvokingUseCase_thenShouldUpdateConnectionStatusToAccepted() = runTest {
        // given
        val (arrangement, acceptConnectionRequestUseCase) = arrange {
            withUpdateConnectionStatus(Either.Right(CONNECTION))
            withFetchConversationSucceeding()
            withUpdateConversationModifiedDate(Either.Right(Unit))
            withPersistUnverifiedWarningMessageSuccess()
            withResolveOneOnOneConversationWithUserIdReturning(Either.Right(TestConversation.ID))
        }

        // when
        val result = acceptConnectionRequestUseCase(USER_ID)

        // then
        assertEquals(AcceptConnectionRequestUseCaseResult.Success, result)
        coVerify {
            arrangement.connectionRepository.updateConnectionStatus(
                any(),
                eq(USER_ID),
                eq(ConnectionState.ACCEPTED)
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccess_whenInvokingUseCase_thenShouldUpdateConversationModifiedDate() = runTest {
        // given
        val (arrangement, acceptConnectionRequestUseCase) = arrange {
            withUpdateConnectionStatus(Either.Right(CONNECTION))
            withFetchConversationSucceeding()
            withUpdateConversationModifiedDate(Either.Right(Unit))
            withPersistUnverifiedWarningMessageSuccess()
            withResolveOneOnOneConversationWithUserIdReturning(Either.Right(TestConversation.ID))
        }

        // when
        val result = acceptConnectionRequestUseCase(USER_ID)

        // then
        assertEquals(AcceptConnectionRequestUseCaseResult.Success, result)
        coVerify {
            arrangement.conversationRepository.updateConversationModifiedDate(eq(CONNECTION.qualifiedConversationId), any())
        }.wasInvoked(once)
    }

    @Test
    fun givenSuccess_whenInvokingUseCase_thenShouldResolveActiveOneOnOneConversation() = runTest {
        // given
        val (arrangement, acceptConnectionRequestUseCase) = arrange {
            withUpdateConnectionStatus(Either.Right(CONNECTION))
            withFetchConversationSucceeding()
            withPersistUnverifiedWarningMessageSuccess()
            withUpdateConversationModifiedDate(Either.Right(Unit))
            withResolveOneOnOneConversationWithUserIdReturning(Either.Right(TestConversation.ID))
        }

        // when
        val result = acceptConnectionRequestUseCase(USER_ID)

        // then
        assertEquals(AcceptConnectionRequestUseCaseResult.Success, result)
        coVerify {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                any(),
                eq(CONNECTION.qualifiedToId),
                eq(true)
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenFailure_whenInvokingUseCase_thenShouldReturnsAFailureResult() = runTest {
        // given
        val failure = CoreFailure.Unknown(RuntimeException("Some error"))
        val (arrangement, acceptConnectionRequestUseCase) = arrange {
            withUpdateConnectionStatus(Either.Left(failure))
        }

        // when
        val resultFailure = acceptConnectionRequestUseCase(USER_ID)

        // then
        assertEquals(AcceptConnectionRequestUseCaseResult.Failure::class, resultFailure::class)
        coVerify {
            arrangement.connectionRepository.updateConnectionStatus(
                any(),
                eq(USER_ID),
                eq(ConnectionState.ACCEPTED)
            )
        }.wasInvoked(once)
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        ConnectionRepositoryArrangement by ConnectionRepositoryArrangementImpl(),
        ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl(),
        FetchConversationUseCaseArrangement by FetchConversationUseCaseArrangementImpl(),
        NewGroupConversationSystemMessageCreatorArrangement by NewGroupConversationSystemMessageCreatorArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        suspend fun arrange() = run {
            block()
            withTransactionReturning(Either.Right(Unit))
            this@Arrangement to AcceptConnectionRequestUseCaseImpl(
                connectionRepository = connectionRepository,
                conversationRepository = conversationRepository,
                oneOnOneResolver = oneOnOneResolver,
                newGroupConversationSystemMessagesCreator = newGroupConversationSystemMessagesCreator,
                fetchConversation = fetchConversation,
                transactionProvider = cryptoTransactionProvider
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val USER_ID = UserId("some_user", "some_domain")
        val CONVERSATION_ID = ConversationId("someId", "someDomain")
        val CONNECTION = Connection(
            "someId",
            "from",
            Instant.DISTANT_PAST,
            CONVERSATION_ID,
            CONVERSATION_ID,
            ConnectionState.ACCEPTED,
            "toId",
            null
        )
    }

}
