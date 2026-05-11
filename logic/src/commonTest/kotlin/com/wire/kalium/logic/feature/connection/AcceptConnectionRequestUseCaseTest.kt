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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.NewGroupConversationSystemMessagesCreator
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.matcher.eq as mokkeryEq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.connectionRepository.updateConnectionStatus(
                mokkeryAny(),
                mokkeryEq(USER_ID),
                mokkeryEq(ConnectionState.ACCEPTED)
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.updateConversationModifiedDate(mokkeryEq(CONNECTION.qualifiedConversationId), mokkeryAny())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUserId(
                mokkeryAny(),
                mokkeryEq(CONNECTION.qualifiedToId),
                mokkeryEq(true)
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.connectionRepository.updateConnectionStatus(
                mokkeryAny(),
                mokkeryEq(USER_ID),
                mokkeryEq(ConnectionState.ACCEPTED)
            )
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val connectionRepository = mock<ConnectionRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val oneOnOneResolver = mock<OneOnOneResolver>(mode = MockMode.autoUnit)
        val fetchConversation = mock<FetchConversationUseCase>(mode = MockMode.autoUnit)
        val newGroupConversationSystemMessagesCreator = mock<NewGroupConversationSystemMessagesCreator>(mode = MockMode.autoUnit)

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

        suspend fun withUpdateConnectionStatus(result: Either<CoreFailure, Connection>) {
            everySuspend {
                connectionRepository.updateConnectionStatus(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns result
        }

        suspend fun withFetchConversationSucceeding() {
            everySuspend {
                fetchConversation(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateConversationModifiedDate(result: Either<StorageFailure, Unit>) {
            everySuspend {
                conversationRepository.updateConversationModifiedDate(mokkeryAny(), mokkeryAny())
            } returns result
        }

        suspend fun withPersistUnverifiedWarningMessageSuccess() {
            everySuspend {
                newGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning(mokkeryAny(), mokkeryAny())
            } returns Either.Right(Unit)
        }

        suspend fun withResolveOneOnOneConversationWithUserIdReturning(result: Either<CoreFailure, ConversationId>) {
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUserId(mokkeryAny(), mokkeryAny(), mokkeryAny())
            } returns result
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
