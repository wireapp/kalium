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
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.CompositeMessageDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
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

class CompositeMessageDataSourceTest {

    @Test
    fun givenTextAndOrderedButtons_whenUpdating_thenExactContentAndIdentifiersAreMappedToDao() = runTest {
        val (arrangement, repository) = arrangement()

        val result = repository.updateCompositeMessage(
            conversationId = conversationId,
            messageContent = compositeEdited,
            newMessageId = signalingMessageId,
            editInstant = signalingDate,
        )

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.updateCompositeMessageContent(
                conversationId = eq(conversationIdEntity),
                currentMessageId = eq(originalMessageId),
                editInstant = eq(signalingDate),
                newCompositeContent = matches { content ->
                    content.text == MessageEntityContent.Text(messageBody = "edited body") &&
                            content.buttonList.map { Triple(it.text, it.id, it.isSelected) } == listOf(
                        Triple("first", "first-id", false),
                        Triple("second", "second-id", true),
                    )
                },
                newMessageId = eq(signalingMessageId),
            )
        }
    }

    @Test
    fun givenNullText_whenUpdating_thenNullTextAndButtonsAreMappedToDao() = runTest {
        val (arrangement, repository) = arrangement()
        val contentWithoutText = compositeEdited.copy(newTextContent = null)

        val result = repository.updateCompositeMessage(
            conversationId = conversationId,
            messageContent = contentWithoutText,
            newMessageId = signalingMessageId,
            editInstant = signalingDate,
        )

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.updateCompositeMessageContent(
                conversationId = eq(conversationIdEntity),
                currentMessageId = eq(originalMessageId),
                editInstant = eq(signalingDate),
                newCompositeContent = matches { content ->
                    content.text == null &&
                            content.buttonList.map { Triple(it.text, it.id, it.isSelected) } == listOf(
                        Triple("first", "first-id", false),
                        Triple("second", "second-id", true),
                    )
                },
                newMessageId = eq(signalingMessageId),
            )
        }
    }

    @Test
    fun givenMessageDaoFailure_whenUpdating_thenFailureIsWrapped() = runTest {
        val expected = IllegalStateException("composite update failed")
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.messageDAO.updateCompositeMessageContent(
                eq(conversationIdEntity),
                eq(originalMessageId),
                eq(signalingDate),
                matches { true },
                eq(signalingMessageId),
            )
        } throws expected

        val result = repository.updateCompositeMessage(
            conversationId,
            compositeEdited,
            signalingMessageId,
            signalingDate,
        )

        assertEquals(Either.Left(StorageFailure.Generic(expected)), result)
    }

    @Test
    fun givenMessageDaoCancellation_whenUpdating_thenCancellationEscapes() = runTest {
        val expected = CancellationException("composite update cancelled")
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.messageDAO.updateCompositeMessageContent(
                eq(conversationIdEntity),
                eq(originalMessageId),
                eq(signalingDate),
                matches { true },
                eq(signalingMessageId),
            )
        } throws expected

        val actual = assertFailsWith<CancellationException> {
            repository.updateCompositeMessage(
                conversationId,
                compositeEdited,
                signalingMessageId,
                signalingDate,
            )
        }

        assertSame(expected, actual)
    }

    private fun arrangement(): Pair<Arrangement, CompositeMessageRepository> {
        val arrangement = Arrangement()
        return arrangement to CompositeMessageDataSource(
            compositeMessageDAO = arrangement.compositeMessageDAO,
            messageDAO = arrangement.messageDAO,
        )
    }

    private class Arrangement {
        val compositeMessageDAO = mock<CompositeMessageDAO>(mode = MockMode.autoUnit)
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
    }

    private companion object {
        const val originalMessageId = "original-message-id"
        const val signalingMessageId = "signaling-message-id"
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationIdEntity = QualifiedIDEntity("conversation-id", "wire.example")
        val signalingDate = Instant.parse("2026-08-19T10:15:30Z")
        val compositeEdited = MessageContent.CompositeEdited(
            editMessageId = originalMessageId,
            newTextContent = MessageContent.Text("edited body"),
            newButtonList = listOf(
                Button(text = "first", id = "first-id", isSelected = false),
                Button(text = "second", id = "second-id", isSelected = true),
            ),
        )
    }
}
