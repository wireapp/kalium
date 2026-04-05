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
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MLSResetConversationEventHandlerTest {

    @Test
    fun givenMLSContextIsNull_whenHandlingEvent_thenShouldDoNothing() = runTest {
        val (arrangement, handler) = arrange {
            withMLSContextNull()
        }

        handler.handle(arrangement.transactionContext, MLS_RESET_EVENT)

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.leaveGroup(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.updateGroupIdAndState(any(), any(), any(), any())
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
            arrangement.mlsConversationRepository.leaveGroup(any(), GROUP_ID)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), NEW_GROUP_ID)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.updateGroupIdAndState(
                CONVERSATION_ID,
                NEW_GROUP_ID,
                0,
                ConversationEntity.GroupState.PENDING_AFTER_RESET,
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
                arrangement.mlsConversationRepository.leaveGroup(any(), GROUP_ID)
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.hasEstablishedMLSGroup(
                    any(),
                    NEW_GROUP_ID
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.updateGroupIdAndState(
                    CONVERSATION_ID,
                    NEW_GROUP_ID,
                    newGroupEpoch,
                    ConversationEntity.GroupState.ESTABLISHED
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
                arrangement.mlsConversationRepository.leaveGroup(any(), GROUP_ID)
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.hasEstablishedMLSGroup(
                    any(),
                    NEW_GROUP_ID
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.updateGroupIdAndState(
                    CONVERSATION_ID,
                    NEW_GROUP_ID,
                    0L,
                    ConversationEntity.GroupState.PENDING_AFTER_RESET
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
                arrangement.mlsConversationRepository.leaveGroup(any(), event.groupID)
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.updateGroupIdAndState(
                    matching { it == event.conversationId },
                    matching { it == event.newGroupID },
                    0L,
                    matching { it == ConversationEntity.GroupState.PENDING_AFTER_RESET }
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
            arrangement.mlsConversationRepository.updateGroupIdAndState(
                CONVERSATION_ID,
                NEW_GROUP_ID,
                0L,
                ConversationEntity.GroupState.PENDING_AFTER_RESET
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
            arrangement.mlsConversationRepository.leaveGroup(any(), GROUP_ID)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.hasEstablishedMLSGroup(any(), NEW_GROUP_ID)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.updateGroupIdAndState(
                CONVERSATION_ID,
                NEW_GROUP_ID,
                newGroupEpoch,
                ConversationEntity.GroupState.ESTABLISHED
            )
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val mlsConversationRepository = mock<MLSConversationRepository>()

        suspend fun withLeaveGroupSucceeding() = apply {
            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withLeaveGroupFailing(failure: CoreFailure) = apply {
            everySuspend {
                mlsConversationRepository.leaveGroup(any(), any())
            } returns Either.Left(failure)
        }

        suspend fun withHasEstablishedMLSGroupReturning(hasGroup: Boolean) = apply {
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
            } returns Either.Right(hasGroup)
        }

        suspend fun withNewGroupEpoch(newGroupEpoch: Long) = apply {
            everySuspend {
                mlsContext.conversationEpoch(any())
            } returns newGroupEpoch.toULong()
        }

        suspend fun withHasEstablishedMLSGroupFailing(failure: MLSFailure) = apply {
            everySuspend {
                mlsConversationRepository.hasEstablishedMLSGroup(any(), any())
            } returns Either.Left(failure)
        }

        suspend fun withUpdateGroupIdAndStateSucceeding() = apply {
            everySuspend {
                mlsConversationRepository.updateGroupIdAndState(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateGroupIdAndStateFailing(failure: CoreFailure) = apply {
            everySuspend {
                mlsConversationRepository.updateGroupIdAndState(any(), any(), any(), any())
            } returns Either.Left(failure)
        }

        suspend fun withMLSContextNull() = apply {
            every { transactionContext.mls } returns null
        }

        suspend fun arrange() = run {
            block()
            this@Arrangement to MLSResetConversationEventHandlerImpl(
                mlsConversationRepository = mlsConversationRepository
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) =
            Arrangement(configuration).arrange()

        val GROUP_ID = GroupID("old_group_id")
        val NEW_GROUP_ID = GroupID("new_group_id")
        val CONVERSATION_ID = TestConversation.ID
        val USER_ID = TestUser.USER_ID

        val MLS_RESET_EVENT = Event.Conversation.MLSReset(
            id = "event_id",
            conversationId = CONVERSATION_ID,
            from = USER_ID,
            groupID = GROUP_ID,
            newGroupID = NEW_GROUP_ID,
        )
    }
}
