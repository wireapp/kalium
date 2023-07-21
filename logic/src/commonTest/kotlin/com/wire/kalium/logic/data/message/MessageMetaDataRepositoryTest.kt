/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.arrangement.dao.MessageMetaDataDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.MessageMetaDataDAOArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageMetaDataRepositoryTest {

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

        verify(arrangement.messageMetaDataDAO)
            .suspendFunction(arrangement.messageMetaDataDAO::originalSenderId)
            .with(eq(ConversationIDEntity("conversation_id", "domain")),  eq("message_id"))
            .wasInvoked(exactly = once)
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

        verify(arrangement.messageMetaDataDAO)
            .suspendFunction(arrangement.messageMetaDataDAO::originalSenderId)
            .with(eq(ConversationIDEntity("conversation_id", "domain")),  eq("message_id"))
            .wasInvoked(exactly = once)
    }



    private class Arrangement : MessageMetaDataDAOArrangement by MessageMetaDataDAOArrangementImpl() {
        private val repo: MessageMetaDataRepository = MessageMetaDataDataSource(messageMetaDataDAO)

        fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, MessageMetaDataRepository> {
            block()
            return this to repo
        }
    }
}
