/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.wire.kalium.logic.feature.call

import com.wire.kalium.calling.*
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.mapper.CallMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.*
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.logic.sync.receiving.CallSignalStore
import com.wire.kalium.logic.sync.receiving.CallSignalRecord
import com.wire.kalium.logic.sync.receiving.IncomingCallReference
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Real retained native handle and production callbacks; repository writes are recorded at its boundary. */
class CallManagerIncomingLifecycleTest {
    @Test fun fullEngineUpdatesRepositoryInCallbackOrder() = runTest {
        val work = async(Dispatchers.Default) {
            withTimeout(90_000) {
                assertTrue(AppleAvsFlowManager().startIfAvailable())
                for (type in listOf(ConversationTypeCalling.OneOnOne, ConversationTypeCalling.Group,
                    ConversationTypeCalling.Conference, ConversationTypeCalling.ConferenceMls)) {
                    for (termination in listOf("answer", "reject", "cancel")) {
                        val self = UserId(Uuid.random().toString(), "example.com")
                        val peer = UserId(Uuid.random().toString(), "example.com")
                        val conversation = ConversationId("11111111-1111-4111-8111-111111111111", "example.com")
                        val ready = CompletableDeferred<Unit>()
                        val reasons = Channel<Int>(Channel.UNLIMITED)
                        val states = Channel<CallStatus>(Channel.UNLIMITED)
                        val repository = mock<CallRepository>(MockMode.autoUnit)
                        everySuspend { repository.fetchServerTime() } returns null
                        everySuspend { repository.getCallConfigResponse(any()) } returns
                            """{"ice_servers":[{"urls":["stun:127.0.0.1:9"]}],"sft_servers":[{"urls":["https://sft.invalid"]}],"ttl":3600}""".right()
                        var metadata: CallMetadata? = null
                        every { repository.getCallMetadata(any()) } calls { metadata }
                        every { repository.establishedCallsFlow() } returns kotlinx.coroutines.flow.flowOf(emptyList())
                        everySuspend { repository.createCall(any(), any(), any(), any(), any(), any(), any()) } calls {
                            metadata = TestCall.oneOnOneCallMetadata().copy(
                                callerId = peer,
                                callStatus = it.arg(2),
                                conversationType = if (type == ConversationTypeCalling.OneOnOne) {
                                    Conversation.Type.OneOnOne
                                } else Conversation.Type.Group.Regular,
                            )
                            states.send(it.arg(2))
                        }
                        everySuspend { repository.updateIsAnsweredElsewhere(any()) } calls {
                            metadata = metadata?.copy(callAnsweredElsewhere = true)
                        }
                        everySuspend { repository.updateCallStatusById(any(), any()) } calls {
                            metadata = metadata?.copy(callStatus = it.arg(1))
                            states.send(it.arg(1))
                        }
                        val client = mock<CurrentClientIdProvider>()
                        everySuspend { client() } returns ClientId("abcd").right()
                        val federated = mock<FederatedIdMapper>()
                        everySuspend { federated.parseToFederatedId(any<QualifiedID>()) } calls {
                            val id = it.arg<QualifiedID>(0)
                            "${id.value}@${id.domain}"
                        }
                        val qualified = mock<QualifiedIdMapper>()
                        every { qualified.fromStringToQualifiedID(any()) } calls {
                            val id = it.arg<String>(0)
                            QualifiedID(id.substringBefore('@'), id.substringAfter('@'))
                        }
                        val types = mock<GetCallConversationTypeProvider>()
                        everySuspend { types(any()) } returns GetCallConversationTypeProvider.Result(type, false)
                        val network = mock<NetworkStateObserver>()
                        every { network.observeNetworkState() } returns MutableStateFlow(NetworkState.ConnectedWithInternet)
                        val native = object : AppleAvsBridge by AppleAvs.bridge {
                            override fun userHandle(selfUserId: String, selfClientId: String, callbacks: AppleAvsCallbacks): UInt? =
                                AppleAvs.bridge.userHandle(selfUserId, selfClientId, object : AppleAvsCallbacks by callbacks {
                                    override fun onReady(version: Int) { callbacks.onReady(version); ready.complete(Unit) }
                                    override fun onClosedCall(reason: Int, conversationId: String?, messageTime: UInt,
                                        userId: String?, clientId: String?) {
                                        reasons.trySend(reason)
                                        callbacks.onClosedCall(reason, conversationId, messageTime, userId, clientId)
                                    }
                                })
                        }
                        val config = mock<com.wire.kalium.logic.configuration.UserConfigRepository>()
                        everySuspend { config.shouldUseSFTForOneOnOneCalls() } returns false.right()
                        val manager = CallManagerImpl(repository, client, mock(), mock(), CallMapperImpl(qualified),
                            federated, qualified, com.wire.kalium.logic.data.call.VideoStateCheckerImpl(), mock(MockMode.autoUnit), mock(MockMode.autoUnit), network, types, config, KaliumConfigs(),
                            mock(MockMode.autoUnit), mock(MockMode.autoUnit), mock(MockMode.autoUnit), self, avs = native)
                        val journal = mock<CallSignalStore>()
                        val signals = mutableListOf<CallSignalRecord>()
                        everySuspend { journal.signals() } calls { signals.toList() }
                        everySuspend { journal.append(any()) } calls {
                            val event = it.arg<com.wire.kalium.logic.sync.receiving.ReceivedCallEvent>(0)
                            signals.find { it.messageId == event.messageId } ?: CallSignalRecord(
                                Uuid.random().toString(), event.messageId, event.conversationId.value, event.conversationId.domain,
                                event.senderId.value, event.senderId.domain, event.senderClientId, event.payload,
                                event.time.toEpochMilliseconds(), event.isGroup, event.isMLS, Clock.System.now().toEpochMilliseconds(),
                                ready = true,
                            ).also { signals.add(it) }
                        }
                        everySuspend { journal.submitted(any(), any()) } calls {
                            val index = signals.indexOfFirst { record -> record.id == it.arg<String>(0) }
                            signals[index] = signals[index].copy(submittedGeneration = it.arg(1))
                        }
                        val incomingReferences = Channel<IncomingCallReference>(Channel.UNLIMITED)
                        everySuspend { journal.incoming(any(), any(), any(), any()) } calls {
                            incomingReferences.send(it.arg(0))
                            null
                        }
                        everySuspend { journal.closed(any(), any(), any()) } returns null
                        manager.attachSignalJournal(journal)
                        try {
                            manager.waitUntilInitialized()
                            withTimeout(5000) { ready.await() }
                            val start = when (type.avsValue) { 0 -> "SETUP"; 1 -> "GROUPSTART"; else -> "CONFSTART" }
                            val session = if (type.avsValue >= 2) "6572f49303d3df266ecfac1d82c74b7c" else "call"
                            val now = Clock.System.now() - 10.seconds
                            suspend fun receive(kind: String, own: Boolean, response: Boolean, sessionId: String, offset: Int, id: String = Uuid.random().toString()) {
                                val content = MessageContent.Calling("""{"version":"3.0","type":"$kind","sessid":"$sessionId","resp":$response,"sdp":"v=0","props":{"videosend":"false"},"sft_url":"https://sft.invalid","secret":"YWJjZA==","timestamp":"1","seqno":"1"}""", conversation)
                                manager.onCallingMessageReceived(Message.Signaling(id, content,
                                    conversation, now + offset.seconds, if (own) self else peer, ClientId(if (own) "eeee" else "def0"),
                                    Message.Status.Sent, isSelfMessage = own, expirationData = null), content)
                            }
                            val queuedReject = type == ConversationTypeCalling.OneOnOne && termination == "reject"
                            receive(start, false, false, session, 0)
                            if (!queuedReject) {
                                assertEquals(CallStatus.INCOMING, withTimeout(5000) { states.receive() })
                                assertEquals(
                                    IncomingCallReference(conversation.toDao(), peer.toDao(), "def0", now.epochSeconds),
                                    withTimeout(5000) { incomingReferences.receive() },
                                )
                            }
                            val end = when (termination) {
                                "answer" -> start
                                "reject" -> "REJECT"
                                else -> when (type.avsValue) { 0 -> "CANCEL"; 1 -> "GROUPLEAVE"; else -> "CONFEND" }
                            }
                            receive(end, termination != "cancel", termination == "answer",
                                if (termination == "answer" && type.avsValue == 0) "other" else session, 1)
                            var queuedState: CallStatus? = null
                            if (queuedReject) {
                                // No wait for native callback effects before enqueueing Answer.
                                manager.answerCall(conversation, false, false)
                                queuedState = withTimeout(5000) { states.receive() }
                                // AVS may suppress the ringing callback when a REJECT is already queued.
                                assertTrue(queuedState in setOf(CallStatus.INCOMING, CallStatus.REJECTED))
                            }
                            val reason = withTimeout(5000) { reasons.receive() }
                            val expected = when {
                                termination == "cancel" && type.avsValue < 2 -> CallStatus.MISSED
                                termination == "cancel" -> CallStatus.CLOSED
                                type != ConversationTypeCalling.OneOnOne -> CallStatus.STILL_ONGOING
                                termination == "answer" -> CallStatus.CLOSED
                                else -> CallStatus.REJECTED
                            }
                            val actual = if (queuedState == CallStatus.REJECTED) queuedState else withTimeout(5000) { states.receive() }
                            println("FULL_MANAGER type=${type.avsValue} termination=$termination reason=$reason repository=$actual")
                            assertEquals(expected, actual)
                            if (queuedReject) {
                                // This deliberately bypasses the runtime presentation guard. AVS may
                                // emit CLOSED for the invalid queued Answer, but must never participate.
                                val subsequent = withTimeoutOrNull(300) { states.receive() }
                                if (subsequent != null) assertEquals(CallStatus.CLOSED, subsequent)
                                assertNull(withTimeoutOrNull(300) { states.receive() })
                                println("FULL_MANAGER queued_reject_before_answer_prevents_participation=true")
                            }
                            if (type == ConversationTypeCalling.OneOnOne && termination == "answer") {
                                receive("SETUP", false, false, "next-call", 2, "next-start")
                                assertEquals(CallStatus.INCOMING, withTimeout(5000) { states.receive() })
                                receive("SETUP", false, false, "next-call", 2, "next-start") // delivery retry
                                receive("REJECT", true, false, session, 1) // delayed old termination
                                assertNull(withTimeoutOrNull(300) { states.receive() })
                                receive("CANCEL", false, false, "next-call", 3)
                                assertEquals(CallStatus.MISSED, withTimeout(5000) { states.receive() })
                                println("FULL_MANAGER duplicate_start_and_delayed_old_reject_preserve_next_call=true")
                            }
                        } finally { manager.cancelJobs() }
                    }
                }
            }
        }
        while (!work.isCompleted) CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
        work.await()
    }
}
