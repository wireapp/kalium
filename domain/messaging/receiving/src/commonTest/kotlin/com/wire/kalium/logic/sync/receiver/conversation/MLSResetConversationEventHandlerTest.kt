/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.MLSResetEventRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail

class MLSResetConversationEventHandlerTest {

    @Test
    fun givenMLSContextIsNull_whenHandlingEvent_thenShouldOnlyEndCall() = runTest {
        val (arrangement, handler) = arrange {
            withMLSContextNull()
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        assertEquals(listOf(CONVERSATION_ID), arrangement.endedCallConversationIds)

        verifySuspend(VerifyMode.not) {
            arrangement.mlsResetEventRepository.leaveGroup(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsResetEventRepository.hasEstablishedMLSGroup(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsResetEventRepository.updateGroupIdAndState(any(), any(), any(), any())
        }
    }

    @Test
    fun givenLeaveGroupFails_whenHandlingEvent_thenShouldStillUpdateGroupId() = runTest {
        val failure = CoreFailure.Unknown(RuntimeException("Leave failed"))
        val (arrangement, handler) = arrange {
            withLeaveGroupFailing(failure)
            withHasEstablishedMLSGroupReturning(false)
            withUpdateGroupIdAndStateSucceeding()
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.leaveGroup(eq(arrangement.mlsContext), eq(GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.hasEstablishedMLSGroup(eq(arrangement.mlsContext), eq(NEW_GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.updateGroupIdAndState(
                eq(CONVERSATION_ID),
                eq(NEW_GROUP_ID),
                eq(0),
                eq(ConversationEntity.GroupState.PENDING_AFTER_RESET),
            )
        }
    }

    @Test
    fun givenNewGroupAlreadyEstablished_whenHandlingEvent_thenShouldUpdateWithEstablishedState() =
        runTest {
            val newGroupEpoch = 42L
            val (arrangement, handler) = arrange {
                withLeaveGroupSucceeding()
                withHasEstablishedMLSGroupReturning(true)
                withNewGroupEpoch(newGroupEpoch)
                withUpdateGroupIdAndStateSucceeding()
            }

            handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.leaveGroup(eq(arrangement.mlsContext), eq(GROUP_ID))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.hasEstablishedMLSGroup(
                    eq(arrangement.mlsContext),
                    eq(NEW_GROUP_ID)
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.updateGroupIdAndState(
                    eq(CONVERSATION_ID),
                    eq(NEW_GROUP_ID),
                    eq(newGroupEpoch),
                    eq(ConversationEntity.GroupState.ESTABLISHED)
                )
            }
        }

    @Test
    fun givenNewGroupNotEstablished_whenHandlingEvent_thenShouldUpdateWithPendingWelcomeState() =
        runTest {
            val (arrangement, handler) = arrange {
                withLeaveGroupSucceeding()
                withHasEstablishedMLSGroupReturning(false)
                withUpdateGroupIdAndStateSucceeding()
            }

            handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.leaveGroup(eq(arrangement.mlsContext), eq(GROUP_ID))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.hasEstablishedMLSGroup(
                    eq(arrangement.mlsContext),
                    eq(NEW_GROUP_ID)
                )
            }

            verifySuspend(VerifyMode.not) {
                arrangement.mlsContext.conversationEpoch(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.updateGroupIdAndState(
                    eq(CONVERSATION_ID),
                    eq(NEW_GROUP_ID),
                    eq(0L),
                    eq(ConversationEntity.GroupState.PENDING_AFTER_RESET)
                )
            }
        }

    @Test
    fun givenHasEstablishedGroupFails_whenHandlingEvent_thenShouldUpdateGroupIdWithNotEstablished() =
        runTest {
            val failure = MLSFailure.Generic(RuntimeException("Has established failed"))
            val event = MLS_RESET_EVENT
            val (arrangement, handler) = arrange {
                withLeaveGroupSucceeding()
                withHasEstablishedMLSGroupFailing(failure)
                withUpdateGroupIdAndStateSucceeding()
            }

            handler.handle(arrangement.transactionContext, event)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.leaveGroup(eq(arrangement.mlsContext), eq(event.groupID))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.mlsContext.conversationEpoch(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsResetEventRepository.updateGroupIdAndState(
                    matches { it == event.conversationId },
                    matches { it == event.newGroupID },
                    eq(0L),
                    matches { it == ConversationEntity.GroupState.PENDING_AFTER_RESET }
                )
            }
        }

    @Test
    fun givenUpdateGroupIdAndStateFails_whenHandlingEvent_thenShouldPropagateError() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, handler) = arrange {
            withLeaveGroupSucceeding()
            withHasEstablishedMLSGroupReturning(false)
            withUpdateGroupIdAndStateFailing(failure)
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.updateGroupIdAndState(
                eq(CONVERSATION_ID),
                eq(NEW_GROUP_ID),
                eq(0L),
                eq(ConversationEntity.GroupState.PENDING_AFTER_RESET)
            )
        }
    }

    @Test
    fun givenAllSucceedsAndGroupIsEstablished_whenHandlingEvent_thenShouldLeaveGroupAndUpdateState() = runTest {
        val newGroupEpoch = 44L
        val (arrangement, handler) = arrange {
            withLeaveGroupSucceeding()
            withHasEstablishedMLSGroupReturning(true)
            withNewGroupEpoch(newGroupEpoch)
            withUpdateGroupIdAndStateSucceeding()
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.leaveGroup(eq(arrangement.mlsContext), eq(GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.hasEstablishedMLSGroup(eq(arrangement.mlsContext), eq(NEW_GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsResetEventRepository.updateGroupIdAndState(
                eq(CONVERSATION_ID),
                eq(NEW_GROUP_ID),
                eq(newGroupEpoch),
                eq(ConversationEntity.GroupState.ESTABLISHED)
            )
        }
    }

    @Test
    fun givenEstablishedGroup_whenHandling_thenLeaveCheckEpochAndUpdateRunInExactOrder() = runTest {
        val newGroupEpoch = 51L
        val (arrangement, handler) = arrange {
            withLeaveGroupSucceeding()
            withHasEstablishedMLSGroupReturning(true)
            withNewGroupEpoch(newGroupEpoch)
            withUpdateGroupIdAndStateSucceeding()
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        assertEquals(listOf("endCall", "leave", "hasEstablished", "epoch", "update"), arrangement.callOrder)
        verifySuspend(VerifyMode.order) {
            arrangement.mlsResetEventRepository.leaveGroup(eq(arrangement.mlsContext), eq(GROUP_ID))
            arrangement.mlsResetEventRepository.hasEstablishedMLSGroup(eq(arrangement.mlsContext), eq(NEW_GROUP_ID))
            arrangement.mlsContext.conversationEpoch(eq(NEW_GROUP_ID.value))
            arrangement.mlsResetEventRepository.updateGroupIdAndState(
                eq(CONVERSATION_ID),
                eq(NEW_GROUP_ID),
                eq(newGroupEpoch),
                eq(ConversationEntity.GroupState.ESTABLISHED),
            )
        }
    }

    @Test
    fun givenLeaveAndUpdateReturnLeft_whenHandling_thenBothFailuresAreIgnoredAndAllOperationsStillRun() = runTest {
        val (arrangement, handler) = arrange {
            withLeaveGroupFailing(CoreFailure.Unknown(IllegalStateException("leave failed")))
            withHasEstablishedMLSGroupReturning(false)
            withUpdateGroupIdAndStateFailing(StorageFailure.DataNotFound)
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        assertEquals(listOf("endCall", "leave", "hasEstablished", "update"), arrangement.callOrder)
    }

    @Test
    fun givenAnyOperationThrows_whenHandling_thenSameExceptionEscapesAndLaterWorkIsSkipped() = runTest {
        FailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, IllegalStateException("$stage failed"))
        }
    }

    @Test
    fun givenAnyOperationCancels_whenHandling_thenSameCancellationEscapesAndLaterWorkIsSkipped() = runTest {
        FailureStage.entries.forEach { stage ->
            assertEscapingFailure(stage, CancellationException("$stage cancelled"))
        }
    }

    private suspend fun assertEscapingFailure(stage: FailureStage, expected: Throwable) {
        val (arrangement, handler) = arrange {
            withLeaveGroupSucceeding(expected.takeIf { stage == FailureStage.LEAVE })
            withHasEstablishedMLSGroupReturning(true, expected.takeIf { stage == FailureStage.HAS_ESTABLISHED })
            withNewGroupEpoch(13L, expected.takeIf { stage == FailureStage.EPOCH })
            withUpdateGroupIdAndStateSucceeding(expected.takeIf { stage == FailureStage.UPDATE })
        }

        val actual = try {
            handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)
            fail("Expected $expected to escape from $stage")
        } catch (actual: Throwable) {
            actual
        }

        assertSame(expected, actual)
        assertEquals(listOf("endCall") + FailureStage.entries.take(stage.ordinal + 1).map { it.callName }, arrangement.callOrder)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) {
        val transactionContext = mock<CryptoTransactionContext>()
        val mlsContext = mock<MlsCoreCryptoContext>()
        val mlsResetEventRepository = mock<MLSResetEventRepository>()
        val callOrder = mutableListOf<String>()
        val endedCallConversationIds = mutableListOf<ConversationId>()

        val endCallOnMLSReset: suspend (ConversationId) -> Unit = { conversationId ->
            callOrder += "endCall"
            endedCallConversationIds += conversationId
        }

        init {
            every { transactionContext.mls } returns mlsContext
        }

        fun withLeaveGroupSucceeding(throwable: Throwable? = null) =
            withLeaveGroupReturning(Either.Right(Unit), throwable)

        fun withLeaveGroupFailing(failure: CoreFailure) =
            withLeaveGroupReturning(Either.Left(failure))

        private fun withLeaveGroupReturning(result: Either<CoreFailure, Unit>, throwable: Throwable? = null) = apply {
            everySuspend {
                mlsResetEventRepository.leaveGroup(any(), any())
            } calls {
                callOrder += "leave"
                throwable?.let { throw it }
                result
            }
        }

        fun withHasEstablishedMLSGroupReturning(hasGroup: Boolean, throwable: Throwable? = null) = apply {
            everySuspend {
                mlsResetEventRepository.hasEstablishedMLSGroup(any(), any())
            } calls {
                callOrder += "hasEstablished"
                throwable?.let { throw it }
                Either.Right(hasGroup)
            }
        }

        fun withNewGroupEpoch(newGroupEpoch: Long, throwable: Throwable? = null) = apply {
            everySuspend {
                mlsContext.conversationEpoch(any())
            } calls {
                callOrder += "epoch"
                throwable?.let { throw it }
                newGroupEpoch.toULong()
            }
        }

        fun withHasEstablishedMLSGroupFailing(failure: MLSFailure) = apply {
            everySuspend {
                mlsResetEventRepository.hasEstablishedMLSGroup(any(), any())
            } calls {
                callOrder += "hasEstablished"
                Either.Left(failure)
            }
        }

        fun withUpdateGroupIdAndStateSucceeding(throwable: Throwable? = null) =
            withUpdateGroupIdAndStateReturning(Either.Right(Unit), throwable)

        fun withUpdateGroupIdAndStateFailing(failure: CoreFailure) =
            withUpdateGroupIdAndStateReturning(Either.Left(failure))

        private fun withUpdateGroupIdAndStateReturning(
            result: Either<CoreFailure, Unit>,
            throwable: Throwable? = null,
        ) = apply {
            everySuspend {
                mlsResetEventRepository.updateGroupIdAndState(any(), any(), any(), any())
            } calls {
                callOrder += "update"
                throwable?.let { throw it }
                result
            }
        }

        fun withMLSContextNull() = apply {
            every { transactionContext.mls } returns null
        }

        fun arrange(): Pair<Arrangement, MLSResetConversationEventHandler> {
            block()
            return this to MLSResetConversationEventHandlerImpl(
                mlsResetEventRepository = mlsResetEventRepository,
                endCallOnMLSReset = endCallOnMLSReset,
            )
        }
    }

    private enum class FailureStage(val callName: String) {
        LEAVE("leave"),
        HAS_ESTABLISHED("hasEstablished"),
        EPOCH("epoch"),
        UPDATE("update"),
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) =
            Arrangement(configuration).arrange()

        val GROUP_ID = GroupID("old_group_id")
        val NEW_GROUP_ID = GroupID("new_group_id")
        val CONVERSATION_ID = ConversationId("valueConvo", "domainConvo")
        val USER_ID = UserId("41d2b365-f4a9-4ba1-bddf-5afb8aca6786", "domain")

        val MLS_RESET_EVENT = Event.Conversation.MLSReset(
            id = "event_id",
            conversationId = CONVERSATION_ID,
            from = USER_ID,
            groupID = GROUP_ID,
            newGroupID = NEW_GROUP_ID,
        )
    }
}
