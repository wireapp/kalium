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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDAOTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val conversationEntity1 = newConversationEntity("Test1")
    private val conversationEntity2 = newConversationEntity("Test2")
    private val userEntity1 = newUserEntity("userEntity1")
    private val userEntity2 = newUserEntity("userEntity2")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingMessagesByUser_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING,
                senderName = userInQuestion.name!!
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING,
                senderName = userInQuestion.name!!
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ,
                senderName = userInQuestion.name!!
            ),
            newRegularMessageEntity(
                "4",
                conversationId = conversationEntity1.id,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.PENDING,
                senderName = otherUser.name!!
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)

        val result = messageDAO.getAllPendingMessagesFromUser(userInQuestion.id)

        assertContentEquals(expectedMessages, result)
    }

    @Test
    fun givenMessageIsInserted_whenInsertingAgainSameIdAndConversationId_thenShouldKeepOriginalData() = runTest {
        insertInitialData()
        val messageId = "testMessageId"
        val originalUser = userEntity1
        val replacementUser = userEntity2

        val originalMessage = newRegularMessageEntity(
            id = messageId,
            conversationId = conversationEntity1.id,
            senderUserId = originalUser.id,
            senderClientId = "initialClientId",
            content = MessageEntityContent.Text("Howdy"),
            date = Instant.DISTANT_FUTURE - 5.days,
            visibility = MessageEntity.Visibility.VISIBLE
        )

        messageDAO.insertOrIgnoreMessage(originalMessage)

        val replacementMessage = newRegularMessageEntity(
            id = originalMessage.id,
            conversationId = originalMessage.conversationId,
            senderUserId = replacementUser.id,
            senderClientId = "replacementClientId",
            content = MessageEntityContent.Knock(true),
            date = Instant.DISTANT_FUTURE,
            visibility = MessageEntity.Visibility.DELETED
        )

        messageDAO.insertOrIgnoreMessage(replacementMessage)

        val result = messageDAO.getMessageById(originalMessage.id, originalMessage.conversationId).first()

        assertNotNull(result)
        assertIs<MessageEntity.Regular>(result)
        assertEquals(originalMessage.id, result.id)
        assertEquals(originalMessage.conversationId, result.conversationId)
        assertEquals(originalMessage.senderUserId, result.senderUserId)
        assertEquals(originalMessage.senderClientId, result.senderClientId)
        assertEquals(originalMessage.content, result.content)
        assertEquals(originalMessage.date, result.date)
        assertEquals(originalMessage.visibility, result.visibility)
    }

    @Test
    fun givenMessagesNoRelevantMessagesAreInserted_whenGettingPendingMessagesByUser_thenAnEmptyListIsReturned() = runTest {
        insertInitialData()

        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val allMessages = listOf(
            newRegularMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ
            ),
            newRegularMessageEntity(
                "4",
                conversationId = conversationEntity1.id,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.PENDING
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)

        val result = messageDAO.getAllPendingMessagesFromUser(userInQuestion.id)

        assertTrue { result.isEmpty() }
    }

    @Test
    fun givenListOfMessages_WhenMarkMessageAsDeleted_OnlyTheTargetedMessageVisibilityIsDeleted() = runTest {
        insertInitialData()
        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val deleteMessageUuid = "3"
        val deleteMessageConversationId = conversationEntity1.id
        val visibleMessageUuid = "4"
        val visibleMessageConversationId = conversationEntity2.id

        val allMessages = listOf(
            newRegularMessageEntity(
                deleteMessageUuid,
                conversationId = deleteMessageConversationId,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.SENT
            ),
            newRegularMessageEntity(
                visibleMessageUuid,
                conversationId = visibleMessageConversationId,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.SENT
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        messageDAO.markMessageAsDeleted(deleteMessageUuid, deleteMessageConversationId)

        val resultDeletedMessage = messageDAO.getMessageById(deleteMessageUuid, deleteMessageConversationId)

        assertTrue { resultDeletedMessage.first()?.visibility == MessageEntity.Visibility.DELETED }

        val notDeletedMessage = messageDAO.getMessageById(visibleMessageUuid, visibleMessageConversationId)
        assertTrue { notDeletedMessage.first()?.visibility == MessageEntity.Visibility.VISIBLE }
    }

    @Test
    fun givenMessagesBySameMessageIdDifferentConvId_WhenMarkMessageAsDeleted_OnlyTheMessageWithCorrectConIdVisibilityIsDeleted() = runTest {
        insertInitialData()
        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val messageUuid = "sameMessageUUID"
        val deleteMessageConversationId = conversationEntity1.id
        val visibleMessageConversationId = conversationEntity2.id

        val allMessages = listOf(
            newRegularMessageEntity(
                messageUuid,
                conversationId = deleteMessageConversationId,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.SENT
            ),
            newRegularMessageEntity(
                messageUuid,
                conversationId = visibleMessageConversationId,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.SENT
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        messageDAO.markMessageAsDeleted(messageUuid, deleteMessageConversationId)

        val resultDeletedMessage = messageDAO.getMessageById(messageUuid, deleteMessageConversationId)

        assertTrue { resultDeletedMessage.first()?.visibility == MessageEntity.Visibility.DELETED }

        val notDeletedMessage = messageDAO.getMessageById(messageUuid, visibleMessageConversationId)
        assertTrue { notDeletedMessage.first()?.visibility == MessageEntity.Visibility.VISIBLE }
    }

    @Suppress("LongMethod")
    @Test
    fun givenMessagesAreInserted_whenGettingMessagesByConversation_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val otherConversation = conversationEntity2

        val visibilityInQuestion = MessageEntity.Visibility.VISIBLE
        val otherVisibility = MessageEntity.Visibility.HIDDEN

        val baseInstant = Instant.parse("2022-01-01T00:00:00.000Z")
        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!,
                date = baseInstant + 10.seconds
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!,
                date = baseInstant + 5.seconds
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "3",
                // different conversation
                conversationId = otherConversation.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!
            ),
            newRegularMessageEntity(
                "4",
                // different conversation, different visibility
                conversationId = otherConversation.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!
            ),
            newRegularMessageEntity(
                "5",
                // same conversation, different visibility
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = otherVisibility,
                senderName = userEntity1.name!!
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)
        val result = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversationInQuestion.id,
            limit = 10,
            offset = 0,
            visibility = listOf(visibilityInQuestion)
        )
        assertContentEquals(expectedMessages, result.first())
    }

    @Test
    fun givenMessagesAreInserted_whenGettingMessagesByConversationAfterDate_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val dateInQuestion = "2022-03-30T15:36:00.000Z"

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                // date after
                date = "2022-03-30T15:37:00.000Z".toInstant(),
                senderName = userEntity1.name!!
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:35:00.000Z".toInstant(),
                senderName = userEntity1.name!!
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)
        val result = messageDAO.observeMessagesByConversationAndVisibilityAfterDate(conversationInQuestion.id, dateInQuestion)
        assertContentEquals(expectedMessages, result.first())
    }

    @Test
    @Ignore
    fun givenUnreadMessageAssetContentType_WhenGettingUnreadMessageCount_ThenCounterShouldContainAssetContentType() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "assetMessage"
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.insertUser(userEntity1)

        messageDAO.insertOrIgnoreMessages(
            listOf(
                newRegularMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    content = MessageEntityContent.Asset(
                        1000,
                        assetName = "test name",
                        assetMimeType = "MP4",
                        assetDownloadStatus = null,
                        assetOtrKey = byteArrayOf(1),
                        assetSha256Key = byteArrayOf(1),
                        assetId = "assetId",
                        assetToken = "",
                        assetDomain = "domain",
                        assetEncryptionAlgorithm = "",
                        assetWidth = 111,
                        assetHeight = 111,
                        assetDurationMs = 10,
                        assetNormalizedLoudness = byteArrayOf(1),
                    )
                )
            )
        )

        // when
        val messageIds = messageDAO.observeUnreadMessages()
            .map { it.filter { previewEntity -> previewEntity.conversationId == conversationId }.map { message -> message.id } }
            .first()
        // then
        assertContains(messageIds, messageId)
    }

    @Test
    @Ignore
    fun givenUnreadMessageMissedCallContentType_WhenGettingUnreadMessageCount_ThenCounterShouldContainMissedCallContentType() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "missedCall"
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.insertUser(userEntity1)

        messageDAO.insertOrIgnoreMessages(
            listOf(
                newSystemMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    content = MessageEntityContent.MissedCall
                )
            )
        )

        // when
        val messageIds = messageDAO.observeUnreadMessages()
            .map { it.filter { previewEntity -> previewEntity.conversationId == conversationId }.map { message -> message.id } }
            .first()
        // then
        assertContains(messageIds, messageId)
    }

    @Test
    @Ignore
    fun givenMessagesArrivedBeforeUserSawTheConversation_whenGettingUnreadMessageCount_thenReturnZeroUnreadCount() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")

        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.insertUser(userEntity1)

        val message = buildList {
            // add 9 Message before the lastReadDate
            repeat(9) {
                add(
                    newRegularMessageEntity(
                        id = it.toString(), date = "2000-01-01T11:0$it:00.000Z".toInstant(),
                        conversationId = conversationId,
                        senderUserId = userEntity1.id,
                    )
                )
            }
        }

        messageDAO.insertOrIgnoreMessages(message)

        // when
        val messages = messageDAO.observeUnreadMessages()
            .map { it.filter { previewEntity -> previewEntity.conversationId == conversationId } }
            .first()
        // then
        assertEquals(0, messages.size)
    }

    @Test
    @Ignore
    fun givenMessagesArrivedAfterTheUserSawConversation_WhenGettingUnreadMessageCount_ThenReturnTheExpectedCount() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.insertUser(userEntity1)
        val readMessagesCount = 3
        val unreadMessagesCount = 2

        val message = buildList {
            // add 9 Message before the lastReadDate
            repeat(readMessagesCount) {
                add(
                    newRegularMessageEntity(
                        id = "read$it",
                        date = "2000-01-01T11:0$it:00.000Z".toInstant(),
                        conversationId = conversationId,
                        senderUserId = userEntity1.id,
                    )
                )
            }
            // add 9 Message past the lastReadDate
            repeat(unreadMessagesCount) {
                add(
                    newRegularMessageEntity(
                        id = "unread$it",
                        date = "2000-01-01T13:0$it:00.000Z".toInstant(),
                        conversationId = conversationId,
                        senderUserId = userEntity1.id,
                    )
                )
            }
        }

        messageDAO.insertOrIgnoreMessages(message)

        // when
        val messages = messageDAO.observeUnreadMessages()
            .map { it.filter { previewEntity -> previewEntity.conversationId == conversationId } }
            .first()
        // then
        assertEquals(unreadMessagesCount, messages.size)
    }

    @Test
    @Ignore
    fun givenDifferentUnreadMessageContentTypes_WhenGettingUnreadMessageCount_ThenSystemMessagesShouldBeNotCounted() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.insertUser(userEntity1)
        val readMessagesCount = 3
        val unreadMessagesCount = 2

        val message = buildList {
            // add 9 Message before the lastReadDate
            repeat(readMessagesCount) {
                add(
                    newRegularMessageEntity(
                        id = "read$it",
                        date = "2000-01-01T11:0$it:00.000Z".toInstant(),
                        conversationId = conversationId,
                        senderUserId = userEntity1.id,
                    )
                )
            }
            // add 9 Message past the lastReadDate
            repeat(unreadMessagesCount) {
                add(
                    newRegularMessageEntity(
                        id = "unread$it",
                        date = "2000-01-01T13:0$it:00.000Z".toInstant(),
                        conversationId = conversationId,
                        senderUserId = userEntity1.id,
                    )
                )
            }
        }

        messageDAO.insertOrIgnoreMessages(message)

        // when
        val messages = messageDAO.observeUnreadMessages()
            .map { it.filter { previewEntity -> previewEntity.conversationId == conversationId } }
            .first()

        assertNotNull(messages)
        // then
        assertEquals(unreadMessagesCount, messages.size)
    }

    @Test
    @Ignore
    fun givenUnreadMessageTextContentType_WhenGettingUnreadMessageCount_ThenCounterShouldContainTextContentType() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "textMessage"
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.insertUser(userEntity1)

        messageDAO.insertOrIgnoreMessages(
            listOf(
                newRegularMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    content = MessageEntityContent.Text("text")
                )
            )
        )

        // when
        val messageIds = messageDAO.observeUnreadMessages()
            .map { it.filter { previewEntity -> previewEntity.conversationId == conversationId }.map { message -> message.id } }
            .first()
        // then
        assertContains(messageIds, messageId)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingMessagesByConversationAfterDate_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val dateInQuestion = "2022-03-30T15:36:00.000Z"

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                // date after
                date = "2022-03-30T15:37:00.000Z".toInstant(),
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:38:00.000Z".toInstant(),
                senderName = userEntity1.name!!,
                expectsReadConfirmation = false
            ),

            newRegularMessageEntity(
                "3",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:39:00.000Z".toInstant(),
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)
        val result = messageDAO.getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(conversationInQuestion.id)
        assertEquals(2, result.size)
    }

    @Test
    fun givenMessageFailedToDecrypt_WhenMarkingAsResolved_ThenTheValuesShouldBeUpdated() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "textMessage"
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant()
            )
        )
        userDAO.insertUser(userEntity1)
        messageDAO.insertOrIgnoreMessages(
            listOf(
                newRegularMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    senderClientId = "someClient",
                    content = MessageEntityContent.FailedDecryption(null, false, userEntity1.id, "someClient")
                )
            )
        )

        // when
        messageDAO.markMessagesAsDecryptionResolved(conversationId, userEntity1.id, "someClient")

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId).firstOrNull()
        assertTrue((updatedMessage?.content as MessageEntityContent.FailedDecryption).isDecryptionResolved)
    }

    @Test
    @IgnoreIOS
    fun givenAPreviewGenericAssetMessageInDB_WhenReceivingAValidUpdateAssetMessage_ThenTheKeysAndVisibilityShouldBeCorrect() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "assetMessageId"
        val senderClientId = "someClient"
        val dummyOtrKey = byteArrayOf(1, 2, 3, 4, 5)
        val dummySha256Key = byteArrayOf(10, 9, 8, 7, 6)
        val previewAssetMessage = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderClientId = senderClientId,
            visibility = MessageEntity.Visibility.HIDDEN,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 1000,
                assetName = "some-asset.zip",
                assetMimeType = "application/zip",
                assetOtrKey = byteArrayOf(),
                assetSha256Key = byteArrayOf(),
                assetId = "some-asset-id",
                assetEncryptionAlgorithm = "AES/GCM"
            )
        )
        val finalAssetMessage = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:05.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderClientId = senderClientId,
            visibility = MessageEntity.Visibility.VISIBLE,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 0,
                assetMimeType = "*/*",
                assetOtrKey = dummyOtrKey,
                assetSha256Key = dummySha256Key,
                assetId = "some-asset-id",
                assetEncryptionAlgorithm = "AES/GCM"
            )
        )
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant()
            )
        )
        userDAO.insertUser(userEntity1)
        messageDAO.insertOrIgnoreMessages(listOf(previewAssetMessage))

        // when
        messageDAO.insertOrIgnoreMessages(listOf(finalAssetMessage))

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId).firstOrNull()
        assertTrue((updatedMessage?.content as MessageEntityContent.Asset).assetOtrKey.contentEquals(dummyOtrKey))
        assertTrue((updatedMessage.content as MessageEntityContent.Asset).assetSha256Key.contentEquals(dummySha256Key))
        assertTrue((updatedMessage.visibility == MessageEntity.Visibility.VISIBLE))
    }

    @Test
    @IgnoreIOS
    fun givenAPreviewGenericAssetMessageInDB_WhenReceivingAnAssetUpdateWithWrongKey_ThenTheMessageVisibilityShouldBeHidden() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "assetMessageId"
        val senderClientId = "someClient"
        val invalidOtrKey = byteArrayOf()
        val dummySha256Key = byteArrayOf(10, 9, 8, 7, 6)
        val previewAssetMessage = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderClientId = senderClientId,
            visibility = MessageEntity.Visibility.HIDDEN,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 1000,
                assetName = "some-asset.zip",
                assetMimeType = "application/zip",
                assetOtrKey = byteArrayOf(),
                assetSha256Key = byteArrayOf(),
                assetId = "some-asset-id",
                assetEncryptionAlgorithm = "AES/GCM"
            )
        )
        val finalAssetMessage = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:05.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderClientId = senderClientId,
            visibility = MessageEntity.Visibility.VISIBLE,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 0,
                assetMimeType = "*/*",
                assetOtrKey = invalidOtrKey,
                assetSha256Key = dummySha256Key,
                assetId = "some-asset-id",
                assetEncryptionAlgorithm = "AES/GCM"
            )
        )
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant()
            )
        )
        userDAO.insertUser(userEntity1)
        messageDAO.insertOrIgnoreMessages(listOf(previewAssetMessage))

        // when
        messageDAO.insertOrIgnoreMessages(listOf(finalAssetMessage))

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId).firstOrNull()
        assertTrue((updatedMessage?.visibility == MessageEntity.Visibility.HIDDEN))
    }

    @Test
    @IgnoreIOS
    fun givenAPreviewGenericAssetMessageInDB_WhenReceivingAnAssetUpdateFromDifferentSender_ThenTheMessageVisibilityShouldBeHidden() =
        runTest {
            // given
            val conversationId = QualifiedIDEntity("1", "someDomain")
            val messageId = "assetMessageId"
            val senderClientId = "someClient"
            val dummyOtrKey = byteArrayOf(1, 2, 3)
            val dummySha256Key = byteArrayOf(10, 9, 8, 7, 6)
            val previewAssetMessage = newRegularMessageEntity(
                id = messageId,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = conversationId,
                senderUserId = userEntity1.id,
                senderClientId = senderClientId,
                visibility = MessageEntity.Visibility.HIDDEN,
                content = MessageEntityContent.Asset(
                    assetSizeInBytes = 1000,
                    assetName = "some-asset.zip",
                    assetMimeType = "application/zip",
                    assetOtrKey = byteArrayOf(),
                    assetSha256Key = byteArrayOf(),
                    assetId = "some-asset-id",
                    assetEncryptionAlgorithm = "AES/GCM"
                )
            )
            val finalAssetMessage = newRegularMessageEntity(
                id = messageId,
                date = "2000-01-01T13:00:05.000Z".toInstant(),
                conversationId = conversationId,
                senderUserId = userEntity2.id,
                senderClientId = "impostorSenderClientId",
                visibility = MessageEntity.Visibility.VISIBLE,
                content = MessageEntityContent.Asset(
                    assetSizeInBytes = 0,
                    assetMimeType = "*/*",
                    assetOtrKey = dummyOtrKey,
                    assetSha256Key = dummySha256Key,
                    assetId = "some-asset-id",
                    assetEncryptionAlgorithm = "AES/GCM"
                )
            )
            conversationDAO.insertConversation(
                newConversationEntity(
                    id = conversationId,
                    lastReadDate = "2000-01-01T12:00:00.000Z".toInstant()
                )
            )
            userDAO.insertUser(userEntity1)
            messageDAO.insertOrIgnoreMessages(listOf(previewAssetMessage))

            // when
            messageDAO.insertOrIgnoreMessages(listOf(finalAssetMessage))

            // then
            val updatedMessage = messageDAO.getMessageById(messageId, conversationId).firstOrNull()
            assertTrue((updatedMessage?.visibility == MessageEntity.Visibility.HIDDEN))
        }

    @Suppress("LongMethod")
    @Test
    @IgnoreIOS
    fun givenAnAssetMessageInDB_WhenTryingAnAssetUpdate_thenIgnore() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "assetMessageId"
        val senderClientId = "someClient"
        val dummyOtrKey = byteArrayOf(1, 2, 3)
        val dummySha256Key = byteArrayOf(10, 9, 8, 7, 6)
        val initialUploadStatus = MessageEntity.UploadStatus.IN_PROGRESS
        val updatedUploadStatus = MessageEntity.UploadStatus.UPLOADED
        val initialDownloadStatus = MessageEntity.DownloadStatus.IN_PROGRESS
        val updatedDownloadStatus = MessageEntity.DownloadStatus.SAVED_INTERNALLY
        val initialAssetSize = 1000L
        val updatedAssetSize = 2000L
        val initialAssetName = "Some asset name.zip"
        val updatedAssetName = "updated asset name.svg"
        val initialAssetId = "some-id-124567"
        val updatedAssetId = "some-updated-id-0000"
        val initialDomain = "some@domain.com"
        val updatedAssetDomain = "some@future-domain.com"
        val initialMimeType = "application/zip"
        val updatedMimeType = "image/svg"
        val initialAssetEncryption = "AES/GCM"
        val updatedAssetEncryption = "AES/CBC"
        val initialAssetToken = "Some-token"
        val updatedAssetToken = "updated-token"
        val initialMetadataWidth = 100
        val initialMetadataHeight = 300
        val updatedMetadataHeight = null
        val updatedMetadataWidth = null

        val initialAssetMessage = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderClientId = senderClientId,
            visibility = MessageEntity.Visibility.VISIBLE,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = initialAssetSize,
                assetName = initialAssetName,
                assetMimeType = initialMimeType,
                assetOtrKey = dummyOtrKey,
                assetSha256Key = dummySha256Key,
                assetId = initialAssetId,
                assetDomain = initialDomain,
                assetEncryptionAlgorithm = initialAssetEncryption,
                assetUploadStatus = initialUploadStatus,
                assetDownloadStatus = initialDownloadStatus,
                assetToken = initialAssetToken,
                assetWidth = initialMetadataWidth,
                assetHeight = initialMetadataHeight
            )
        )
        val updatedAssetMessage = initialAssetMessage.copy(
            content = (initialAssetMessage.content as MessageEntityContent.Asset).copy(
                assetSizeInBytes = updatedAssetSize,
                assetName = updatedAssetName,
                assetMimeType = updatedMimeType,
                assetOtrKey = dummyOtrKey,
                assetSha256Key = dummySha256Key,
                assetId = updatedAssetId,
                assetDomain = updatedAssetDomain,
                assetEncryptionAlgorithm = updatedAssetEncryption,
                assetUploadStatus = updatedUploadStatus,
                assetDownloadStatus = updatedDownloadStatus,
                assetToken = updatedAssetToken,
                assetWidth = updatedMetadataWidth,
                assetHeight = updatedMetadataHeight
            )
        )
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant()
            )
        )
        userDAO.insertUser(userEntity1)
        messageDAO.insertOrIgnoreMessage(initialAssetMessage)

        // when
        messageDAO.insertOrIgnoreMessage(updatedAssetMessage)

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId).firstOrNull()
        val updatedMessageContent = updatedMessage?.content

        // asset values that should not be updated
        assertTrue((updatedMessage?.visibility == MessageEntity.Visibility.VISIBLE))
        assertTrue(updatedMessageContent is MessageEntityContent.Asset)
        assertEquals(initialAssetSize, updatedMessageContent.assetSizeInBytes)
        assertEquals(initialAssetName, updatedMessageContent.assetName)
        assertEquals(initialMimeType, updatedMessageContent.assetMimeType)
        assertEquals(initialAssetEncryption, updatedMessageContent.assetEncryptionAlgorithm)
        assertEquals(initialAssetToken, updatedMessageContent.assetToken)
        assertEquals(initialAssetId, updatedMessageContent.assetId)
        assertEquals(initialDomain, updatedMessageContent.assetDomain)
        assertTrue(updatedMessageContent.assetOtrKey.contentEquals(dummyOtrKey))
        assertTrue(updatedMessageContent.assetSha256Key.contentEquals(dummySha256Key))
        assertEquals(initialDownloadStatus, updatedMessageContent.assetDownloadStatus)
        assertEquals(initialMetadataWidth, updatedMessageContent.assetWidth)
        assertEquals(initialMetadataHeight, updatedMessageContent.assetHeight)
        assertEquals(initialUploadStatus, updatedMessageContent.assetUploadStatus)
    }

    @Test
    fun givenMultipleMessagesWithTheSameIdFromTheSameUser_whenInserting_theOnlyTheFirstOneIsInserted() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "textMessage"
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )
        userDAO.insertUser(userEntity1)

        val message1 = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderName = userEntity1.name!!,
            senderClientId = "someClient",
            content = MessageEntityContent.Text("hello, world!", emptyList())
        )

        val message2 = message1.copy(content = MessageEntityContent.Text("new message content", emptyList()))
        messageDAO.insertOrIgnoreMessages(
            listOf(message1, message2)
        )

        // when
        messageDAO.getMessageById(messageId, conversationId).first().also {
            assertEquals(message1, it)
        }
    }

    @Test
    fun givenMultipleMessagesWithTheSameIdFromDifferentUsers_whenInserting_theOnlyTheFirstOneIsInserted() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "textMessage"
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )
        userDAO.insertUser(userEntity1)
        userDAO.insertUser(userEntity2)

        val messageFromUser1 = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderName = userEntity1.name!!,
            senderClientId = "someClient",
            content = MessageEntityContent.Text("hello, world!", emptyList())
        )

        val messageFromUser2 = messageFromUser1.copy(
            senderName = userEntity2.name!!,
            senderUserId = userEntity2.id,
            content = MessageEntityContent.Text("new message content", emptyList())
        )
        messageDAO.insertOrIgnoreMessages(
            listOf(messageFromUser1, messageFromUser2)
        )

        // when
        messageDAO.getMessageById(messageId, conversationId).first().also {
            assertEquals(messageFromUser1, it)
        }
    }

    @Test
    fun givenAConversationWithUnConfirmedMessages_whenGetPendingToConfirmMessages_itReturnsCorrectList() = runTest {
        val conversationLastReadDate = "2000-01-01T12:00:00.000Z".toInstant()
        val messageDateAfterLastReadDate = "2000-01-01T13:00:00.000Z".toInstant()
        val messageDateBeforeLastReadDate = "2000-01-01T11:00:00.000Z".toInstant()

        // having a conversation with last readDate
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(conversationEntity1.copy(lastReadDate = conversationLastReadDate))

        // having a list of messages after the lastReadDate
        val allMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity2.id,
                date = messageDateBeforeLastReadDate,
                expectsReadConfirmation = true
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity2.id,
                date = messageDateAfterLastReadDate,
                expectsReadConfirmation = true
            ),
            newRegularMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity2.id,
                date = messageDateAfterLastReadDate,
                expectsReadConfirmation = true
            )
        )

        val expected = listOf("2", "3")

        messageDAO.insertOrIgnoreMessages(allMessages)

        // the list should be correct
        val result = messageDAO.getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(conversationEntity1.id)

        assertEquals(expected.sorted(), result.sorted())
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
    }
}
