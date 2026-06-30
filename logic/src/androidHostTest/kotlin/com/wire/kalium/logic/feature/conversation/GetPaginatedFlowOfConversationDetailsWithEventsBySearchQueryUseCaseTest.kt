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
    fun givenOngoingCalls_whenGettingPaginatedList_thenCallUseCaseWithOngoingConversationIds() =
        runTest(dispatcher.default) {
            // Given
            val ongoingCallConversationId = ConversationId("ongoing", "domain")
            val (arrangement, useCase) = Arrangement()
                .withOngoingCallsFlow(flowOf(listOf(TestCall.groupIncomingCall(ongoingCallConversationId))))
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
                        listOf(ongoingCallConversationId)
                    )
                }
            }
        }

    @Test
    fun givenSameOngoingCallIdsInDifferentOrder_whenGettingPaginatedList_thenPagerIsNotRecreated() =
        runTest(dispatcher.default) {
            // Given
            val firstOngoingCallConversationId = ConversationId("ongoing-1", "domain")
            val secondOngoingCallConversationId = ConversationId("ongoing-2", "domain")
            val (arrangement, useCase) = Arrangement()
                .withOngoingCallsFlow(
                    flowOf(
                        listOf(
                            TestCall.groupIncomingCall(firstOngoingCallConversationId),
                            TestCall.groupIncomingCall(secondOngoingCallConversationId)
                        ),
                        listOf(
                            TestCall.groupIncomingCall(secondOngoingCallConversationId),
                            TestCall.groupIncomingCall(firstOngoingCallConversationId)
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
                callRepository.ongoingCallsFlow()
            }.returns(flowOf(emptyList()))
        }

        fun withOngoingCallsFlow(result: Flow<List<Call>>) = apply {
            every {
                callRepository.ongoingCallsFlow()
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
