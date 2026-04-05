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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.utils.NetworkResponse
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.leaveSubconversation(
                Arrangement.CONVERSATION_ID.toApi(),
                Arrangement.SUBCONVERSATION_ID.toApi()
            )
        }
    }

    @Test
    fun givenNotMemberOfSubconversation_whenInvokingUseCase_thenNoApiCallToRemoveSelfIsMade() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationApi.leaveSubconversation(
                Arrangement.CONVERSATION_ID.toApi(),
                Arrangement.SUBCONVERSATION_ID.toApi()
            )
        }
    }

    @Test
    fun givenMembershipIsNotKnown_whenInvokingUseCase_thenQueryMembershipFromApi() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(null)
            .withFetchingSubconversationDetails(Arrangement.SUBCONVERSATION_RESPONSE_WITH_ZERO_EPOCH)
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationApi.fetchSubconversationDetails(
                Arrangement.CONVERSATION_ID.toApi(),
                Arrangement.SUBCONVERSATION_ID.toApi()
            )
        }
    }

    @Test
    fun givenApiCallToRemoveSelfIsSuccessful_whenInvokingUseCase_thenWipeLocalMlsGroup() = runTest {
        val (arrangement, leaveSubconversation) = Arrangement()
            .withGetSubconversationInfoReturns(Arrangement.SUBCONVERSATION_GROUP_ID)
            .withLeaveSubconversationSuccessful()
            .withWipeMlsConversationSuccessful()
            .withDeleteSubconversationSuccessful()
            .arrange()

        leaveSubconversation(arrangement.mlsContext, Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.wipeConversation(Arrangement.SUBCONVERSATION_GROUP_ID.toCrypto())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.subconversationRepository.deleteSubconversation(Arrangement.CONVERSATION_ID, Arrangement.SUBCONVERSATION_ID)
        }
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationApi = mock<ConversationApi>()
        val subconversationRepository = mock<SubconversationRepository>()
        val selfClientIdProvider = mock<CurrentClientIdProvider>()

        suspend fun arrange() = this to LeaveSubconversationUseCaseImpl(
            conversationApi,
            subconversationRepository,
            TestUser.SELF.id,
            selfClientIdProvider
        ).also {
            everySuspend {
                selfClientIdProvider.invoke()
            } returns Either.Right(TestClient.CLIENT_ID)
        }

        suspend fun withFetchingSubconversationDetails(response: SubconversationResponse) = apply {
            everySuspend {
                conversationApi.fetchSubconversationDetails(mokkeryAny(), mokkeryAny())
            } returns NetworkResponse.Success(response, emptyMap(), 200)
        }

        suspend fun withLeaveSubconversationSuccessful() = apply {
            everySuspend {
                conversationApi.leaveSubconversation(mokkeryAny(), mokkeryAny())
            } returns NetworkResponse.Success(Unit, emptyMap(), 200)
        }

        suspend fun withGetSubconversationInfoReturns(groupID: GroupID?) = apply {
            everySuspend {
                subconversationRepository.getSubconversationInfo(mokkeryAny(), mokkeryAny())
            } returns groupID
        }

        suspend fun withDeleteSubconversationSuccessful() = apply {
            everySuspend {
                subconversationRepository.deleteSubconversation(mokkeryAny(), mokkeryAny())
            } returns Unit
        }

        suspend fun withWipeMlsConversationSuccessful() = apply {
            everySuspend {
                mlsContext.wipeConversation(mokkeryAny())
            } returns Unit
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
