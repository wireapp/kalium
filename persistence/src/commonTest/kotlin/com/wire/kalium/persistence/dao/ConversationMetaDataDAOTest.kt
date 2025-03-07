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
package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAO
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationMetaDataDAOTest : BaseDatabaseTest() {

    private lateinit var conversationMetaDataDAO: ConversationMetaDataDAO
    private lateinit var conversationDAO: ConversationDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true)
        conversationMetaDataDAO = db.conversationMetaDataDAO
        conversationDAO = db.conversationDAO
    }

    private suspend fun insertConversation() {
        conversationDAO.insertConversation(conversationEntity)
    }

    @Test
    fun givenFreshDb_whenRequestInformedAboutDegradedMLS_thenDefaultValueReturned() = runTest {
        insertConversation()
        assertEquals(
            false,
            conversationMetaDataDAO.isInformedAboutDegradedMLSVerification(CONVERSATION_ID)
        )
    }

    @Test
    fun givenFreshDb_whenSetInformedAboutDegradedMLS_thenItIsApplied() = runTest {
        insertConversation()
        conversationMetaDataDAO.setInformedAboutDegradedMLSVerificationFlag(CONVERSATION_ID, true)
        assertEquals(
            true,
            conversationMetaDataDAO.isInformedAboutDegradedMLSVerification(CONVERSATION_ID)
        )
    }

    companion object {
        private val CONVERSATION_ID = QualifiedIDEntity("1", "wire.com")

        val conversationEntity = ConversationEntity(
            CONVERSATION_ID,
            "conversation1",
            ConversationEntity.Type.ONE_ON_ONE,
            "teamId",
            ConversationEntity.ProtocolInfo.Proteus,
            creatorId = "someValue",
            lastNotificationDate = null,
            lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = 5000L,
            userMessageTimer = null,
            archived = false,
            archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            wireCell = null,
        )
    }
}
