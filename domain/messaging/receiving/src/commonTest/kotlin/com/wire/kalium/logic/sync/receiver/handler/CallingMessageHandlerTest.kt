/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.call.CallModerationAction
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.ShouldRemoteMuteChecker
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMembersProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CallingMessageHandlerTest {

    @Test
    fun givenNormalCallingMessage_whenHandling_thenOnlyExactIncomingMessageIsForwarded() = runTest {
        val arrangement = Arrangement()
        val message = CALLING_MESSAGE.copy(content = CALLING_CONTENT)

        arrangement.handler.handle(message, CALLING_CONTENT)

        assertEquals(listOf("consumer"), arrangement.trace)
        assertEquals(message to CALLING_CONTENT, arrangement.consumedMessage)
    }

    @Test
    fun givenRemoteMuteMessage_whenSelectingTarget_thenSelfUsesContentIdWithMessageIdFallbackAndNonSelfUsesMessageId() = runTest {
        val cases = listOf(
            TargetCase(isSelfMessage = true, contentConversationId = CONTENT_CONVERSATION_ID, expected = CONTENT_CONVERSATION_ID),
            TargetCase(isSelfMessage = true, contentConversationId = null, expected = MESSAGE_CONVERSATION_ID),
            TargetCase(isSelfMessage = false, contentConversationId = CONTENT_CONVERSATION_ID, expected = MESSAGE_CONVERSATION_ID),
        )

        cases.forEach { case ->
            val arrangement = Arrangement()
            val content = REMOTE_MUTE_CONTENT.copy(conversationId = case.contentConversationId)
            val message = CALLING_MESSAGE.copy(
                content = content,
                conversationId = MESSAGE_CONVERSATION_ID,
                isSelfMessage = case.isSelfMessage,
            )

            arrangement.handler.handle(message, content)

            assertEquals(case.expected, arrangement.observedConversationId)
            assertEquals(case.expected, arrangement.mutedConversationId)
            assertEquals(case.expected, arrangement.recordedConversationId)
            assertEquals(listOf("client", "members", "checker", "mute", "record"), arrangement.trace)
        }
    }

    @Test
    fun givenCurrentClientIdIsUnavailable_whenHandlingRemoteMute_thenReturnBeforeMemberLookupAndEffects() = runTest {
        val arrangement = Arrangement(
            currentClientResult = Either.Left(CoreFailure.MissingClientRegistration),
        )

        arrangement.handler.handle(CALLING_MESSAGE.copy(content = REMOTE_MUTE_CONTENT), REMOTE_MUTE_CONTENT)

        assertEquals(listOf("client"), arrangement.trace)
    }

    @Test
    fun givenMemberFlowHasMultipleEmissions_whenHandlingRemoteMute_thenOnlyFirstEmissionIsUsed() = runTest {
        val firstMembers = listOf(Conversation.Member(SENDER_USER_ID, Conversation.Member.Role.Admin))
        val secondMembers = listOf(Conversation.Member(SELF_USER_ID, Conversation.Member.Role.Member))
        val arrangement = Arrangement(shouldRemoteMute = false).apply {
            membersFlow = flow {
                trace += "first-emission"
                emit(firstMembers)
                trace += "second-emission"
                emit(secondMembers)
            }
        }

        arrangement.handler.handle(CALLING_MESSAGE.copy(content = REMOTE_MUTE_CONTENT), REMOTE_MUTE_CONTENT)

        assertEquals(firstMembers, arrangement.checkedMembers)
        assertEquals(listOf("client", "members", "first-emission", "checker"), arrangement.trace)
    }

    @Test
    fun givenCheckerRejectsRemoteMute_whenHandling_thenNeitherEffectRuns() = runTest {
        val arrangement = Arrangement(shouldRemoteMute = false)

        arrangement.handler.handle(CALLING_MESSAGE.copy(content = REMOTE_MUTE_CONTENT), REMOTE_MUTE_CONTENT)

        assertEquals(listOf("client", "members", "checker"), arrangement.trace)
        assertEquals(null, arrangement.mutedConversationId)
        assertEquals(null, arrangement.recordedAction)
    }

    @Test
    fun givenCheckerAcceptsRemoteMute_whenHandling_thenExactInputsAndEffectsRunInOrder() = runTest {
        val members = listOf(Conversation.Member(SENDER_USER_ID, Conversation.Member.Role.Admin))
        val arrangement = Arrangement().apply { membersFlow = flowOf(members) }
        val message = CALLING_MESSAGE.copy(content = REMOTE_MUTE_CONTENT)

        arrangement.handler.handle(message, REMOTE_MUTE_CONTENT)

        assertEquals(listOf("client", "members", "checker", "mute", "record"), arrangement.trace)
        assertEquals(message.senderUserId, arrangement.checkedSenderUserId)
        assertEquals(SELF_USER_ID, arrangement.checkedSelfUserId)
        assertEquals(CLIENT_ID.value, arrangement.checkedSelfClientId)
        assertEquals(REMOTE_MUTE_TARGETS, arrangement.checkedTargets)
        assertEquals(members, arrangement.checkedMembers)
        assertEquals(message.conversationId, arrangement.mutedConversationId)
        assertEquals(message.conversationId, arrangement.recordedConversationId)
        assertEquals(
            CallModerationAction(message.id, message.senderUserId, CallModerationAction.Type.MUTED),
            arrangement.recordedAction,
        )
        assertEquals(null, arrangement.consumedMessage)
    }

    @Test
    fun givenAnyRemoteMuteDependencyThrows_whenHandling_thenSameExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        DependencyStage.entries.forEach { stage ->
            val expected = IllegalStateException("$stage failed")
            assertDependencyFailure(stage, expected)
        }
    }

    @Test
    fun givenSelfRemoteMuteWithTargetConversation_whenHandling_thenContentConversationIsUsed() = runTest {
        val targetConversationId = ConversationId("target-conversation", "wire.com")
        val content = REMOTE_MUTE_CONTENT.copy(conversationId = targetConversationId)
        val message = CALLING_MESSAGE.copy(content = content, isSelfMessage = true)
        val arrangement = Arrangement()

        arrangement.handler.handle(message, content)

        assertEquals(targetConversationId, arrangement.mutedConversationId)
        assertEquals(targetConversationId, arrangement.recordedConversationId)
        assertEquals(
            CallModerationAction(message.id, message.senderUserId, CallModerationAction.Type.MUTED),
            arrangement.recordedAction,
        )
    }

    @Test
    fun givenSelfRemoteMuteWithoutTargetConversation_whenHandling_thenMessageConversationIsUsed() = runTest {
        val content = REMOTE_MUTE_CONTENT.copy(conversationId = null)
        val message = CALLING_MESSAGE.copy(content = content, isSelfMessage = true)
        val arrangement = Arrangement()

        arrangement.handler.handle(message, content)

        assertEquals(message.conversationId, arrangement.mutedConversationId)
    }

    @Test
    fun givenAnyRemoteMuteDependencyCancels_whenHandling_thenSameCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        DependencyStage.entries.forEach { stage ->
            val expected = CancellationException("$stage cancelled")
            assertDependencyFailure(stage, expected)
        }
    }

    private suspend fun assertDependencyFailure(stage: DependencyStage, expected: Throwable) {
        val arrangement = Arrangement().apply { throwableStage = stage to expected }

        val actual = assertFailsWith<Throwable> {
            arrangement.handler.handle(CALLING_MESSAGE.copy(content = REMOTE_MUTE_CONTENT), REMOTE_MUTE_CONTENT)
        }

        assertSame(expected, actual)
        assertEquals(stage.expectedTrace, arrangement.trace)
        assertEquals(null, arrangement.consumedMessage)
    }

    private class Arrangement(
        private val currentClientResult: Either<CoreFailure, ClientId> = Either.Right(CLIENT_ID),
        private val shouldRemoteMute: Boolean = true,
    ) {
        val trace = mutableListOf<String>()
        var membersFlow: Flow<List<Conversation.Member>> = flowOf(emptyList())
        var throwableStage: Pair<DependencyStage, Throwable>? = null

        var consumedMessage: Pair<Message.Signaling, MessageContent.Calling>? = null
        var observedConversationId: ConversationId? = null
        var checkedSenderUserId: UserId? = null
        var checkedSelfUserId: UserId? = null
        var checkedSelfClientId: String? = null
        var checkedTargets: MessageContent.Calling.Targets? = null
        var checkedMembers: List<Conversation.Member>? = null
        var mutedConversationId: ConversationId? = null
        var recordedConversationId: ConversationId? = null
        var recordedAction: CallModerationAction? = null

        private val currentClientIdProvider: suspend () -> Either<CoreFailure, ClientId> = {
            trace += "client"
            throwAt(DependencyStage.CURRENT_CLIENT)
            currentClientResult
        }

        private val incomingCallingMessageConsumer = IncomingCallingMessageConsumer { message, content ->
            trace += "consumer"
            consumedMessage = message to content
        }

        private val conversationMembersProvider = ConversationMembersProvider { conversationId ->
            trace += "members"
            observedConversationId = conversationId
            throwAt(DependencyStage.MEMBER_PROVIDER)
            if (throwableStage?.first == DependencyStage.MEMBER_FLOW) {
                flow { throw throwableStage!!.second }
            } else {
                membersFlow
            }
        }

        private val checker = object : ShouldRemoteMuteChecker {
            override fun check(
                senderUserId: UserId,
                selfUserId: UserId,
                selfClientId: String,
                targets: MessageContent.Calling.Targets?,
                conversationMembers: List<Conversation.Member>,
            ): Boolean {
                trace += "checker"
                checkedSenderUserId = senderUserId
                checkedSelfUserId = selfUserId
                checkedSelfClientId = selfClientId
                checkedTargets = targets
                checkedMembers = conversationMembers
                throwAt(DependencyStage.CHECKER)
                return shouldRemoteMute
            }
        }

        private val remoteMuteCall = RemoteMuteCall { conversationId ->
            trace += "mute"
            mutedConversationId = conversationId
            throwAt(DependencyStage.MUTE)
        }

        private val remoteMuteActionRecorder = RemoteMuteActionRecorder { conversationId, action ->
            trace += "record"
            recordedConversationId = conversationId
            recordedAction = action
            throwAt(DependencyStage.RECORDER)
        }

        val handler = CallingMessageHandlerImpl(
            selfUserId = SELF_USER_ID,
            currentClientIdProvider = currentClientIdProvider,
            incomingCallingMessageConsumer = incomingCallingMessageConsumer,
            conversationMembersProvider = conversationMembersProvider,
            remoteMuteActionRecorder = remoteMuteActionRecorder,
            remoteMuteCall = remoteMuteCall,
            shouldRemoteMuteChecker = checker,
        )

        private fun throwAt(stage: DependencyStage) {
            throwableStage?.takeIf { it.first == stage }?.let { throw it.second }
        }
    }

    private enum class DependencyStage(val expectedTrace: List<String>) {
        CURRENT_CLIENT(listOf("client")),
        MEMBER_PROVIDER(listOf("client", "members")),
        MEMBER_FLOW(listOf("client", "members")),
        CHECKER(listOf("client", "members", "checker")),
        MUTE(listOf("client", "members", "checker", "mute")),
        RECORDER(listOf("client", "members", "checker", "mute", "record")),
    }

    private data class TargetCase(
        val isSelfMessage: Boolean,
        val contentConversationId: ConversationId?,
        val expected: ConversationId,
    )

    private companion object {
        val MESSAGE_CONVERSATION_ID = ConversationId(value = "convId", domain = "domainId")
        val CONTENT_CONVERSATION_ID = ConversationId("content-conversation", "wire.example")
        val SELF_USER_ID = UserId(value = "41d2b365-f4a9-4ba1-bddf-5afb8aca6786", domain = "domain")
        val SENDER_USER_ID = SELF_USER_ID
        val CLIENT_ID = ClientId("test")
        val CALLING_CONTENT = MessageContent.Calling(
            value = """{"type":"TYPE","conversationId":"$MESSAGE_CONVERSATION_ID"}""",
            conversationId = null,
        )
        val REMOTE_MUTE_CONTENT = MessageContent.Calling(
            value = """
                {
                    "type":"REMOTEMUTE",
                    "data":{"targets":{"${SELF_USER_ID.domain}":{"${SELF_USER_ID.value}":["${CLIENT_ID.value}"]}}}
                }
            """.trimIndent(),
            conversationId = null,
        )
        val REMOTE_MUTE_TARGETS = MessageContent.Calling.Targets(
            domainToUserIdToClients = mapOf(
                SELF_USER_ID.domain to mapOf(
                    SELF_USER_ID.value to listOf(CLIENT_ID.value),
                ),
            ),
        )
        val CALLING_MESSAGE = Message.Signaling(
            id = "message-id",
            content = CALLING_CONTENT,
            conversationId = MESSAGE_CONVERSATION_ID,
            date = Clock.System.now(),
            senderUserId = SENDER_USER_ID,
            senderClientId = CLIENT_ID,
            status = Message.Status.Read(0),
            isSelfMessage = false,
            expirationData = null,
        )
    }
}
