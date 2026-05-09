/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageMetadataDAO
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class MessageMetadataRepositoryTest {

    @Test
    fun givenMessageOriginalSender_whenGetOriginalSender_thenReturnsOriginalSender() = runTest {
        val convId = ConversationId("conversation_id", "domain")

        val (arrangement, repo) = Arrangement().arrange {
            withMessageOriginalSender(
                result = UserIDEntity("original_sender_id", "domain")
            )
        }

        repo.originalSenderId(
            conversationId = convId,
            messageId = "message_id"
        ).shouldSucceed {
            assertEquals("original_sender_id", it.value)
            assertEquals("domain", it.domain)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageMetaDataDAO.originalSenderId(eq(ConversationIDEntity("conversation_id", "domain")), eq("message_id"))
        }
    }

    @Test
    fun givenMessageNotExists_whenGetOriginalSender_thenDataNotFound() = runTest {
        val convId = ConversationId("conversation_id", "domain")

        val (arrangement, repo) = Arrangement().arrange {
            withMessageOriginalSender(
                result = null
            )
        }

        repo.originalSenderId(
            conversationId = convId,
            messageId = "message_id"
        ).shouldFail {
            assertEquals(StorageFailure.DataNotFound, it)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageMetaDataDAO.originalSenderId(eq(ConversationIDEntity("conversation_id", "domain")), eq("message_id"))
        }
    }

    private class Arrangement {
        val messageMetaDataDAO = mock<MessageMetadataDAO>()
        private val repo: MessageMetadataRepository = MessageMetadataSource(messageMetaDataDAO)

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, MessageMetadataRepository> {
            runBlocking { block() }
            return this to repo
        }

        suspend fun withMessageOriginalSender(result: UserIDEntity?) = apply {
            everySuspend {
                messageMetaDataDAO.originalSenderId(any(), any())
            } returns result
        }
    }
}
