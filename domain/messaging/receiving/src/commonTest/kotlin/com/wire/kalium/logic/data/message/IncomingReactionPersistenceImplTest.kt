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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class IncomingReactionPersistenceImplTest {

    @Test
    fun givenIncomingReaction_whenUpdating_thenOriginalPayloadAndMappedIdsAreForwardedToDao() = runTest {
        val (arrangement, persistence) = arrangement()

        val result = persistence.updateReaction(messageId, conversationId, senderUserId, date, reactions)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.reactionDAO.updateReactions(
                eq(messageId),
                eq(conversationIdEntity),
                eq(senderUserIdEntity),
                eq(date),
                eq(reactions),
            )
        }
    }

    @Test
    fun givenReactionDaoFailure_whenUpdating_thenFailureIsWrapped() = runTest {
        val expectedException = IllegalStateException("reaction update failed")
        val (arrangement, persistence) = arrangement()
        everySuspend {
            arrangement.reactionDAO.updateReactions(
                eq(messageId),
                eq(conversationIdEntity),
                eq(senderUserIdEntity),
                eq(date),
                eq(reactions),
            )
        } throws expectedException

        val result = persistence.updateReaction(messageId, conversationId, senderUserId, date, reactions)

        assertEquals(Either.Left(StorageFailure.Generic(expectedException)), result)
    }

    @Test
    fun givenReactionDaoCancellation_whenUpdating_thenCancellationEscapes() = runTest {
        val expectedException = CancellationException("reaction update cancelled")
        val (arrangement, persistence) = arrangement()
        everySuspend {
            arrangement.reactionDAO.updateReactions(
                eq(messageId),
                eq(conversationIdEntity),
                eq(senderUserIdEntity),
                eq(date),
                eq(reactions),
            )
        } throws expectedException

        val actualException = assertFailsWith<CancellationException> {
            persistence.updateReaction(messageId, conversationId, senderUserId, date, reactions)
        }

        assertSame(expectedException, actualException)
    }

    private fun arrangement(): Pair<Arrangement, IncomingReactionPersistence> {
        val arrangement = Arrangement()
        return arrangement to IncomingReactionPersistenceImpl(arrangement.reactionDAO)
    }

    private class Arrangement {
        val reactionDAO = mock<ReactionDAO>(mode = MockMode.autoUnit)
    }

    private companion object {
        const val messageId = "message-id"
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationIdEntity = ConversationIDEntity("conversation-id", "wire.example")
        val senderUserId = UserId("sender-id", "wire.example")
        val senderUserIdEntity = UserIDEntity("sender-id", "wire.example")
        val date = Instant.parse("2026-08-19T10:15:30Z")
        val reactions = setOf("👍", "❤️")
    }
}
