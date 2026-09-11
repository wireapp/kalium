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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Suppress("MaxLineLength")
class GetOrCreateOneToOneConversationUseCaseTest {

    @Test
    fun givenConversationIsReady_whenCallingTheUseCase_thenReturnExistingConversation() = runTest {
        val (arrangement, useCase) = arrange {
            withReadinessResult(CheckOneToOneConversationIsReadyUseCase.Result.Ready(CONVERSATION))
        }

        val result = useCase.invoke(OTHER_USER_ID)

        assertIs<CreateConversationResult.Success>(result)

        verifySuspend(VerifyMode.not) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any(), any())
        }
    }

    @Test
    fun givenReadinessFailure_whenCallingTheUseCase_thenErrorIsPropagatedWithoutResolving() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (arrangement, useCase) = arrange {
            withReadinessResult(CheckOneToOneConversationIsReadyUseCase.Result.Failure(failure))
        }

        val result = useCase.invoke(OTHER_USER_ID)

        assertIs<CreateConversationResult.Failure>(result)
        assertEquals(failure, result.coreFailure)
        verifySuspend(VerifyMode.not) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any(), any())
        }
    }

    @Test
    fun givenConversationIsNotReadyAndResolvingFails_whenCallingTheUseCase_thenErrorIsPropagated() = runTest {
        val (_, useCase) = arrange {
            withReadinessResult(CheckOneToOneConversationIsReadyUseCase.Result.NotReady)
            withUserByIdReturning(OTHER_USER.right())
            withResolveOneOnOneConversationWithUserReturning(Either.Left(CoreFailure.NoCommonProtocolFound.SelfNeedToUpdate))
        }

        val result = useCase.invoke(OTHER_USER_ID)

        assertIs<CreateConversationResult.Failure>(result)
    }

    @Test
    fun givenFailureWhileGettingUser_whenCallingTheUseCase_ThenErrorIsPropagated() = runTest {
        val (_, useCase) = arrange {
            withReadinessResult(CheckOneToOneConversationIsReadyUseCase.Result.NotReady)
            withUserByIdReturning(Either.Left(StorageFailure.DataNotFound))
        }

        val result = useCase.invoke(OTHER_USER_ID)

        assertIs<CreateConversationResult.Failure>(result)
    }

    @Test
    fun givenConversationIsNotReady_whenCallingTheUseCase_thenResolveOneOnOneConversation() = runTest {
        val (arrangement, useCase) = arrange {
            withReadinessResult(CheckOneToOneConversationIsReadyUseCase.Result.NotReady)
            withUserByIdReturning(OTHER_USER.right())
            withResolveOneOnOneConversationWithUserReturning(Either.Right(CONVERSATION.id))
            withConversationDetailsByIdReturning(CONVERSATION.right())
        }

        val result = useCase.invoke(OTHER_USER_ID)

        assertIs<CreateConversationResult.Success>(result)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), eq(OTHER_USER), any(), eq(true))
        }
    }

    @Test
    fun givenConversationIsNotReadyAndKeyPackagesAreMissing_whenCallingTheUseCase_thenPropagateMissingKeyPackages() = runTest {
        val failure = CoreFailure.MissingKeyPackages(setOf(OTHER_USER_ID))
        val (_, useCase) = arrange {
            withReadinessResult(CheckOneToOneConversationIsReadyUseCase.Result.NotReady)
            withUserByIdReturning(OTHER_USER.right())
            withResolveOneOnOneConversationWithUserReturning(Either.Left(failure))
        }

        val result = useCase.invoke(OTHER_USER_ID)

        assertIs<CreateConversationResult.Failure>(result)
        assertEquals(failure, result.coreFailure)
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    internal class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val oneOnOneResolver = mock<OneOnOneResolver>(mode = MockMode.autoUnit)
        val checkOneToOneConversationIsReady = mock<CheckOneToOneConversationIsReadyUseCase>(mode = MockMode.autoUnit)

        suspend fun arrange() = run {
            withTransactionReturning(Either.Right(Unit))
            block()
            this@Arrangement to GetOrCreateOneToOneConversationUseCaseImpl(
                conversationRepository = conversationRepository,
                userRepository = userRepository,
                oneOnOneResolver = oneOnOneResolver,
                transactionProvider = cryptoTransactionProvider,
                checkOneToOneConversationIsReady = checkOneToOneConversationIsReady,
            )
        }

        suspend fun withReadinessResult(result: CheckOneToOneConversationIsReadyUseCase.Result) {
            everySuspend { checkOneToOneConversationIsReady(any()) } returns result
        }

        suspend fun withUserByIdReturning(result: Either<CoreFailure, com.wire.kalium.logic.data.user.OtherUser>) {
            everySuspend {
                userRepository.userById(any())
            } returns result
        }

        suspend fun withResolveOneOnOneConversationWithUserReturning(result: Either<CoreFailure, com.wire.kalium.logic.data.id.ConversationId>) {
            everySuspend {
                oneOnOneResolver.resolveOneOnOneConversationWithUser(any(), any(), any(), eq(true))
            } returns result
        }

        suspend fun withConversationDetailsByIdReturning(result: Either<StorageFailure, com.wire.kalium.logic.data.conversation.Conversation>) {
            everySuspend {
                conversationRepository.getConversationById(any())
            } returns result
        }
    }

    private companion object {
        val OTHER_USER = TestUser.OTHER
        val OTHER_USER_ID = OTHER_USER.id
        val CONVERSATION = TestConversation.ONE_ON_ONE()
    }
}
