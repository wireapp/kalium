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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageLegalHoldTest : BaseMessageTest() {
    @Test
    fun givenLegalHoldMessageWasInserted_whenUpdatingMembersList_thenContentShouldHaveNewMembersList() = runTest {
        // given
        insertInitialData()
        val newMembers = listOf(OTHER_USER.id, OTHER_USER_2.id)
        messageDAO.updateLegalHoldMessageMembers(CONVERSATION_ID, ORIGINAL_MESSAGE_ID, newMembers)
        // when
        val result = messageDAO.getMessageById(ORIGINAL_MESSAGE_ID, CONVERSATION_ID)!!
        // then
        val content = result.content
        assertIs<MessageEntityContent.LegalHold>(content)
        assertEquals(newMembers, content.memberUserIdList)
    }
    override suspend fun insertInitialData() {
        super.insertInitialData()
        messageDAO.insertOrIgnoreMessage(ORIGINAL_MESSAGE)
    }
    private companion object {
        const val ORIGINAL_MESSAGE_ID = "originalMessageId"
        val CONVERSATION_ID = TEST_CONVERSATION_1.id
        val ORIGINAL_CONTENT = MessageEntityContent.LegalHold(listOf(OTHER_USER.id), MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS)
        val ORIGINAL_MESSAGE = newSystemMessageEntity(ORIGINAL_MESSAGE_ID, ORIGINAL_CONTENT, CONVERSATION_ID, OTHER_USER.id)
    }
}
