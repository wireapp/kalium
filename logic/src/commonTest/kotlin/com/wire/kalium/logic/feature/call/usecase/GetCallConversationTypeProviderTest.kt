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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationMetaDataRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetCallConversationTypeProviderTest {

    @Test
    fun givenGroupConversationWithMLSProtocol_whenGettingCallType_thenReturnConferenceMls() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.GROUP,
                protocolInfo = Conversation.ProtocolInfo.MLS(
                    groupId = GroupID("groupId"),
                    groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                    epoch = 1.toULong(),
                    cipherSuite = CipherSuite.Companion.fromTag(1),
                    keyingMaterialLastUpdate = kotlinx.datetime.Instant.DISTANT_PAST
                )
            )
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.ConferenceMls, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasNotInvoked()
    }

    @Test
    fun givenOneOnOneConversationWithSFTDisabled_whenGettingCallType_thenReturnOneOnOne() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.ONE_ON_ONE,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
            withShouldNotUseSFTForOneOnOneCalls()
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.OneOnOne, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusOneOnOneConversationWithSFTEnabled_whenGettingCallType_thenReturnConference() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.ONE_ON_ONE,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
            withShouldUseSFTForOneOnOneCalls()
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Conference, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfConversation_whenGettingCallType_thenReturnUnknown() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.SELF,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Unknown, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasNotInvoked()
    }

    @Test
    fun givenOneOnOneConversationWithSFTEnabledButSFTCheckFails_whenGettingCallType_thenReturnUnknown() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.ONE_ON_ONE,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
            withSFTCheckFailure()
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Unknown, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenConnectionPendingConversation_whenGettingCallType_thenReturnUnknown() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.CONNECTION_PENDING,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Unknown, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasNotInvoked()
    }

    @Test
    fun givenGroupConversationWithMixedProtocol_whenGettingCallType_thenReturnConference() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.GROUP,
                protocolInfo = Conversation.ProtocolInfo.Mixed(
                    groupId = GroupID("groupId"),
                    groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                    epoch = 1.toULong(),
                    cipherSuite = CipherSuite.Companion.fromTag(1),
                    keyingMaterialLastUpdate = kotlinx.datetime.Instant.DISTANT_PAST
                )
            )
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Conference, result)
        coVerify { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }.wasInvoked(exactly = once)
        coVerify { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }.wasNotInvoked()
    }

    @Test
    fun givenGroupConversationWithProteusProtocol_whenGettingCallType_thenReturnConference() = runTest {
        val (arrangement, getCallConversationType) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.GROUP,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
        }

        val result = getCallConversationType(ConversationId("some-id", "some-domain"))

        assertEquals(ConversationTypeCalling.Conference, result)
    }

    @Test
    fun givenChannelWithProteusProtocol_whenGettingCallType_thenReturnConference() = runTest {
        val (arrangement, getCallConversationType) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.GROUP,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
        }

        val result = getCallConversationType(ConversationId("some-id", "some-domain"))

        assertEquals(ConversationTypeCalling.Conference, result)
    }

    @Test
    fun givenOneOnOneMLSConversationWithSFTEnabled_whenGettingCallType_thenReturnConferenceMls() = runTest {
        val (arrangement, getCallConversationType) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.ONE_ON_ONE,
                protocolInfo = Conversation.ProtocolInfo.MLS(
                    groupId = GroupID("groupId"),
                    groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                    epoch = 1.toULong(),
                    cipherSuite = CipherSuite.Companion.fromTag(1),
                    keyingMaterialLastUpdate = kotlinx.datetime.Instant.DISTANT_PAST
                )
            )
            withShouldUseSFTForOneOnOneCalls()
        }

        val result = getCallConversationType(ConversationId("some-id", "some-domain"))

        assertEquals(ConversationTypeCalling.ConferenceMls, result)
    }

    @Test
    fun givenGetConversationTypeAndProtocolInfoFails_whenGettingCallType_thenReturnUnknown() = runTest {
        val (arrangement, getCallConversationType) = arrange {
            withGetConversationTypeAndProtocolInfoFailure(Either.Left(StorageFailure.DataNotFound))
        }

        val result = getCallConversationType(ConversationId("some-id", "some-domain"))

        assertEquals(ConversationTypeCalling.Unknown, result)
    }


    private class Arrangement {
        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val conversationMetaDataRepository = mock(ConversationMetaDataRepository::class)

        private val getCallConversationType = GetCallConversationTypeProviderImpl(
            userConfigRepository = userConfigRepository,
            conversationMetaDataRepository = conversationMetaDataRepository
        )

        suspend fun withShouldUseSFTForOneOnOneCalls() = apply {
            coEvery { userConfigRepository.shouldUseSFTForOneOnOneCalls() }.returns(Either.Right(true))
        }

        suspend fun withShouldNotUseSFTForOneOnOneCalls() = apply {
            coEvery { userConfigRepository.shouldUseSFTForOneOnOneCalls() }.returns(Either.Right(false))
        }

        suspend fun withGetConversationTypeAndProtocolInfoSuccess(
            type: Conversation.Type,
            protocolInfo: Conversation.ProtocolInfo
        ) = apply {
            coEvery {
                conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any())
            }.returns(Either.Right(Pair(type, protocolInfo)))
        }

        suspend fun withSFTCheckFailure() = apply {
            coEvery {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withGetConversationTypeAndProtocolInfoFailure(result: Either.Left<StorageFailure>) {
            coEvery {
                conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any())
            }.returns(result)
        }


        fun arrange() = this to getCallConversationType
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement().apply { runBlocking { block() } }.arrange()
}
