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
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.JoinExistingMLSConversationUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.JoinExistingMLSConversationUseCaseArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSOneOnOneConversationResolverTest {

    @Test
    fun givenAUserId_whenInvokingUseCase_shouldPassCorrectUserIdWhenGettingConversationsForUser() = runTest {
        val (arrangement, getOrEstablishMlsOneToOneUseCase) = arrange {
            withConversationsForUserIdReturning(Either.Right(ALL_CONVERSATIONS))
        }

        getOrEstablishMlsOneToOneUseCase(userId)

        coVerify {
            arrangement.conversationRepository.getConversationsByUserId(eq(userId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFailureWhenGettingConversations_thenShouldPropagateFailureAndAvoidUnnecessaryCalls() = runTest {
        val cause = CoreFailure.Unknown(null)
        val (arrangement, getOrEstablishMlsOneToOneUseCase) = arrange {
            withConversationsForUserIdReturning(Either.Left(cause))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
        }

        val result = getOrEstablishMlsOneToOneUseCase(userId)

        result.shouldFail {
            assertEquals(cause, it)
        }

        coVerify {
            arrangement.conversationRepository.fetchMlsOneToOneConversation(any())
        }.wasNotInvoked()

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenOneOnOneMLSConversationAlreadyExists_thenShouldReturnIt() = runTest {
        val (_, getOrEstablishMlsOneToOneUseCase) = arrange {
            withConversationsForUserIdReturning(Either.Right(ALL_CONVERSATIONS))
        }

        val result = getOrEstablishMlsOneToOneUseCase(userId)

        result.shouldSucceed {
            assertEquals(CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED.id, it)
        }
    }

    @Test
    fun givenNoInitializedMLSAndFetchingFails_thenShouldPropagateFailure() = runTest {
        val cause = CoreFailure.Unknown(null)
        val (_, getOrEstablishMlsOneToOneUseCase) = arrange {
            withConversationsForUserIdReturning(
                Either.Right(
                    ALL_CONVERSATIONS - CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED
                )
            )
            withFetchMlsOneToOneConversation(Either.Left(cause))
        }

        val result = getOrEstablishMlsOneToOneUseCase(userId)

        result.shouldFail {
            assertEquals(cause, it)
        }
    }

    @Test
    fun givenNoInitializedMLSAndFetchingSucceeds_thenShouldJoinAndAndReturnIt() = runTest {
        val (arrangement, getOrEstablishMlsOneToOneUseCase) = arrange {
            withConversationsForUserIdReturning(
                Either.Right(
                    ALL_CONVERSATIONS - CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED
                )
            )
            withFetchMlsOneToOneConversation(Either.Right(CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED))
            withJoinExistingMLSConversationUseCaseReturning(Either.Right(Unit))
        }

        val result = getOrEstablishMlsOneToOneUseCase(userId)

        result.shouldSucceed {
            assertEquals(CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED.id, it)
        }

        coVerify {
            arrangement.joinExistingMLSConversationUseCase.invoke(any(), any())
        }.wasInvoked(exactly = once)
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        JoinExistingMLSConversationUseCaseArrangement by JoinExistingMLSConversationUseCaseArrangementImpl() {

        fun arrange() = run {
            runBlocking { block() }
            this to MLSOneOnOneConversationResolverImpl(
                conversationRepository = conversationRepository,
                joinExistingMLSConversationUseCase = joinExistingMLSConversationUseCase,
            )
        }
    }

    private companion object {
        private val userId = TestUser.USER_ID

        private val CONVERSATION_ONE_ON_ONE_PROTEUS = TestConversation.ONE_ON_ONE().copy(
            id = ConversationId("one-on-one-proteus", "test"),
            protocol = Conversation.ProtocolInfo.Proteus,
        )

        private val CONVERSATION_ONE_ON_ONE_MLS_NOT_ESTABLISHED = CONVERSATION_ONE_ON_ONE_PROTEUS.copy(
            id = ConversationId("one-on-one-mls-NOT-initialized", "test"),
            protocol = TestConversation.MLS_PROTOCOL_INFO.copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION,
                epoch = 0U
            ),
        )

        private val CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED = CONVERSATION_ONE_ON_ONE_MLS_NOT_ESTABLISHED.copy(
            id = ConversationId("one-on-one-mls-initialized", "test"),
            protocol = TestConversation.MLS_PROTOCOL_INFO.copy(
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                epoch = 0U
            ),
        )

        private val CONVERSATION_GROUP_MLS_INITIALIZED = CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED.copy(
            id = ConversationId("group-mls-initialized", "test"),
            type = Conversation.Type.GROUP
        )

        private val ALL_CONVERSATIONS = listOf(
            CONVERSATION_ONE_ON_ONE_PROTEUS,
            CONVERSATION_ONE_ON_ONE_MLS_NOT_ESTABLISHED,
            CONVERSATION_ONE_ON_ONE_MLS_ESTABLISHED,
            CONVERSATION_GROUP_MLS_INITIALIZED,
        )
    }
}
