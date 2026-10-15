/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ApplicationMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapperImpl
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.MessageUnpackResult
import com.wire.kalium.logic.sync.receiver.conversation.message.NewMessageEventHandlerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.ProteusMessageUnpackerImpl
import com.wire.kalium.logic.sync.receiver.conversation.message.toSignalingMessage
import com.wire.kalium.logic.sync.receiver.handler.CallingMessageHandlerImpl
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.HistoryClient
import com.wire.kalium.protobuf.messages.HistoryClientAvailable
import com.wire.kalium.protobuf.messages.Text
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pbandk.ByteArr
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class InvalidApplicationMessageSyncTest {
    @Test
    fun givenInvalidMLSContent_whenSyncing_thenConsumeItAndProcessFollowingEvents() = runTest(TestKaliumDispatcher.default) {
        verifyInvalidContent(isMLS = true, durable = false)
        verifyInvalidContent(isMLS = true, durable = true)
    }

    @Test
    fun givenInvalidProteusContent_whenSyncing_thenConsumeItAndProcessFollowingEvents() = runTest(TestKaliumDispatcher.default) {
        verifyInvalidContent(isMLS = false, durable = false)
        verifyInvalidContent(isMLS = false, durable = true)
    }

    private suspend fun verifyInvalidContent(isMLS: Boolean, durable: Boolean) {
        val crypto = CryptoTransactionProviderArrangementMokkeryImpl()
        crypto.withTransactionReturning(Either.Right(Unit))
        val database = TestUserDatabase(TestUser.SELF.id.toDao(), dispatcher = TestKaliumDispatcher.default).builder
        val conversationRepository = mock<ConversationRepository>()
        val mlsRepository = mock<MLSConversationRepository>()
        val mapper = ProtoContentMapperImpl(selfUserId = TestUser.SELF.id)
        val processed = mutableListOf<String>()
        val recorded = mutableListOf<String>()
        val applications = mock<ApplicationMessageHandler>(MockMode.autoUnit)
        val calling = CallingMessageHandlerImpl(
            TestUser.SELF.id, mock(), lazy { mock(MockMode.autoUnit) }, conversationRepository, mock(), mock(),
        )
        everySuspend { conversationRepository.getConversationProtocolInfo(any()) } returns Either.Right(TestConversation.MLS_PROTOCOL_INFO)
        everySuspend { mlsRepository.decryptMessage(any(), any(), any()) } calls { call ->
            Either.Right(listOf(DecryptedMessageBundle.Text(
                TestConversation.GROUP_ID,
                ApplicationMessage(call.args[1] as ByteArray, TestUser.USER_ID, ClientId("sender")),
                identity = null,
            )))
        }
        everySuspend { crypto.proteusContext.decryptMessage<Either<*, *>>(any(), any(), any()) } calls { call ->
            @Suppress("UNCHECKED_CAST")
            val handle = call.args[2] as suspend (ByteArray) -> Either<*, *>
            handle(call.args[1] as ByteArray)
        }
        everySuspend { applications.handleContent(any(), any(), any(), any(), any(), any()) } calls { call ->
            val content = call.args[5] as ProtoContent.Readable
            val value = content.messageContent
            if (value is MessageContent.Calling) {
                val message = MessageUnpackResult.ApplicationMessage(
                    TestConversation.ID, Instant.DISTANT_PAST, TestUser.USER_ID, ClientId("sender"), content,
                )
                calling.handle(message.toSignalingMessage(TestUser.SELF.id), value)
            } else processed += content.messageUid
        }
        val handler = NewMessageEventHandlerImpl(
            ProteusMessageUnpackerImpl(TestUser.SELF.id, mapper),
            MLSMessageUnpackerImpl(conversationRepository, mock(), mlsRepository, mock(), TestUser.SELF.id, mapper),
            applications, mock(), { _, _ -> }, { _, _ -> }, TestUser.SELF.id, mock(), mock(),
        )
        val eventProcessor = mock<EventProcessor>()
        every { eventProcessor.disableEventProcessing } returns false
        everySuspend { eventProcessor.flushPendingSideEffects() } returns Either.Right(Unit)
        everySuspend { eventProcessor.processEvent(any(), any()) } calls { call ->
            val context = call.args[0] as CryptoTransactionContext
            val event = (call.args[1] as EventEnvelope).event
            when (event) {
                is Event.Conversation.NewMLSMessage -> handler.handleNewMLSMessage(context, event, TestEvent.liveDeliveryInfo).shouldSucceed()
                is Event.Conversation.NewMessage -> handler.handleNewProteusMessage(context, event, TestEvent.liveDeliveryInfo)
                else -> error("Unexpected event")
            }
            Either.Right(event.id)
        }
        val envelopes = malformedPayloads().flatMapIndexed { index, invalid ->
            listOf(invalid, GenericMessage("valid-$index", content = GenericMessage.Content.Text(Text("hello"))).encodeToByteArray())
                .mapIndexed { offset, bytes ->
                    val id = "event-$index-$offset"
                    if (isMLS) TestEvent.newMLSMessageEvent(Instant.DISTANT_PAST)
                        .copy(id = id, content = Base64.encode(bytes)).wrapInEnvelope()
                    else TestEvent.newMessageEvent(Base64.encode(bytes)).copy(id = id).wrapInEnvelope()
                }
        }
        database.eventDAO.insertEvents(envelopes.map { NewEventEntity(it.event.id, "[]", false, false) })
        val repository = mock<EventRepository>()
        everySuspend { repository.setEventsAsProcessed(any()) } calls { call ->
            @Suppress("UNCHECKED_CAST")
            database.eventDAO.markEventsAsProcessed(call.args[0] as List<String>)
            Either.Right(Unit)
        }
        val recovery = mock<EventBatchRecovery>()
        everySuspend { recovery.wereEffectsProcessed(any(), any()) } returns false
        everySuspend { recovery.recordProcessed(any(), any()) } calls { call ->
            @Suppress("UNCHECKED_CAST")
            recorded += (call.args[1] as List<EventEnvelope>).map { it.event.id }
        }
        everySuspend { recovery.acknowledge(any()) } calls { call ->
            @Suppress("UNCHECKED_CAST")
            database.eventDAO.markEventsAsProcessed(call.args[0] as List<String>)
        }
        val processor = if (durable) EventBatchProcessor.durable(eventProcessor, crypto.cryptoTransactionProvider, database, { recovery })
        else EventBatchProcessor.standard(eventProcessor, crypto.cryptoTransactionProvider, database, repository)

        processor.processBatch(envelopes)

        assertEquals(listOf("valid-0", "valid-1", "valid-2"), processed)
        assertTrue(database.eventDAO.observeUnprocessedEvents().first().isEmpty())
        envelopes.forEach { assertTrue(database.eventDAO.getEventById(it.event.id)!!.isProcessed) }
        if (durable) assertEquals(envelopes.map { it.event.id }, recorded)
    }

    private fun malformedPayloads(): List<ByteArray> = listOf(
        byteArrayOf(0x80.toByte()),
        GenericMessage("bad-history", content = GenericMessage.Content.HistoryClientAvailable(HistoryClientAvailable(
            HistoryClient(clientId = "client", secret = ByteArr(byteArrayOf(1, 2, 3)), createdAt = "not-an-instant")
        ))).encodeToByteArray(),
        GenericMessage("bad-calling", content = GenericMessage.Content.Calling(Calling(content = "{"))).encodeToByteArray(),
    )
}
