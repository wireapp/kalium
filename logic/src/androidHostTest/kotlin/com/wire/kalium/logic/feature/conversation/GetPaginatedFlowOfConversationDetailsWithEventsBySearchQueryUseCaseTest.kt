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

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationQueryConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationRepositoryExtensions
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

internal class GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCaseTest {
    private val dispatcher = TestKaliumDispatcher

    @Test
    fun givenSearchQuery_whenGettingPaginatedList_thenCallUseCaseWithProperParams() = runTest(dispatcher.default) {
        // Given
        val (arrangement, useCase) = Arrangement().withPaginatedConversationResult(flowOf(PagingData.empty())).arrange()
        with(arrangement) {
            // When
            useCase(
                queryConfig = queryConfig,
                pagingConfig = pagingConfig,
                startingOffset = startingOffset,
                strictMlsFilter = false
            ).first()
            // Then
            verifySuspend(VerifyMode.exactly(1)) {
                conversationRepository.extensions
                    .getPaginatedConversationDetailsWithEventsBySearchQuery(
                        queryConfig,
                        pagingConfig,
                        startingOffset,
                        false,
                        emptyList()
                    )
            }
        }
    }

    @Test
    fun givenJoinableCalls_whenGettingPaginatedList_thenCallUseCaseWithJoinableConversationIds() =
        runTest(dispatcher.default) {
            // Given
            val joinableCallConversationId = ConversationId("joinable", "domain")
            val (arrangement, useCase) = Arrangement()
                .withJoinableCallsFlow(
                    flowOf(mapOf(joinableCallConversationId to TestCall.groupIncomingCall(joinableCallConversationId)))
                )
                .withPaginatedConversationResult(flowOf(PagingData.empty()))
                .arrange()

            with(arrangement) {
                // When
                useCase(
                    queryConfig = queryConfig,
                    pagingConfig = pagingConfig,
                    startingOffset = startingOffset,
                    strictMlsFilter = false
                ).first()

                // Then
                verifySuspend(VerifyMode.exactly(1)) {
                    conversationRepository.extensions.getPaginatedConversationDetailsWithEventsBySearchQuery(
                        queryConfig,
                        pagingConfig,
                        startingOffset,
                        false,
                        listOf(joinableCallConversationId)
                    )
                }
            }
        }

    @Test
    fun givenSameJoinableCallIdsInDifferentOrder_whenGettingPaginatedList_thenPagerIsNotRecreated() =
        runTest(dispatcher.default) {
            // Given
            val firstJoinableCallConversationId = ConversationId("joinable-1", "domain")
            val secondJoinableCallConversationId = ConversationId("joinable-2", "domain")
            val (arrangement, useCase) = Arrangement()
                .withJoinableCallsFlow(
                    flowOf(
                        linkedMapOf(
                            firstJoinableCallConversationId to TestCall.groupIncomingCall(firstJoinableCallConversationId),
                            secondJoinableCallConversationId to TestCall.groupIncomingCall(secondJoinableCallConversationId)
                        ),
                        linkedMapOf(
                            secondJoinableCallConversationId to TestCall.groupIncomingCall(secondJoinableCallConversationId),
                            firstJoinableCallConversationId to TestCall.groupIncomingCall(firstJoinableCallConversationId)
                        )
                    )
                )
                .withPaginatedConversationResult(flowOf(PagingData.empty()))
                .arrange()

            // When
            useCase(
                queryConfig = arrangement.queryConfig,
                pagingConfig = arrangement.pagingConfig,
                startingOffset = arrangement.startingOffset,
                strictMlsFilter = false
            ).toList()

            // Then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.extensions.getPaginatedConversationDetailsWithEventsBySearchQuery(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
        }

    inner class Arrangement {
        val callRepository = mock<CallRepository>()
        val conversationRepository = mock<ConversationRepository>()
        val conversationRepositoryExtensions = mock<ConversationRepositoryExtensions>()

        val queryConfig = ConversationQueryConfig("search")
        val pagingConfig = PagingConfig(20)
        val startingOffset = 0L

        init {
            every {
                conversationRepository.extensions
            }.returns(conversationRepositoryExtensions)
            every {
                callRepository.joinableCallsByConversationIdFlow()
            }.returns(flowOf(emptyMap()))
        }

        fun withJoinableCallsFlow(result: Flow<Map<ConversationId, Call>>) = apply {
            every {
                callRepository.joinableCallsByConversationIdFlow()
            }.returns(result)
        }

        suspend fun withPaginatedConversationResult(result: Flow<PagingData<ConversationDetailsWithEvents>>) = apply {
            everySuspend {
                conversationRepositoryExtensions.getPaginatedConversationDetailsWithEventsBySearchQuery(
                    any(),
                    any(),
                    any(),
                    any(),
                    any()
                )
            } returns result
        }

        fun arrange() = this to GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase(
            dispatcher,
            conversationRepository,
            callRepository
        )
    }
}
