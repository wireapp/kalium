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
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
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

class IncomingLastReadPersistenceImplTest {

    @Test
    fun givenConversationDates_whenUpdating_thenInputAndOutputIdsAreMapped() = runTest {
        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        everySuspend {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(eq(conversationDatesEntity))
        } returns hasUnreadByConversationEntity
        val persistence = IncomingLastReadPersistenceImpl(conversationDAO)

        val result = persistence.updateReadDatesAndGetHasUnreadEvents(conversationDates)

        assertEquals(Either.Right(hasUnreadByConversation), result)
        verifySuspend(VerifyMode.exactly(1)) {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(eq(conversationDatesEntity))
        }
    }

    @Test
    fun givenEmptyDaoResult_whenUpdating_thenEmptySuccessIsReturned() = runTest {
        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        everySuspend {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(eq(emptyMap()))
        } returns emptyMap()
        val persistence = IncomingLastReadPersistenceImpl(conversationDAO)

        assertEquals(Either.Right(emptyMap()), persistence.updateReadDatesAndGetHasUnreadEvents(emptyMap()))
    }

    @Test
    fun givenDaoFailure_whenUpdating_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("last-read update failed")
        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        everySuspend {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(eq(conversationDatesEntity))
        } throws expected
        val persistence = IncomingLastReadPersistenceImpl(conversationDAO)

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            persistence.updateReadDatesAndGetHasUnreadEvents(conversationDates),
        )
    }

    @Test
    fun givenDaoCancellation_whenUpdating_thenCancellationEscapes() = runTest {
        val expected = CancellationException("last-read update cancelled")
        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        everySuspend {
            conversationDAO.updateReadDatesAndGetHasUnreadEvents(eq(conversationDatesEntity))
        } throws expected
        val persistence = IncomingLastReadPersistenceImpl(conversationDAO)

        val actual = assertFailsWith<CancellationException> {
            persistence.updateReadDatesAndGetHasUnreadEvents(conversationDates)
        }

        assertSame(expected, actual)
    }

    private companion object {
        val firstConversationId = ConversationId("first-conversation", "wire.example")
        val secondConversationId = ConversationId("second-conversation", "other.example")
        val firstConversationIdEntity = QualifiedIDEntity("first-conversation", "wire.example")
        val secondConversationIdEntity = QualifiedIDEntity("second-conversation", "other.example")
        val firstDate = Instant.parse("2026-08-19T10:15:30Z")
        val secondDate = Instant.parse("2026-08-19T10:16:30Z")
        val conversationDates = mapOf(firstConversationId to firstDate, secondConversationId to secondDate)
        val conversationDatesEntity = mapOf(firstConversationIdEntity to firstDate, secondConversationIdEntity to secondDate)
        val hasUnreadByConversationEntity = mapOf(firstConversationIdEntity to true, secondConversationIdEntity to false)
        val hasUnreadByConversation = mapOf(firstConversationId to true, secondConversationId to false)
    }
}
