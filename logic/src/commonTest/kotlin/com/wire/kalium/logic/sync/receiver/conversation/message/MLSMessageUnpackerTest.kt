package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class MLSMessageUnpackerTest {

    @Test
    fun givenNewMLSMessageEventWithProposal_whenUnpacking_thenScheduleProposalTimer() = runTest {
        val eventTimestamp = Clock.System.now()
        val commitDelay: Long = 10

        val (arrangement, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversation(TestConversation.MLS_CONVERSATION)
            .withDecryptMessageReturningProposal(commitDelay)
            .withScheduleCommitSucceeding()
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        mlsUnpacker.unpackMlsMessage(messageEvent)

        verify(arrangement.pendingProposalScheduler)
            .suspendFunction(arrangement.pendingProposalScheduler::scheduleCommit)
            .with(eq(TestConversation.GROUP_ID), eq(eventTimestamp.plus(commitDelay.seconds)))
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val pendingProposalScheduler = mock(classOf<PendingProposalScheduler>())

        @Mock
        val selfUserId = mock(classOf<UserId>())

        @Mock
        val protoContentMapper = mock(classOf<ProtoContentMapper>())

        private val mlsMessageUnpacker = MLSMessageUnpackerImpl(
            mlsClientProvider,
            conversationRepository,
            pendingProposalScheduler,
            selfUserId,
            protoContentMapper
        )

        fun withMLSClientProviderReturningClient() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
        }

        fun withDecryptMessageReturningProposal(commitDelay: Long = 15) = apply {
            given(mlsClient)
                .function(mlsClient::decryptMessage)
                .whenInvokedWith(anything<String>(), anything<ByteArray>())
                .thenReturn(DecryptedMessageBundle(null, commitDelay, null))
        }

        fun withScheduleCommitSucceeding() = apply {
            given(pendingProposalScheduler)
                .suspendFunction(pendingProposalScheduler::scheduleCommit)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withProtoContentMapperReturning(plainBlobMatcher: Matcher<PlainMessageBlob>, protoContent: ProtoContent) = apply {
            given(protoContentMapper)
                .function(protoContentMapper::decodeFromProtobuf)
                .whenInvokedWith(plainBlobMatcher)
                .thenReturn(protoContent)
        }

        fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationById)
                .whenInvokedWith(any())
                .thenReturn(conversation)
        }

        fun arrange() = this to mlsMessageUnpacker

    }
}
