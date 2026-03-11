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

import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageSystemContentTest : BaseMessageTest() {

    // ============================================================
    // MEMBER_CHANGE Tests (list_1 = member_change_list, enum_1 = member_change_type)
    // ============================================================

    @Test
    fun givenMemberChangeMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val memberList = listOf(OTHER_USER.id, OTHER_USER_2.id)
        val message = newSystemMessageEntity(
            id = "memberChange1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.MemberChange(
                memberList,
                MessageEntity.MemberChangeType.ADDED
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.MemberChange>(content)
        assertEquals(memberList, content.memberUserIdList)
        assertEquals(MessageEntity.MemberChangeType.ADDED, content.memberChangeType)
    }

    @Test
    fun givenMemberChangeMessage_whenRetrievedViaView_thenFieldsAreMappedCorrectly() = runTest {
        // given
        insertInitialData()
        val memberList = listOf(OTHER_USER.id)
        val message = newSystemMessageEntity(
            id = "memberChange2",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id,
            content = MessageEntityContent.MemberChange(
                memberList,
                MessageEntity.MemberChangeType.REMOVED
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then - verify via view
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.MemberChange
        assertEquals(memberList, content.memberUserIdList)
        assertEquals(MessageEntity.MemberChangeType.REMOVED, content.memberChangeType)
    }

    @Test
    fun givenMultipleMemberChangeTypes_whenInserted_thenAllTypesArePreserved() = runTest {
        // given
        insertInitialData()
        val changeTypes = listOf(
            MessageEntity.MemberChangeType.ADDED,
            MessageEntity.MemberChangeType.REMOVED,
            MessageEntity.MemberChangeType.CREATION_ADDED,
            MessageEntity.MemberChangeType.FAILED_TO_ADD_FEDERATION,
            MessageEntity.MemberChangeType.FAILED_TO_ADD_LEGAL_HOLD,
            MessageEntity.MemberChangeType.FAILED_TO_ADD_UNKNOWN,
            MessageEntity.MemberChangeType.FEDERATION_REMOVED,
            MessageEntity.MemberChangeType.REMOVED_FROM_TEAM
        )

        // when
        changeTypes.forEachIndexed { index, type ->
            val message = newSystemMessageEntity(
                id = "memberChange_$index",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.MemberChange(
                    listOf(OTHER_USER.id),
                    type
                )
            )
            messageDAO.insertOrIgnoreMessage(message)
        }

        // then
        changeTypes.forEachIndexed { index, expectedType ->
            val result = messageDAO.getMessageById("memberChange_$index", TEST_CONVERSATION_1.id)
            assertNotNull(result)
            val content = result.content as MessageEntityContent.MemberChange
            assertEquals(expectedType, content.memberChangeType)
        }
    }

    @Test
    fun givenMemberChangeWithEmptyList_whenInserted_thenEmptyListIsPreserved() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "memberChangeEmpty",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.MemberChange(
                emptyList(),
                MessageEntity.MemberChangeType.ADDED
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.MemberChange
        assertTrue(content.memberUserIdList.isEmpty())
    }

    // ============================================================
    // FAILED_DECRYPT Tests (blob_1 = data, boolean_1 = resolved, integer_1 = error_code)
    // ============================================================

    @Test
    fun givenFailedDecryptMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val encryptedData = "encrypted_data_blob".encodeToByteArray()
        val message = newRegularMessageEntity(
            id = "failedDecrypt1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id,
            senderClientId = "client1",
            content = MessageEntityContent.FailedDecryption(
                encodedData = encryptedData,
                code = 404,
                isDecryptionResolved = false,
                senderUserId = OTHER_USER.id,
                senderClientId = "client1"
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content
        assertIs<MessageEntityContent.FailedDecryption>(content)
        assertEquals(encryptedData.contentToString(), content.encodedData?.contentToString())
        assertEquals(404, content.code)
        assertEquals(false, content.isDecryptionResolved)
    }

    @Test
    fun givenFailedDecryptMessage_whenMarkingAsResolved_thenIsDecryptionResolvedIsUpdated() = runTest {
        // given
        insertInitialData()
        val message = newRegularMessageEntity(
            id = "failedDecrypt2",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id,
            senderClientId = "client2",
            content = MessageEntityContent.FailedDecryption(
                encodedData = null,
                code = null,
                isDecryptionResolved = false,
                senderUserId = OTHER_USER.id,
                senderClientId = "client2"
            )
        )
        messageDAO.insertOrIgnoreMessage(message)

        // when
        messageDAO.markProteusMessagesAsDecryptionResolved(OTHER_USER.id, "client2")

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content
        assertIs<MessageEntityContent.FailedDecryption>(content)
        assertEquals(true, content.isDecryptionResolved)
    }

    @Test
    fun givenFailedDecryptWithNullData_whenInserted_thenNullIsPreserved() = runTest {
        // given
        insertInitialData()
        val message = newRegularMessageEntity(
            id = "failedDecryptNull",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id,
            senderClientId = "client3",
            content = MessageEntityContent.FailedDecryption(
                encodedData = null,
                code = null,
                isDecryptionResolved = false,
                senderUserId = OTHER_USER.id,
                senderClientId = "client3"
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content
        assertIs<MessageEntityContent.FailedDecryption>(content)
        assertNull(content.encodedData)
        assertNull(content.code)
    }

    // ============================================================
    // CONVERSATION_RENAMED Tests (text_1 = conversation_name)
    // ============================================================

    @Test
    fun givenConversationRenamedMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val newName = "New Conversation Name"
        val message = newSystemMessageEntity(
            id = "renamed1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed(newName)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.ConversationRenamed>(content)
        assertEquals(newName, content.conversationName)
    }

    @Test
    fun givenConversationRenamedWithSpecialCharacters_whenInserted_thenSpecialCharsArePreserved() = runTest {
        // given
        insertInitialData()
        val specialName = "Conv w/ 特殊字符 & émojis 🎉"
        val message = newSystemMessageEntity(
            id = "renamedSpecial",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed(specialName)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.ConversationRenamed
        assertEquals(specialName, content.conversationName)
    }

    // ============================================================
    // RECEIPT_MODE Tests (boolean_1 = receipt_mode)
    // ============================================================

    @Test
    fun givenNewConversationReceiptModeMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "receiptMode1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.NewConversationReceiptMode(receiptMode = true)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.NewConversationReceiptMode>(content)
        assertEquals(true, content.receiptMode)
    }

    @Test
    fun givenConversationReceiptModeChanged_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "receiptModeChanged",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationReceiptModeChanged(receiptMode = false)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.ConversationReceiptModeChanged>(content)
        assertEquals(false, content.receiptMode)
    }

    // ============================================================
    // CONVERSATION_TIMER_CHANGED Tests (integer_1 = message_timer)
    // ============================================================

    @Test
    fun givenConversationTimerChangedMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val timerDuration = 86400000L // 24 hours in milliseconds
        val message = newSystemMessageEntity(
            id = "timer1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationMessageTimerChanged(timerDuration)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.ConversationMessageTimerChanged>(content)
        assertEquals(timerDuration, content.messageTimer)
    }

    @Test
    fun givenConversationTimerDisabled_whenInserted_thenNullTimerIsPreserved() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "timerNull",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationMessageTimerChanged(null)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.ConversationMessageTimerChanged
        assertNull(content.messageTimer)
    }

    // ============================================================
    // FEDERATION_TERMINATED Tests (list_1 = domains, enum_1 = federation_type)
    // ============================================================

    @Test
    fun givenFederationTerminatedMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val domains = listOf("domain1.com", "domain2.com")
        val message = newSystemMessageEntity(
            id = "federation1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.Federation(
                domains,
                MessageEntity.FederationType.DELETE
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.Federation>(content)
        assertEquals(domains, content.domainList)
        assertEquals(MessageEntity.FederationType.DELETE, content.type)
    }

    @Test
    fun givenMultipleFederationTypes_whenInserted_thenAllTypesArePreserved() = runTest {
        // given
        insertInitialData()
        val types = listOf(
            MessageEntity.FederationType.DELETE,
            MessageEntity.FederationType.CONNECTION_REMOVED
        )

        // when
        types.forEachIndexed { index, type ->
            val message = newSystemMessageEntity(
                id = "federation_$index",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.Federation(
                    listOf("example.com"),
                    type
                )
            )
            messageDAO.insertOrIgnoreMessage(message)
        }

        // then
        types.forEachIndexed { index, expectedType ->
            val result = messageDAO.getMessageById("federation_$index", TEST_CONVERSATION_1.id)
            assertNotNull(result)
            val content = result.content as MessageEntityContent.Federation
            assertEquals(expectedType, content.type)
        }
    }

    // ============================================================
    // CONVERSATION_PROTOCOL_CHANGED Tests (enum_1 = protocol)
    // ============================================================

    @Test
    fun givenConversationProtocolChangedMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "protocol1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationProtocolChanged(
                ConversationEntity.Protocol.MLS
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.ConversationProtocolChanged>(content)
        assertEquals(ConversationEntity.Protocol.MLS, content.protocol)
    }

    @Test
    fun givenMultipleProtocolTypes_whenInserted_thenAllProtocolsArePreserved() = runTest {
        // given
        insertInitialData()
        val protocols = listOf(
            ConversationEntity.Protocol.PROTEUS,
            ConversationEntity.Protocol.MLS,
            ConversationEntity.Protocol.MIXED
        )

        // when
        protocols.forEachIndexed { index, protocol ->
            val message = newSystemMessageEntity(
                id = "protocol_$index",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.ConversationProtocolChanged(protocol)
            )
            messageDAO.insertOrIgnoreMessage(message)
        }

        // then
        protocols.forEachIndexed { index, expectedProtocol ->
            val result = messageDAO.getMessageById("protocol_$index", TEST_CONVERSATION_1.id)
            assertNotNull(result)
            val content = result.content as MessageEntityContent.ConversationProtocolChanged
            assertEquals(expectedProtocol, content.protocol)
        }
    }

    // ============================================================
    // LEGAL_HOLD Tests (list_1 = members, enum_1 = legal_hold_type)
    // ============================================================

    @Test
    fun givenLegalHoldMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val memberList = listOf(OTHER_USER.id, OTHER_USER_2.id)
        val message = newSystemMessageEntity(
            id = "legalHold1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.LegalHold(
                memberList,
                MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.LegalHold>(content)
        assertEquals(memberList, content.memberUserIdList)
        assertEquals(MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS, content.type)
    }

    @Test
    fun givenLegalHoldMessage_whenUpdatingMembersList_thenContentIsUpdated() = runTest {
        // given
        insertInitialData()
        val initialMembers = listOf(OTHER_USER.id)
        val message = newSystemMessageEntity(
            id = "legalHoldUpdate",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.LegalHold(
                initialMembers,
                MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS
            )
        )
        messageDAO.insertOrIgnoreMessage(message)

        // when
        val newMembers = listOf(OTHER_USER.id, OTHER_USER_2.id)
        messageDAO.updateLegalHoldMessageMembers(
            TEST_CONVERSATION_1.id,
            message.id,
            newMembers
        )

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.LegalHold
        assertEquals(newMembers, content.memberUserIdList)
    }

    @Test
    fun givenMultipleLegalHoldTypes_whenInserted_thenAllTypesArePreserved() = runTest {
        // given
        insertInitialData()
        val types = listOf(
            MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS,
            MessageEntity.LegalHoldType.DISABLED_FOR_MEMBERS,
            MessageEntity.LegalHoldType.ENABLED_FOR_CONVERSATION,
            MessageEntity.LegalHoldType.DISABLED_FOR_CONVERSATION
        )

        // when
        types.forEachIndexed { index, type ->
            val message = newSystemMessageEntity(
                id = "legalHold_$index",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.LegalHold(
                    listOf(OTHER_USER.id),
                    type
                )
            )
            messageDAO.insertOrIgnoreMessage(message)
        }

        // then
        types.forEachIndexed { index, expectedType ->
            val result = messageDAO.getMessageById("legalHold_$index", TEST_CONVERSATION_1.id)
            assertNotNull(result)
            val content = result.content as MessageEntityContent.LegalHold
            assertEquals(expectedType, content.type)
        }
    }

    // ============================================================
    // CONVERSATION_APPS_ENABLED_CHANGED Tests (boolean_1 = is_apps_enabled)
    // ============================================================

    @Test
    fun givenConversationAppsEnabledMessage_whenInserted_thenCanBeRetrievedWithCorrectContent() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "appsEnabled1",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationAppsAccessChanged(isEnabled = true)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        val content = result!!.content
        assertNotNull(result)
        assertIs<MessageEntityContent.ConversationAppsAccessChanged>(content)
        assertEquals(true, content.isEnabled)
    }

    @Test
    fun givenConversationAppsDisabled_whenInserted_thenFalseValueIsPreserved() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "appsDisabled",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationAppsAccessChanged(isEnabled = false)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.ConversationAppsAccessChanged
        assertEquals(false, content.isEnabled)
    }

    // ============================================================
    // CASCADE Deletion Tests
    // ============================================================

    @Test
    fun givenSystemMessage_whenParentMessageIsDeleted_thenSystemContentIsAlsoDeleted() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "cascadeDelete",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.MemberChange(
                listOf(OTHER_USER.id),
                MessageEntity.MemberChangeType.ADDED
            )
        )
        messageDAO.insertOrIgnoreMessage(message)

        // verify message exists
        assertNotNull(messageDAO.getMessageById(message.id, message.conversationId))

        // when
        messageDAO.deleteMessage(message.id, message.conversationId)

        // then - system content should be deleted via CASCADE
        assertNull(messageDAO.getMessageById(message.id, message.conversationId))
    }

    // this test showed an issue if a delete message is sent to a system message it will break mapping and the client ID would be null
    // solution: maybe add "DELETED" to the client ID when it is missing and we mark as deleted
    @Ignore
    @Test
    fun givenMultipleSystemMessages_whenMarkingAsDeleted_thenSystemContentIsCleared() = runTest {
        // given
        insertInitialData()
        val message = newSystemMessageEntity(
            id = "markDeleted",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed("Test Name")
        )
        messageDAO.insertOrIgnoreMessage(message)

        // when
        messageDAO.markMessageAsDeleted(message.id, message.conversationId)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        assertEquals(MessageEntity.Visibility.DELETED, result.visibility)
        // Content should be replaced with empty text content
        assertIs<MessageEntityContent.Text>(result.content)
    }

    // ============================================================
    // Bulk Operations Tests
    // ============================================================

    @Test
    fun givenMultipleSystemMessageTypes_whenInserted_thenAllCanBeRetrieved() = runTest {
        // given
        insertInitialData()
        val messages = listOf(
            newSystemMessageEntity(
                id = "bulk1",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.MemberChange(
                    listOf(OTHER_USER.id),
                    MessageEntity.MemberChangeType.ADDED
                )
            ),
            newSystemMessageEntity(
                id = "bulk2",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.ConversationRenamed("Bulk Test")
            ),
            newSystemMessageEntity(
                id = "bulk3",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.LegalHold(
                    listOf(OTHER_USER.id),
                    MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS
                )
            ),
            newSystemMessageEntity(
                id = "bulk4",
                conversationId = TEST_CONVERSATION_1.id,
                senderUserId = SELF_USER.id,
                content = MessageEntityContent.ConversationProtocolChanged(
                    ConversationEntity.Protocol.MLS
                )
            )
        )

        // when
        messageDAO.insertOrIgnoreMessages(messages)

        // then
        messages.forEach { expectedMessage ->
            val result = messageDAO.getMessageById(expectedMessage.id, expectedMessage.conversationId)
            assertNotNull(result)
            assertEquals(expectedMessage.content::class, result.content::class)
        }
    }

    // ============================================================
    // Edge Cases and Special Scenarios
    // ============================================================

    @Test
    fun givenSystemMessageWithMaxLengthText_whenInserted_thenTextIsPreserved() = runTest {
        // given
        insertInitialData()
        val longName = "A".repeat(5000) // Very long conversation name
        val message = newSystemMessageEntity(
            id = "longText",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed(longName)
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.ConversationRenamed
        assertEquals(longName, content.conversationName)
    }

    @Test
    fun givenSystemMessageWithLargeBlobData_whenInserted_thenBlobIsPreserved() = runTest {
        // given
        insertInitialData()
        val largeBlob = ByteArray(10000) { it.toByte() } // 10KB blob
        val message = newRegularMessageEntity(
            id = "largeBlob",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = OTHER_USER.id,
            content = MessageEntityContent.FailedDecryption(
                encodedData = largeBlob,
                code = 500,
                isDecryptionResolved = false,
                senderUserId = OTHER_USER.id,
                senderClientId = "client",
            )
        )

        // when
        messageDAO.insertOrIgnoreMessage(message)

        // then
        val result = messageDAO.getMessageById(message.id, message.conversationId)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.FailedDecryption
        assertEquals(largeBlob.size, content.encodedData?.size)
    }

    @Test
    fun givenDuplicateSystemMessage_whenInsertedWithIgnore_thenOriginalIsPreserved() = runTest {
        // given
        insertInitialData()
        val originalMessage = newSystemMessageEntity(
            id = "duplicate",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed("Original Name")
        )
        messageDAO.insertOrIgnoreMessage(originalMessage)

        // when - try to insert duplicate with different content
        val duplicateMessage = newSystemMessageEntity(
            id = "duplicate", // Same ID
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed("New Name")
        )
        messageDAO.insertOrIgnoreMessage(duplicateMessage)

        // then - original should be preserved
        val result = messageDAO.getMessageById("duplicate", TEST_CONVERSATION_1.id)
        assertNotNull(result)
        val content = result.content as MessageEntityContent.ConversationRenamed
        assertEquals("Original Name", content.conversationName)
    }

    @Test
    fun givenSystemMessagesInDifferentConversations_whenQueried_thenOnlyCorrectConversationMessagesReturned() = runTest {
        // given
        insertInitialData()
        val message1 = newSystemMessageEntity(
            id = "conv1Msg",
            conversationId = TEST_CONVERSATION_1.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed("Conv1")
        )
        val message2 = newSystemMessageEntity(
            id = "conv2Msg",
            conversationId = TEST_CONVERSATION_2.id,
            senderUserId = SELF_USER.id,
            content = MessageEntityContent.ConversationRenamed("Conv2")
        )

        // when
        messageDAO.insertOrIgnoreMessages(listOf(message1, message2))

        // then
        val conv1Result = messageDAO.getMessageById("conv1Msg", TEST_CONVERSATION_1.id)
        val conv2Result = messageDAO.getMessageById("conv2Msg", TEST_CONVERSATION_2.id)
        val crossResult = messageDAO.getMessageById("conv1Msg", TEST_CONVERSATION_2.id)

        assertNotNull(conv1Result)
        assertNotNull(conv2Result)
        assertNull(crossResult) // Message from conv1 should not be found in conv2

        assertEquals("Conv1", (conv1Result.content as MessageEntityContent.ConversationRenamed).conversationName)
        assertEquals("Conv2", (conv2Result.content as MessageEntityContent.ConversationRenamed).conversationName)
    }
}
