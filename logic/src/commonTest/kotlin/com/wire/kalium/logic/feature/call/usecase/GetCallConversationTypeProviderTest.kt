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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.mock
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
                type = Conversation.Type.Group.Regular,
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
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenOneOnOneConversationWithSFTDisabled_whenGettingCallType_thenReturnOneOnOne() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.OneOnOne,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
            withShouldNotUseSFTForOneOnOneCalls()
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.OneOnOne, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenProteusOneOnOneConversationWithSFTEnabled_whenGettingCallType_thenReturnConference() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.OneOnOne,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
            withShouldUseSFTForOneOnOneCalls()
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Conference, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenSelfConversation_whenGettingCallType_thenReturnUnknown() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.Self,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Unknown, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenOneOnOneConversationWithSFTEnabledButSFTCheckFails_whenGettingCallType_thenReturnUnknown() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.OneOnOne,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
            withSFTCheckFailure()
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Unknown, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenConnectionPendingConversation_whenGettingCallType_thenReturnUnknown() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.ConnectionPending,
                protocolInfo = Conversation.ProtocolInfo.Proteus
            )
        }

        // When
        val result = provider(ConversationId("some-id", "some-domain"))

        // Then
        assertEquals(ConversationTypeCalling.Unknown, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenGroupConversationWithMixedProtocol_whenGettingCallType_thenReturnConference() = runTest {
        // Given
        val (arrangement, provider) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.Group.Regular,
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
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any()) }
        verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.shouldUseSFTForOneOnOneCalls() }
    }

    @Test
    fun givenGroupConversationWithProteusProtocol_whenGettingCallType_thenReturnConference() = runTest {
        val (arrangement, getCallConversationType) = arrange {
            withGetConversationTypeAndProtocolInfoSuccess(
                type = Conversation.Type.Group.Regular,
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
                type = Conversation.Type.Group.Channel,
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
                type = Conversation.Type.OneOnOne,
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
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val conversationMetaDataRepository = mock<ConversationMetaDataRepository>(mode = MockMode.autoUnit)

        private val getCallConversationType = GetCallConversationTypeProviderImpl(
            userConfigRepository = userConfigRepository,
            conversationMetaDataRepository = conversationMetaDataRepository
        )

        suspend fun withShouldUseSFTForOneOnOneCalls() = apply {
            everySuspend { userConfigRepository.shouldUseSFTForOneOnOneCalls() } returns (Either.Right(true))
        }

        suspend fun withShouldNotUseSFTForOneOnOneCalls() = apply {
            everySuspend { userConfigRepository.shouldUseSFTForOneOnOneCalls() } returns (Either.Right(false))
        }

        suspend fun withGetConversationTypeAndProtocolInfoSuccess(
            type: Conversation.Type,
            protocolInfo: Conversation.ProtocolInfo
        ) = apply {
            everySuspend {
                conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any())
            } returns (Either.Right(Pair(type, protocolInfo)))
        }

        suspend fun withSFTCheckFailure() = apply {
            everySuspend {
                userConfigRepository.shouldUseSFTForOneOnOneCalls()
            } returns (Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withGetConversationTypeAndProtocolInfoFailure(result: Either.Left<StorageFailure>) {
            everySuspend {
                conversationMetaDataRepository.getConversationTypeAndProtocolInfo(any())
            } returns (result)
        }


        fun arrange() = this to getCallConversationType
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement().apply { runBlocking { block() } }.arrange()
}
