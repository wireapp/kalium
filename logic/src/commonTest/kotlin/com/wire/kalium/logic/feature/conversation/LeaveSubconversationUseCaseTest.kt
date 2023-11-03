/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::leaveSubconversation)
            .with(eq(Arrangement.CONVERSATION_ID.toApi()), eq(Arrangement.SUBCONVERSATION_ID.toApi()))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNotMemberOfSubconversation_whenInvokingUseCase_thenNoApiCallToRemoveSelfIsMade() = runTest  {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::leaveSubconversation)
            .with(eq(Arrangement.CONVERSATION_ID.toApi()), eq(Arrangement.SUBCONVERSATION_ID.toApi()))
            .wasNotInvoked()
    }

    @Test
    fun givenMembershipIsNotKnown_whenInvokingUseCase_thenQueryMembershipFromApi() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verify(arrangement.conversationApi)
            .suspendFunction(arrangement.conversationApi::fetchSubconversationDetails)
            .with(eq(Arrangement.CONVERSATION_ID.toApi()), eq(Arrangement.SUBCONVERSATION_ID.toApi()))
            .wasInvoked(exactly = once)
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

        verify(arrangement.mlsClient)
            .function(arrangement.mlsClient::wipeConversation)
            .with(eq(Arrangement.SUBCONVERSATION_GROUP_ID.toCrypto()))
            .wasInvoked(exactly = once)

        verify(arrangement.subconversationRepository)
            .function(arrangement.subconversationRepository::deleteSubconversation)
            .with(eq(Arrangement.CONVERSATION_ID), eq(Arrangement.SUBCONVERSATION_ID))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationApi = mock(classOf<ConversationApi>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val selfClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        init {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(mlsClient))

            given(selfClientIdProvider)
                .suspendFunction(selfClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
        }

        fun arrange() = this to LeaveSubconversationUseCaseImpl(
            conversationApi,
            mlsClientProvider,
            subconversationRepository,
            TestUser.SELF.id,
            selfClientIdProvider
        )

        fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::fetchSubconversationDetails)
                .whenInvokedWith(anything(), anything())
                .thenReturn(NetworkResponse.Success(response, emptyMap(), 200))
        }

        fun withLeaveSubconversationSuccessful() = apply {
            given(conversationApi)
                .suspendFunction(conversationApi::leaveSubconversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(NetworkResponse.Success(Unit, emptyMap(), 200))
        }

        fun withGetSubconversationInfoReturns(groupID: GroupID?) = apply {
            given(subconversationRepository)
                .suspendFunction(subconversationRepository::getSubconversationInfo)
                .whenInvokedWith(anything(), anything())
                .thenReturn(groupID)
        }

        fun withDeleteSubconversationSuccessful() = apply {
            given(subconversationRepository)
                .suspendFunction(subconversationRepository::deleteSubconversation)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Unit)
        }

        fun withWipeMlsConversationSuccessful() = apply {
            given(mlsClient)
                .suspendFunction(mlsClient::wipeConversation)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
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
