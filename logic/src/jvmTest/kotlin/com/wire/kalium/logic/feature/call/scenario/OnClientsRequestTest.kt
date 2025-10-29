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
package com.wire.kalium.logic.feature.call.scenario

import com.wire.kalium.calling.types.Handle
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapperImpl
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.framework.TestCall.oneOnOneCallMetadata
import com.wire.kalium.logic.framework.TestUser
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import kotlin.apply
import kotlin.to
import kotlin.toULong

@OptIn(ExperimentalCoroutinesApi::class)
class OnClientsRequestTest {

    val testScope = TestScope()

    @Test
    fun givenOngoingCall_whenClientsRequested_thenCallClientsInCallUpdater() = testScope.runTest() {
        val (arrangement, callback) = Arrangement(testScope)
            .withCallMetadata(conversationId, oneOnOneCallMetadata())
            .arrange()

        callback.onClientsRequest(inst = handle, conversationId = conversationId.toString(), arg = null)
        advanceUntilIdle()

        coVerify {
            arrangement.conversationClientsInCallUpdater(conversationId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOngoingMLSCall_whenClientsRequested_thenCallEpochInfoUpdate() = testScope.runTest() {
        val callMetadata = oneOnOneCallMetadata().copy(
            protocol = Conversation.ProtocolInfo.MLS(
                groupId = GroupID(""),
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                epoch = ULong.MAX_VALUE,
                keyingMaterialLastUpdate = Instant.DISTANT_FUTURE,
                cipherSuite = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            )
        )
        val (arrangement, callback) = Arrangement(testScope)
            .withCallMetadata(conversationId, callMetadata)
            .withEpochInfo(conversationId, epochInfo)
            .arrange()

        callback.onClientsRequest(inst = handle, conversationId = conversationId.toString(), arg = null)
        advanceUntilIdle()

        coVerify {
            arrangement.epochInfoUpdater.updateEpochInfo(conversationId, epochInfo)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenOngoingNonMLSCall_whenClientsRequested_thenDoNotCallEpochInfoUpdate() = testScope.runTest() {
        val callMetadata = oneOnOneCallMetadata().copy(
            protocol = Conversation.ProtocolInfo.Proteus
        )
        val (arrangement, callback) = Arrangement(testScope)
            .withCallMetadata(conversationId, callMetadata)
            .withEpochInfo(conversationId, epochInfo)
            .arrange()

        callback.onClientsRequest(inst = handle, conversationId = conversationId.toString(), arg = null)
        advanceUntilIdle()

        coVerify {
            arrangement.epochInfoUpdater.updateEpochInfo(conversationId, epochInfo)
        }.wasNotInvoked()
    }

    private class Arrangement(val testScope: TestScope) {
        val callRepository: CallRepository = mock(CallRepository::class)
        val conversationClientsInCallUpdater: ConversationClientsInCallUpdater = mock(ConversationClientsInCallUpdater::class)
        val epochInfoUpdater: EpochInfoUpdater = mock(EpochInfoUpdater::class)
        val qualifiedIdMapper = QualifiedIdMapperImpl(TestUser.SELF.id)

        fun withCallMetadata(id: ConversationId, metadata: CallMetadata): Arrangement = apply {
            every {
                callRepository.getCallMetadata(id)
            }.returns(metadata)
        }

        suspend fun withEpochInfo(id: ConversationId, epochInfo: EpochInfo): Arrangement = apply {
            coEvery {
                callRepository.observeEpochInfo(id)
            }.returns(flowOf(epochInfo).right())
        }

        fun arrange() = this to OnClientsRequest(
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            epochInfoUpdater = epochInfoUpdater,
            qualifiedIdMapper = qualifiedIdMapper,
            callingScope = testScope,
            callRepository = callRepository,
        )
    }

    companion object {
        val handle = Handle(42)
        val conversationId = MockConversation.ID
        val epochInfo = EpochInfo(epoch = 1.toULong(), members = CallClientList(emptyList()), sharedSecret = byteArrayOf())
    }
}
