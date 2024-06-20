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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class LeaveSubconversationUseCaseTest {

    @Test
    fun givenMemberOfSubconversation_whenInvokingUseCase_thenMakeApiCallToRemoveSelf() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withLeaveSubconversationSuccessful()
            .withWipeMlsConversationSuccessful()
            .withDeleteSubconversationSuccessful()
            .arrange()

        leaveSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        coVerify {
            arrangement.conversationApi.leaveSubconversation(
                eq(Arrangement.CONVERSATION_ID.toApi()),
                eq(Arrangement.SUBCONVERSATION_ID.toApi())
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenNotMemberOfSubconversation_whenInvokingUseCase_thenNoApiCallToRemoveSelfIsMade() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        coVerify {
            arrangement.conversationApi.leaveSubconversation(
                eq(Arrangement.CONVERSATION_ID.toApi()),
                eq(Arrangement.SUBCONVERSATION_ID.toApi())
            )
        }.wasNotInvoked()
    }

    @Test
    fun givenMembershipIsNotKnown_whenInvokingUseCase_thenQueryMembershipFromApi() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        coVerify {
            arrangement.conversationApi.fetchSubconversationDetails(
                eq(Arrangement.CONVERSATION_ID.toApi()),
                eq(Arrangement.SUBCONVERSATION_ID.toApi())
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiCallToRemoveSelfIsSuccessful_whenInvokingUseCase_thenWipeLocalMlsGroup() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withLeaveSubconversationSuccessful()
            .withWipeMlsConversationSuccessful()
            .withDeleteSubconversationSuccessful()
            .arrange()

        leaveSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        coVerify {
            arrangement.mlsClient.wipeConversation(eq(Arrangement.SUBCONVERSATION_GROUP_ID.toCrypto()))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.subconversationRepository.deleteSubconversation(eq(Arrangement.CONVERSATION_ID), eq(Arrangement.SUBCONVERSATION_ID))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationApi = mock(ConversationApi::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        val subconversationRepository = mock(SubconversationRepository::class)

        @Mock
        val selfClientIdProvider = mock(CurrentClientIdProvider::class)

        suspend fun arrange() = this to LeaveSubconversationUseCaseImpl(
            conversationApi,
            mlsClientProvider,
            subconversationRepository,
            TestUser.SELF.id,
            selfClientIdProvider
        ).also {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))

            coEvery {
                selfClientIdProvider.invoke()
            }.returns(Either.Right(TestClient.CLIENT_ID))
        }

        suspend fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            coEvery {
                conversationApi.fetchSubconversationDetails(any(), any())
            }.returns(NetworkResponse.Success(response, emptyMap(), 200))
        }

        suspend fun withLeaveSubconversationSuccessful() = apply {
            coEvery {
                conversationApi.leaveSubconversation(any(), any())
            }.returns(NetworkResponse.Success(Unit, emptyMap(), 200))
        }

        suspend fun withGetSubconversationInfoReturns(groupID: GroupID?) = apply {
            coEvery {
                subconversationRepository.getSubconversationInfo(any(), any())
            }.returns(groupID)
        }

        suspend fun withDeleteSubconversationSuccessful() = apply {
            coEvery {
                subconversationRepository.deleteSubconversation(any(), any())
            }.returns(Unit)
        }

        suspend fun withWipeMlsConversationSuccessful() = apply {
            coEvery {
                mlsClient.wipeConversation(any())
            }.returns(Unit)
        }

        companion object {
            val SUBCONVERSATION_GROUP_ID = GroupID("group_id")
            val CONVERSATION_ID = ConversationId("id1", "domain")
            val SUBCONVERSATION_ID = SubconversationId("subconversation_id")
            val SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH = SubconversationResponse(
                SUBCONVERSATION_ID.toApi(),
                QualifiedID(CONVERSATION_ID.value, CONVERSATION_ID.domain),
                SUBCONVERSATION_GROUP_ID.value,
                0UL,
                null,
                0,
                emptyList()
            )
        }
    }
}
