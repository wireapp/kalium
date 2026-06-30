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

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveConversationDetailsUseCaseTest {

    private val conversationRepository: ConversationRepository = mock()

    private lateinit var observeConversationsUseCase: ObserveConversationDetailsUseCase

    @BeforeTest
    fun setup() {
        observeConversationsUseCase = ObserveConversationDetailsUseCase(
            conversationRepository,
            TestKaliumDispatcher
        )
    }

    @Test
    fun givenAConversationId_whenObservingConversationUseCase_thenTheConversationRepositoryShouldBeCalledWithTheCorrectID() =
        runTest(TestKaliumDispatcher.main) {
            val conversationId = TestConversation.ID

            everySuspend {
                conversationRepository.observeConversationDetailsById(any())
            } returns flowOf()

            observeConversationsUseCase(conversationId)

            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.observeConversationDetailsById(eq(conversationId))
            }
        }

    @Test
    fun givenTheConversationIsUpdated_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() =
        runTest(TestKaliumDispatcher.main) {
            val conversation = TestConversation.GROUP()
            val conversationDetailsValues = listOf(
                Either.Right(
                    ConversationDetails.Group.Regular(
                        conversation,
                        isSelfUserMember = true,
                        selfRole = Conversation.Member.Role.Member
                    )
                ),
                Either.Right(
                    ConversationDetails.Group.Regular(
                        conversation.copy(name = "New Name"),
                        isSelfUserMember = true,
                        selfRole = Conversation.Member.Role.Member
                    )
                )
            )
            val conversationDetailsUpdates = Channel<Either<StorageFailure, ConversationDetails>>(Channel.UNLIMITED)

            everySuspend {
                conversationRepository.observeConversationDetailsById(any())
            } returns conversationDetailsUpdates.consumeAsFlow()

            observeConversationsUseCase(TestConversation.ID).test {
                conversationDetailsUpdates.send(conversationDetailsValues[0])
                awaitItem().let { item ->
                    assertIs<ObserveConversationDetailsUseCase.Result.Success>(item)
                    assertEquals(conversationDetailsValues[0].value, item.conversationDetails)
                }
                conversationDetailsUpdates.send(conversationDetailsValues[1])
                awaitItem().let { item ->
                    assertIs<ObserveConversationDetailsUseCase.Result.Success>(item)
                    assertEquals(conversationDetailsValues[1].value, item.conversationDetails)
                }
                conversationDetailsUpdates.close()
                awaitComplete()
            }
        }

    @Test
    fun givenTheStorageFailure_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() = runTest(TestKaliumDispatcher.main) {
        val failure = StorageFailure.DataNotFound

        everySuspend {
            conversationRepository.observeConversationDetailsById(any())
        } returns flowOf(Either.Left(failure))

        observeConversationsUseCase(TestConversation.ID).test {
            awaitItem().let { item ->
                assertIs<ObserveConversationDetailsUseCase.Result.Failure>(item)
                assertEquals(failure, item.storageFailure)
            }
            awaitComplete()
        }
    }
}
