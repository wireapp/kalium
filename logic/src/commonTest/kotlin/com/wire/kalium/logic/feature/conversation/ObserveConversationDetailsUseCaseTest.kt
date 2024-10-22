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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveConversationDetailsUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

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

            coEvery {
                conversationRepository.observeConversationDetailsById(any())
            }.returns(flowOf())

            observeConversationsUseCase(conversationId)

            coVerify {
                conversationRepository.observeConversationDetailsById(eq(conversationId))
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenTheConversationIsUpdated_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() =
        runTest(TestKaliumDispatcher.main) {
            val conversation = TestConversation.GROUP()
            val conversationDetailsValues = listOf(
                Either.Right(
                    ConversationDetails.Group(
                        conversation,
                        isSelfUserMember = true,
                        isSelfUserCreator = true,
                        selfRole = Conversation.Member.Role.Member
                    )
                ),
                Either.Right(
                    ConversationDetails.Group(
                        conversation.copy(name = "New Name"),
                        isSelfUserMember = true,
                        isSelfUserCreator = true,
                        selfRole = Conversation.Member.Role.Member
                    )
                )
            )

            coEvery {
                conversationRepository.observeConversationDetailsById(any())
            }.returns(conversationDetailsValues.asFlow())

            observeConversationsUseCase(TestConversation.ID).test {
                awaitItem().let { item ->
                    assertIs<ObserveConversationDetailsUseCase.Result.Success>(item)
                    assertEquals(conversationDetailsValues[0].value, item.conversationDetails)
                }
                awaitItem().let { item ->
                    assertIs<ObserveConversationDetailsUseCase.Result.Success>(item)
                    assertEquals(conversationDetailsValues[1].value, item.conversationDetails)
                }
                awaitComplete()
            }
        }

    @Test
    fun givenTheStorageFailure_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() = runTest(TestKaliumDispatcher.main) {
        val failure = StorageFailure.DataNotFound

        coEvery {
            conversationRepository.observeConversationDetailsById(any())
        }.returns(flowOf(Either.Left(failure)))

        observeConversationsUseCase(TestConversation.ID).test {
            awaitItem().let { item ->
                assertIs<ObserveConversationDetailsUseCase.Result.Failure>(item)
                assertEquals(failure, item.storageFailure)
            }
            awaitComplete()
        }
    }
}
