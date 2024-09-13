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

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetDAO
import com.wire.kalium.persistence.dao.asset.AssetEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.utils.IgnoreIOS
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newSystemMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserDetailsEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass")
class MessageDAOTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var receiptDao: ReceiptDAO
    private lateinit var assetDao: AssetDAO

    private val conversationEntity1 = newConversationEntity("Test1")
    private val conversationEntity2 = newConversationEntity("Test2")
    private val conversationEntity3 = newConversationEntity("Test3")
    private val userEntity1 = newUserEntity("userEntity1")
    private val userEntity2 = newUserEntity("userEntity2")
    private val userDetailsEntity1 = newUserDetailsEntity("userEntity1")
    private val userDetailsEntity2 = newUserDetailsEntity("userEntity2")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        receiptDao = db.receiptDAO
        assetDao = db.assetDAO
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingMessagesByUser_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val userInQuestion = userDetailsEntity1
        val otherUser = userDetailsEntity2

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING,
                senderName = userInQuestion.name!!,
                sender = userInQuestion
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING,
                senderName = userInQuestion.name!!,
                sender = userInQuestion
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ,
                senderName = userInQuestion.name!!,
                sender = userInQuestion
            ),
            newRegularMessageEntity(
                "4",
                conversationId = conversationEntity1.id,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.PENDING,
                senderName = otherUser.name!!,
                sender = otherUser
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

        val result = messageDAO.getMessageById(originalMessage.id, originalMessage.conversationId)

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

        assertTrue { resultDeletedMessage?.visibility == MessageEntity.Visibility.DELETED }

        val notDeletedMessage = messageDAO.getMessageById(visibleMessageUuid, visibleMessageConversationId)
        assertTrue { notDeletedMessage?.visibility == MessageEntity.Visibility.VISIBLE }
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

        assertTrue { resultDeletedMessage?.visibility == MessageEntity.Visibility.DELETED }

        val notDeletedMessage = messageDAO.getMessageById(messageUuid, visibleMessageConversationId)
        assertTrue { notDeletedMessage?.visibility == MessageEntity.Visibility.VISIBLE }

        assertFalse { messageDAO.observeUnreadEvents().first().containsKey(deleteMessageConversationId) }
    }

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
                date = baseInstant + 10.seconds,
                sender = userDetailsEntity1
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!,
                date = baseInstant + 5.seconds,
                sender = userDetailsEntity1
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
                senderName = userEntity1.name!!,
                sender = userDetailsEntity1
            ),
            newRegularMessageEntity(
                "4",
                // different conversation, different visibility
                conversationId = otherConversation.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!,
                sender = userDetailsEntity1
            ),
            newRegularMessageEntity(
                "5",
                // same conversation, different visibility
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = otherVisibility,
                senderName = userEntity1.name!!,
                sender = userDetailsEntity1
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
                senderName = userEntity1.name!!,
                sender = userDetailsEntity1
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
                senderName = userEntity1.name!!,
                sender = userDetailsEntity1
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)
        val result = messageDAO.observeMessagesByConversationAndVisibilityAfterDate(conversationInQuestion.id, dateInQuestion)
        assertContentEquals(expectedMessages, result.first())
    }

    @Test
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

        userDAO.upsertUser(userEntity1)

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
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]!!.map { event -> event.type } }
            .first()
        // then
        assertContains(messageTypes, UnreadEventTypeEntity.MESSAGE)
    }

    @Test
    fun givenAssetMessageWithMimeType_WhenGettingAssetMessages_ThenListShouldContainAssetMessageWithMimeType() = runTest {
        // given
        val domain = "domain"
        val conversationId = QualifiedIDEntity("1", domain)
        val assetId = "assetId"
        val messageId = "assetMessage"
        val mimeType = "image/png"
        conversationDAO.insertConversation(
            newConversationEntity(id = conversationId)
        )

        userDAO.upsertUser(userEntity1)

        messageDAO.insertOrIgnoreMessages(
            listOf(
                // only valid asset
                newRegularMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    content = MessageEntityContent.Asset(
                        1000,
                        assetName = "test name",
                        assetMimeType = mimeType,
                        assetOtrKey = byteArrayOf(1),
                        assetSha256Key = byteArrayOf(1),
                        assetId = assetId,
                        assetToken = "",
                        assetDomain = domain,
                        assetEncryptionAlgorithm = "",
                        assetWidth = 111,
                        assetHeight = 111,
                    )
                ),
                // asset with different mime type
                newRegularMessageEntity(
                    id = messageId + "1",
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    content = MessageEntityContent.Asset(
                        1000,
                        assetName = "test name",
                        assetMimeType = "image/jpeg",
                        assetOtrKey = byteArrayOf(1),
                        assetSha256Key = byteArrayOf(1),
                        assetId = assetId,
                        assetToken = "",
                        assetDomain = domain,
                        assetEncryptionAlgorithm = "",
                        assetWidth = 111,
                        assetHeight = 111,
                    )
                ),
                // ephemeral message asset
                newRegularMessageEntity(
                    id = messageId + "2",
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    expireAfterMs = 2000,
                    content = MessageEntityContent.Asset(
                        1000,
                        assetName = "test name",
                        assetMimeType = "image/png",
                        assetOtrKey = byteArrayOf(1),
                        assetSha256Key = byteArrayOf(1),
                        assetId = assetId,
                        assetToken = "",
                        assetDomain = domain,
                        assetEncryptionAlgorithm = "",
                        assetWidth = 111,
                        assetHeight = 111,
                    )
                ),
                // asset with null or 0 width/height
                newRegularMessageEntity(
                    id = messageId + "3",
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    expireAfterMs = 2000,
                    content = MessageEntityContent.Asset(
                        1000,
                        assetName = "test name",
                        assetMimeType = "image/png",
                        assetOtrKey = byteArrayOf(1),
                        assetSha256Key = byteArrayOf(1),
                        assetId = assetId,
                        assetToken = "",
                        assetDomain = domain,
                        assetEncryptionAlgorithm = "",
                        assetWidth = null,
                        assetHeight = 0,
                    )
                ),
            )
        )

        assetDao.insertAsset(
            AssetEntity(
                key = assetId,
                dataSize = 123,
                dataPath = "dataPath",
                domain = domain,
                downloadedDate = null
            )
        )

        // when
        val assetMessages = messageDAO.getImageMessageAssets(conversationId, setOf(mimeType), 100, 0)

        // then
        assertEquals(1, assetMessages.size)
        assertEquals(assetId, assetMessages.first().assetId)
    }

    @Test
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

        userDAO.upsertUser(userEntity1)

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
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]!!.map { event -> event.type } }
            .first()
        // then
        assertContains(messageTypes, UnreadEventTypeEntity.MISSED_CALL)
    }

    @Test
    fun givenMessagesArrivedBeforeUserSawTheConversation_whenGettingUnreadMessageCount_thenReturnZeroUnreadCount() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")

        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.upsertUser(userEntity1)

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
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]?.map { event -> event.type } ?: listOf() }
            .first()
        // then
        assertEquals(0, messageTypes.size)
    }

    @Test
    fun givenMessagesArrivedAfterTheUserSawConversation_WhenGettingUnreadMessageCount_ThenReturnTheExpectedCount() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.upsertUser(userEntity1)
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
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]!!.map { event -> event.type } }
            .first()
        // then
        assertEquals(unreadMessagesCount, messageTypes.size)
    }

    @Test
    fun givenDifferentUnreadMessageContentTypes_WhenGettingUnreadMessageCount_ThenSystemMessagesShouldBeNotCounted() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.upsertUser(userEntity1)
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
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]!!.map { event -> event.type } }
            .first()

        // then
        assertEquals(unreadMessagesCount, messageTypes.size)
    }

    @Test
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

        userDAO.upsertUser(userEntity1)

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
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]!!.map { event -> event.type } }
            .first()
        // then
        assertContains(messageTypes, UnreadEventTypeEntity.MESSAGE)
    }

    @Test
    fun givenUnreadMessages_WhenInsertingSelfMessage_thenReturnZeroUnreadCount() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(
                id = conversationId,
                lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            )
        )

        userDAO.upsertUser(userEntity1)
        val unreadMessagesCount = 2

        val message = buildList {
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
            // add self message past the lastReadDate and other messages
            add(
                newRegularMessageEntity(
                    id = "unreadSelf",
                    date = "2000-01-01T14:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = selfUserId,
                )
            )
        }

        messageDAO.insertOrIgnoreMessages(message)

        // when
        val messageTypes = messageDAO.observeUnreadEvents()
            .map { it[conversationId]!!.map { event -> event.type } }
            .first()

        // then
        assertEquals(unreadMessagesCount, messageTypes.size)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingConfirmableMessageIds_thenOnlyMessagesWithinDateAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val initialDate = Instant.parse("2022-03-30T15:41:00.000Z")
        val finalDate = initialDate + 60.seconds
        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                // date after start
                date = initialDate + 1.seconds,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            ),
            newRegularMessageEntity(
                "5",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date matching end
                date = finalDate,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            ),
            newRegularMessageEntity(
                "6",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date beforeEnd
                date = finalDate - 1.seconds,
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
                // date equal start
                date = initialDate,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            ),
            newRegularMessageEntity(
                "3",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before start
                date = initialDate - 1.seconds,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = false
            ),
            newRegularMessageEntity(
                "4",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date after end
                date = finalDate + 1.seconds,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)
        val result = messageDAO.getMessageIdsThatExpectReadConfirmationWithinDates(
            conversationInQuestion.id,
            initialDate,
            finalDate
        ).sorted()
        assertContentEquals(expectedMessages.map { it.id }.sorted(), result)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingMessagesByConversationAfterDate_thenMessagesFromSelfAreNotReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val initialDate = Instant.parse("2022-03-30T15:41:00.000Z")
        val finalDate = initialDate + 60.seconds
        val expectedMessages = listOf(
            newRegularMessageEntity(
                // This will return
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                // date after
                date = initialDate + 1.seconds,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = true
            )
        )

        val allMessages = expectedMessages + listOf(
            // Self message
            // This will NOT return
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = selfUserId,
                status = MessageEntity.Status.READ,
                // date after
                date = initialDate + 1.seconds,
                senderName = userEntity1.name!!,
                expectsReadConfirmation = false
            )
        )

        messageDAO.insertOrIgnoreMessages(allMessages)
        val result = messageDAO.getMessageIdsThatExpectReadConfirmationWithinDates(
            conversationInQuestion.id,
            initialDate,
            finalDate
        )
        assertEquals(expectedMessages.size, result.size)
    }

    @Test
    fun givenMessageFailedToDecrypt_WhenMarkingAsResolved_ThenTheValuesShouldBeUpdated() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val conversationId2 = QualifiedIDEntity("2", "someDomain")

        val messageId = "textMessage"
        val messageId2 = "textMessage2"
        conversationDAO.insertConversation(
            newConversationEntity(id = conversationId)
        )

        conversationDAO.insertConversation(
            newConversationEntity(id = conversationId2)
        )

        userDAO.upsertUser(userEntity1)
        messageDAO.insertOrIgnoreMessages(
            listOf(
                newRegularMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    senderClientId = "someClient",
                    content = MessageEntityContent.FailedDecryption(null, 333, false, userEntity1.id, "someClient")
                ),
                newRegularMessageEntity(
                    id = messageId2,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId2,
                    senderUserId = userEntity1.id,
                    senderClientId = "someClient",
                    content = MessageEntityContent.FailedDecryption(null, 333, false, userEntity1.id, "someClient")
                )
            )
        )

        // when
        messageDAO.markMessagesAsDecryptionResolved(conversationId, userEntity1.id, "someClient")

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId)
        assertTrue((updatedMessage?.content as MessageEntityContent.FailedDecryption).isDecryptionResolved)
        val updatedMessage2 = messageDAO.getMessageById(messageId2, conversationId2)
        assertTrue((updatedMessage2?.content as MessageEntityContent.FailedDecryption).isDecryptionResolved)
        assertEquals(333, (updatedMessage2.content as MessageEntityContent.FailedDecryption).code)
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
        userDAO.upsertUser(userEntity1)
        messageDAO.insertOrIgnoreMessages(listOf(previewAssetMessage))

        // when
        messageDAO.insertOrIgnoreMessages(listOf(finalAssetMessage))

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId)
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
        userDAO.upsertUser(userEntity1)
        messageDAO.insertOrIgnoreMessages(listOf(previewAssetMessage))

        // when
        messageDAO.insertOrIgnoreMessages(listOf(finalAssetMessage))

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId)
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
            userDAO.upsertUser(userEntity1)
            messageDAO.insertOrIgnoreMessages(listOf(previewAssetMessage))

            // when
            messageDAO.insertOrIgnoreMessages(listOf(finalAssetMessage))

            // then
            val updatedMessage = messageDAO.getMessageById(messageId, conversationId)
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
        userDAO.upsertUser(userEntity1)
        messageDAO.insertOrIgnoreMessage(initialAssetMessage)

        // when
        messageDAO.insertOrIgnoreMessage(updatedAssetMessage)

        // then
        val updatedMessage = messageDAO.getMessageById(messageId, conversationId)
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
        assertEquals(initialMetadataWidth, updatedMessageContent.assetWidth)
        assertEquals(initialMetadataHeight, updatedMessageContent.assetHeight)
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
        userDAO.upsertUser(userEntity1)

        val message1 = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderName = userEntity1.name!!,
            sender = userDetailsEntity1,
            senderClientId = "someClient",
            content = MessageEntityContent.Text("hello, world!", emptyList())
        )

        val message2 = message1.copy(content = MessageEntityContent.Text("new message content", emptyList()))
        messageDAO.insertOrIgnoreMessages(
            listOf(message1, message2)
        )

        // when
        messageDAO.getMessageById(messageId, conversationId).also {
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
        userDAO.upsertUser(userEntity1)
        userDAO.upsertUser(userEntity2)

        val messageFromUser1 = newRegularMessageEntity(
            id = messageId,
            date = "2000-01-01T13:00:00.000Z".toInstant(),
            conversationId = conversationId,
            senderUserId = userEntity1.id,
            senderName = userEntity1.name!!,
            senderClientId = "someClient",
            content = MessageEntityContent.Text("hello, world!", emptyList()),
            sender = userDetailsEntity1
        )

        val messageFromUser2 = messageFromUser1.copy(
            senderName = userEntity2.name!!,
            senderUserId = userEntity2.id,
            content = MessageEntityContent.Text("new message content", emptyList()),
            sender = userDetailsEntity1
        )
        messageDAO.insertOrIgnoreMessages(
            listOf(messageFromUser1, messageFromUser2)
        )

        // when
        messageDAO.getMessageById(messageId, conversationId).also {
            assertEquals(messageFromUser1, it)
        }
    }

    @Test
    fun whenUpdatingMessagesTableAfterSendingAMessage_thenMessageIsMarkedAsSentDateIsUpdatedAndPendingMessagesTimeIsAdjusted() = runTest {
        val messageToSend = newRegularMessageEntity(
            id = "messageToSend",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            date = Instant.fromEpochMilliseconds(123),
            expectsReadConfirmation = true,
            status = MessageEntity.Status.PENDING
        )

        val pendingMessage = newRegularMessageEntity(
            id = "pendingMessage",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            date = Instant.fromEpochMilliseconds(125),
            expectsReadConfirmation = true,
            status = MessageEntity.Status.PENDING
        )

        conversationDAO.insertConversation(conversationEntity1)
        userDAO.upsertUsers(listOf(userEntity1))
        messageDAO.insertOrIgnoreMessages(listOf(messageToSend, pendingMessage))

        messageDAO.promoteMessageToSentUpdatingServerTime(conversationEntity1.id, messageToSend.id, Instant.fromEpochMilliseconds(124), 1)

        messageDAO.getMessageById(messageToSend.id, conversationEntity1.id).also {
            assertNotNull(it)
            assertEquals(MessageEntity.Status.SENT, it.status)
            assertEquals(Instant.fromEpochMilliseconds(124), it.date)
        }

        messageDAO.getMessageById(pendingMessage.id, conversationEntity1.id).also {
            assertNotNull(it)
            assertEquals(MessageEntity.Status.PENDING, it.status)
            assertEquals(Instant.fromEpochMilliseconds(125 + 1), it.date)
        }
    }

    @Test
    fun whenUpdatingMessagesTableAfterSendingAMessageAndServerTimeIsNull_thenMessageIsMarkedAsSentAndPendingMessagesTimeIsAdjusted() =
        runTest {
            val messageToSend = newRegularMessageEntity(
                id = "messageToSend",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                date = Instant.fromEpochMilliseconds(123),
                expectsReadConfirmation = true,
                status = MessageEntity.Status.PENDING
            )

            val pendingMessage = newRegularMessageEntity(
                id = "pendingMessage",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                date = Instant.fromEpochMilliseconds(125),
                expectsReadConfirmation = true,
                status = MessageEntity.Status.PENDING
            )

            conversationDAO.insertConversation(conversationEntity1)
            userDAO.upsertUsers(listOf(userEntity1))
            messageDAO.insertOrIgnoreMessages(listOf(messageToSend, pendingMessage))

            messageDAO.promoteMessageToSentUpdatingServerTime(conversationEntity1.id, messageToSend.id, null, 1)

            messageDAO.getMessageById(messageToSend.id, conversationEntity1.id).also {
                assertNotNull(it)
                assertEquals(MessageEntity.Status.SENT, it.status)
                assertEquals(Instant.fromEpochMilliseconds(123), it.date)
            }

            messageDAO.getMessageById(pendingMessage.id, conversationEntity1.id).also {
                assertNotNull(it)
                assertEquals(MessageEntity.Status.PENDING, it.status)
                assertEquals(Instant.fromEpochMilliseconds(125 + 1), it.date)
            }
        }

    @Test
    fun givenConversationReceiptModeChangedContentType_WhenGettingMessageById_ThenContentShouldBeAsInserted() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "ConversationReceiptModeChanged Message"
        conversationDAO.insertConversation(newConversationEntity(id = conversationId))
        userDAO.upsertUser(userEntity1)

        // when
        messageDAO.insertOrIgnoreMessages(
            listOf(
                newSystemMessageEntity(
                    id = messageId,
                    date = "2000-01-01T13:00:00.000Z".toInstant(),
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    content = MessageEntityContent.ConversationReceiptModeChanged(
                        receiptMode = true
                    )
                )
            )
        )

        // then
        val result = messageDAO.getMessageById(
            id = messageId,
            conversationId = conversationId
        )
        assertTrue {
            result?.content == MessageEntityContent.ConversationReceiptModeChanged(receiptMode = true)
        }
    }

    @Test
    fun givenOneOnOneConversations_WhenPersistSystemMessageInBulk_ThenPersistedForAllConversations() = runTest {
        // given
        val conversationId1 = QualifiedIDEntity("1", "someDomain")
        conversationDAO.insertConversation(newConversationEntity(id = conversationId1))
        val conversationId2 = QualifiedIDEntity("2", "someDomain")
        conversationDAO.insertConversation(newConversationEntity(id = conversationId2))

        val messageId = "systemMessage"
        userDAO.upsertUser(userEntity1)

        // when
        messageDAO.persistSystemMessageToAllConversations(
            newSystemMessageEntity(
                id = messageId,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = conversationId1,
                senderUserId = userEntity1.id,
                content = MessageEntityContent.HistoryLost
            )
        )

        // then
        val result1 = messageDAO.getMessageById(
            id = messageId,
            conversationId = conversationId1
        )
        val result2 = messageDAO.getMessageById(
            id = messageId,
            conversationId = conversationId2
        )
        assertTrue {
            result1?.content == MessageEntityContent.HistoryLost &&
                    result2?.content == MessageEntityContent.HistoryLost
        }
    }

    @Test
    fun givenMixedTypeOfConversations_WhenPersistSystemMessageInBulk_ThenMessageShouldPersistedOnlyForOneOnOneAndGroups() = runTest {
        // given
        val selfConversation = QualifiedIDEntity("selfConversation", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(id = selfConversation).copy(
                type = ConversationEntity.Type.SELF
            )
        )
        val oneOnOneConversation = QualifiedIDEntity("oneOnOneConversation", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(id = oneOnOneConversation).copy(
                type = ConversationEntity.Type.ONE_ON_ONE
            )
        )
        val groupConversation = QualifiedIDEntity("groupConversation", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(id = groupConversation).copy(
                type = ConversationEntity.Type.GROUP
            )
        )
        val connectionPendingConversation = QualifiedIDEntity("connectionPendingConversation", "someDomain")
        conversationDAO.insertConversation(
            newConversationEntity(id = connectionPendingConversation).copy(
                type = ConversationEntity.Type.CONNECTION_PENDING
            )
        )
        val messageId = "systemMessage"
        userDAO.upsertUser(userEntity1)

        // when
        messageDAO.persistSystemMessageToAllConversations(
            newSystemMessageEntity(
                id = messageId,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = selfConversation,
                senderUserId = userEntity1.id,
                content = MessageEntityContent.HistoryLost
            )
        )

        // then
        val resultForSelfConversation = messageDAO.getMessageById(
            id = messageId,
            conversationId = selfConversation
        )
        val resultForOneOnOneConversation = messageDAO.getMessageById(
            id = messageId,
            conversationId = oneOnOneConversation
        )
        val resultForGroupConversation = messageDAO.getMessageById(
            id = messageId,
            conversationId = groupConversation
        )
        val resultForConnectionPendingConversation = messageDAO.getMessageById(
            id = messageId,
            conversationId = connectionPendingConversation
        )

        assertTrue {
            resultForSelfConversation == null &&
                    resultForOneOnOneConversation?.content == MessageEntityContent.HistoryLost &&
                    resultForGroupConversation?.content == MessageEntityContent.HistoryLost &&
                    resultForConnectionPendingConversation == null
        }
    }

    @Test
    fun givenReplyMessage_WhenQuotedMessageExist_MessageShouldContainQuotedDetails() = runTest {
        insertInitialData()
        val quotedUser = userEntity1
        val otherUser = userEntity2
        val conversationId = conversationEntity1.id

        val quotedMessageId = "quotedId"
        val replyMessageId = "replyId"

        val allMessages = listOf(
            newRegularMessageEntity(
                id = quotedMessageId,
                conversationId = conversationId,
                senderUserId = quotedUser.id,
            ),
            newRegularMessageEntity(
                id = replyMessageId,
                senderUserId = otherUser.id,
                conversationId = conversationId,
                content = MessageEntityContent.Text(quotedMessageId = quotedMessageId, messageBody = "Sure")
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        val replyMessage = messageDAO.getMessageById(replyMessageId, conversationId)
        assertTrue {
            replyMessage != null
                    && replyMessage.content is MessageEntityContent.Text
                    && (replyMessage.content as MessageEntityContent.Text).quotedMessage?.id == quotedMessageId
        }
    }

    @Test
    fun givenReplyMessage_WhenQuotedMessageNotExist_MessageShouldContainOnlyQuotedMessageId() = runTest {
        insertInitialData()
        val otherUser = userEntity2
        val conversationId = conversationEntity1.id

        val quotedMessageId = "quotedId"
        val replyMessageId = "replyId"

        val allMessages = listOf(
            newRegularMessageEntity(
                id = replyMessageId,
                senderUserId = otherUser.id,
                conversationId = conversationId,
                content = MessageEntityContent.Text(quotedMessageId = quotedMessageId, messageBody = "Sure")
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        val replyMessage = messageDAO.getMessageById(replyMessageId, conversationId)
        assertTrue {
            replyMessage != null
                    && replyMessage.content is MessageEntityContent.Text
                    && (replyMessage.content as MessageEntityContent.Text).quotedMessageId == quotedMessageId
                    && (replyMessage.content as MessageEntityContent.Text).quotedMessage == null
        }
    }

    @Test
    fun givenAFederatedConversation_WhenSendingAMessageWithPartialSuccess_ThenTheUsersIdsWithFailuresShouldBeInserted() = runTest {
        // given
        val conversationId = QualifiedIDEntity("1", "someDomain")
        val messageId = "Conversation MessageSent With Partial Success"
        conversationDAO.insertConversation(newConversationEntity(id = conversationId))
        userDAO.upsertUser(userEntity1)
        userDAO.upsertUser(userEntity2)

        messageDAO.insertOrIgnoreMessages(
            listOf(
                newRegularMessageEntity(
                    messageId,
                    conversationId = conversationId,
                    senderUserId = userEntity1.id,
                    status = MessageEntity.Status.PENDING,
                    // date after
                    date = "2022-03-30T15:37:00.000Z".toInstant(),
                    senderName = userEntity1.name!!,
                    expectsReadConfirmation = true
                )
            )
        )

        // when
        messageDAO.insertFailedRecipientDelivery(
            messageId, conversationId, listOf(userEntity1.id, userEntity2.id), RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED
        )

        // then
        val result = messageDAO.getMessageById(
            id = messageId,
            conversationId = conversationId
        )

        assertTrue {
            ((result as MessageEntity.Regular).deliveryStatus as DeliveryStatusEntity.PartialDelivery)
                .recipientsFailedDelivery.size == 2
        }
    }

    @Test
    fun givenAMultipleReadReceiptForAMessage_whenQueryingTheMessage_thenTheMessageHasExpectedStatus() = runTest {
        // given
        val usersReadTheMessage = listOf(newUserEntity("2"), newUserEntity("3"), newUserEntity("4"))

        userDAO.upsertUsers(listOf(userEntity1) + usersReadTheMessage)
        conversationDAO.insertConversation(
            conversationEntity1.copy(
                lastReadDate = "2022-03-30T15:40:00.000Z".toInstant()
            )
        )

        val message =
            newRegularMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:38:00.000Z".toInstant(),
                senderName = userEntity1.name!!,
                expectsReadConfirmation = false
            )
        messageDAO.insertOrIgnoreMessage(message)

        usersReadTheMessage.forEach {
            receiptDao.insertReceipts(
                userId = it.id,
                conversationId = conversationEntity1.id,
                date = Instant.DISTANT_FUTURE,
                type = ReceiptTypeEntity.READ,
                messageIds = listOf(
                    "1"
                )
            )
        }

        // when
        val result = messageDAO.getMessageById(message.id, conversationEntity1.id)

        // then
        assertNotNull(result)
        assertEquals(result.status, MessageEntity.Status.READ)
        assertTrue(result.readCount == 3L)
    }

    @Test
    fun givenADeliveryReceiptForAMessage_whenQueryingTheMessage_thenTheMessageHasExpectedStatus() = runTest {
        // given
        userDAO.upsertUsers(listOf(userEntity1))
        conversationDAO.insertConversation(
            conversationEntity1.copy(
                lastReadDate = "2022-03-30T15:40:00.000Z".toInstant()
            )
        )

        val message =
            newRegularMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.DELIVERED,
                // date before
                date = "2022-03-30T15:38:00.000Z".toInstant(),
                senderName = userEntity1.name!!,
                expectsReadConfirmation = false
            )
        messageDAO.insertOrIgnoreMessage(message)
        receiptDao.insertReceipts(
            userId = userEntity1.id,
            conversationId = conversationEntity1.id,
            date = Instant.DISTANT_FUTURE,
            type = ReceiptTypeEntity.DELIVERY,
            messageIds = listOf(
                "1"
            )
        )

        // when
        val result = messageDAO.getMessageById(message.id, conversationEntity1.id)

        // then
        assertNotNull(result)
        assertEquals(MessageEntity.Status.DELIVERED, result.status)
        assertTrue(result.readCount == 0L)
    }

    @Test
    fun givenExistingMessagesAtSource_whenMovingMessages_thenMessagesAreAccessibleAtDestination() = runTest {
        // given
        val source = conversationEntity1
        val destination = conversationEntity2
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(source)
        conversationDAO.insertConversation(destination)

        val allMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                senderUserId = userEntity1.id,
                conversationId = source.id,
                content = MessageEntityContent.Text(messageBody = "Message 1")
            ),
            newRegularMessageEntity(
                id = "2",
                senderUserId = userEntity1.id,
                conversationId = source.id,
                content = MessageEntityContent.Text(messageBody = "Message 2")
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        // when
        messageDAO.moveMessages(source.id, destination.id)

        // then
        val retrievedMessages = messageDAO.getMessagesByConversationAndVisibility(
            destination.id,
            10,
            0,
            listOf(MessageEntity.Visibility.VISIBLE)
        ).first()

        assertEquals(
            allMessages.map { it.content }.toSet(),
            retrievedMessages.map { it.content }.toSet()
        )
    }

    @Test
    fun givenExistingMessagesAtSourceAndDestination_whenMovingMessages_thenMessagesAreAccessibleAtDestination() = runTest {
        // given
        val source = conversationEntity1
        val destination = conversationEntity2
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(source)
        conversationDAO.insertConversation(destination)

        val allMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                senderUserId = userEntity1.id,
                conversationId = source.id,
                content = MessageEntityContent.Text(messageBody = "Message 1")
            ),
            newRegularMessageEntity(
                id = "2",
                senderUserId = userEntity1.id,
                conversationId = destination.id,
                content = MessageEntityContent.Text(messageBody = "Message 2")
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        // when
        messageDAO.moveMessages(source.id, destination.id)

        // then
        val retrievedMessages = messageDAO.getMessagesByConversationAndVisibility(
            destination.id,
            10,
            0,
            listOf(MessageEntity.Visibility.VISIBLE)
        ).first()

        assertEquals(
            allMessages.map { it.content }.toSet(),
            retrievedMessages.map { it.content }.toSet()
        )
    }

    @Test
    fun givenNoExistingMessagesAtSource_whenMovingMessages_thenExistingMessagesAreAccessibleAtDestination() = runTest {
        // given
        val source = conversationEntity1
        val destination = conversationEntity2
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(source)
        conversationDAO.insertConversation(destination)

        val allMessages = listOf(
            newRegularMessageEntity(
                id = "1",
                senderUserId = userEntity1.id,
                conversationId = destination.id,
                content = MessageEntityContent.Text(messageBody = "Message 1")
            ),
            newRegularMessageEntity(
                id = "2",
                senderUserId = userEntity1.id,
                conversationId = destination.id,
                content = MessageEntityContent.Text(messageBody = "Message 2")
            )
        )
        messageDAO.insertOrIgnoreMessages(allMessages)

        // when
        messageDAO.moveMessages(source.id, destination.id)

        // then
        val retrievedMessages = messageDAO.getMessagesByConversationAndVisibility(
            destination.id,
            10,
            0,
            listOf(MessageEntity.Visibility.VISIBLE)
        ).first()

        assertEquals(
            allMessages.map { it.content }.toSet(),
            retrievedMessages.map { it.content }.toSet()
        )
    }

    @Test
    fun givenMessagesAreInserted_whenMessageIsSelected_thenReturnMessagePosition() = runTest {
        // given
        insertInitialData()

        val otherUser = userEntity2

        val expectedPosition = 1

        val message1 = newRegularMessageEntity(
            "1",
            conversationId = conversationEntity1.id,
            status = MessageEntity.Status.SENT,
            senderUserId = otherUser.id,
            senderName = otherUser.name!!,
            content = MessageEntityContent.Text("message1"),
            date = Instant.parse("2022-03-30T15:36:00.000Z")
        )
        val message2 = newRegularMessageEntity(
            "2",
            conversationId = conversationEntity1.id,
            status = MessageEntity.Status.SENT,
            senderUserId = otherUser.id,
            senderName = otherUser.name!!,
            content = MessageEntityContent.Text("message2"),
            date = Instant.parse("2022-03-30T15:36:01.000Z")
        )
        val message3 = newRegularMessageEntity(
            "3",
            conversationId = conversationEntity1.id,
            status = MessageEntity.Status.SENT,
            senderUserId = otherUser.id,
            senderName = otherUser.name!!,
            content = MessageEntityContent.Text("message3"),
            date = Instant.parse("2022-03-30T15:36:02.000Z")
        )

        val messages = listOf(
            message1,
            message2,
            message3
        )

        messageDAO.insertOrIgnoreMessages(messages)

        // when
        val result = messageDAO.getSearchedConversationMessagePosition(
            conversationId = conversationEntity1.id,
            messageId = message3.id
        )

        // then
        assertEquals(expectedPosition, result)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingLastMessagesByConversations_thenOnlyLastMessagesForEachConversationAreReturned() = runTest {
        // given
        insertInitialData()
        fun createMessage(id: String, conversationId: QualifiedIDEntity, date: Instant) = newRegularMessageEntity(
            id = id,
            conversationId = conversationId,
            date = date,
            senderUserId = userEntity1.id,
            senderName = userEntity1.name!!,
            sender = userDetailsEntity1
        )

        val baseInstant = Instant.parse("2022-01-01T00:00:00.000Z")
        val messages = listOf(
            createMessage(id = "1A", conversationId = conversationEntity1.id, date = baseInstant),
            createMessage(id = "2A", conversationId = conversationEntity2.id, date = baseInstant + 5.seconds),
            createMessage(id = "1B", conversationId = conversationEntity1.id, date = baseInstant + 1.seconds),
        )
        messageDAO.insertOrIgnoreMessages(messages)
        // when
        val result = messageDAO.getLastMessagesByConversations(
            listOf(conversationEntity1.id, conversationEntity2.id, conversationEntity3.id)
        )
        // then
        assertEquals(messages[2], result[conversationEntity1.id])
        assertEquals(messages[1], result[conversationEntity2.id])
        assertEquals(null, result[conversationEntity3.id])
    }

    @Test
    fun givenUnverifiedWarningMessageIsInserted_whenInsertingSuchMessageAgain_thenOnlyIdIsUpdatedNoNewMessages() = runTest {
        // given
        insertInitialData()
        userDAO.upsertUser(userEntity1.copy(selfUserId))
        val message0 = newSystemMessageEntity(
            conversationId = conversationEntity2.id,
            id = "local_id_id_0",
            content = MessageEntityContent.ConversationStartedUnverifiedWarning,
            date = Instant.parse("2022-03-30T15:36:00.000Z"),
            senderUserId = selfUserId
        )
        val message1 = message0.copy(
            id = "local_id_id_1",
            date = Instant.parse("2022-03-30T15:46:00.000Z")
        )
        messageDAO.insertOrIgnoreMessages(listOf(message0))

        // when
        messageDAO.insertOrIgnoreMessage(message1)

        // then
        val result = messageDAO.getMessagesByConversationAndVisibility(
            conversationId = conversationEntity2.id,
            limit = 10,
            offset = 0
        ).first()
        assertEquals(1, result.size)
        assertEquals(message1.id, result[0].id)
        assertEquals(message0.date, result[0].date)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingAlreadyEndedEphemeraMessages_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()
        val alreadyEndedEphemeralMessage = newRegularMessageEntity(
            "1",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            status = MessageEntity.Status.SENT,
            senderName = userEntity1.name!!,
            selfDeletionEndDate = Instant.DISTANT_PAST,
            expireAfterMs = 1.seconds.inWholeSeconds
        )
        val pendingEphemeralMessage = newRegularMessageEntity(
            "2",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            status = MessageEntity.Status.SENT,
            senderName = userEntity1.name!!,
            selfDeletionEndDate = Instant.DISTANT_FUTURE,
            expireAfterMs = 1.seconds.inWholeSeconds
        )
        val nonEphemeralMessage = newRegularMessageEntity(
            "3",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            status = MessageEntity.Status.SENT,
            senderName = userEntity1.name!!,
        )
        val expectedMessages = listOf(alreadyEndedEphemeralMessage)
        val allMessages = expectedMessages + listOf(pendingEphemeralMessage, nonEphemeralMessage)

        messageDAO.insertOrIgnoreMessages(allMessages)

        messageDAO.updateSelfDeletionEndDate(
            conversationId = conversationEntity1.id,
            messageId = "1",
            selfDeletionEndDate = Instant.DISTANT_PAST.plus(1.seconds)
        )

        val result = messageDAO.getAllAlreadyEndedEphemeralMessages()

        assertEquals(result.size, 1)
        assertEquals(result.first().id, alreadyEndedEphemeralMessage.id)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingEphemeraMessages_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()
        val alreadyEndedEphemeralMessage = newRegularMessageEntity(
            "1",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            status = MessageEntity.Status.SENT,
            senderName = userEntity1.name!!,
            selfDeletionEndDate = Instant.DISTANT_PAST,
            expireAfterMs = 1.seconds.inWholeSeconds
        )
        val pendingEphemeralMessage = newRegularMessageEntity(
            "2",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            status = MessageEntity.Status.SENT,
            senderName = userEntity1.name!!,
            selfDeletionEndDate = Instant.DISTANT_FUTURE,
            expireAfterMs = 1.seconds.inWholeSeconds
        )
        val nonEphemeralMessage = newRegularMessageEntity(
            "3",
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            status = MessageEntity.Status.SENT,
            senderName = userEntity1.name!!,
        )
        val expectedMessages = listOf(pendingEphemeralMessage)
        val allMessages = expectedMessages + listOf(alreadyEndedEphemeralMessage, nonEphemeralMessage)

        messageDAO.insertOrIgnoreMessages(allMessages)

        messageDAO.updateSelfDeletionEndDate(
            conversationId = conversationEntity1.id,
            messageId = "1",
            selfDeletionEndDate = Instant.DISTANT_PAST.plus(1.seconds)
        )

        val result = messageDAO.getAllPendingEphemeralMessages()

        assertEquals(result.size, 1)
        assertEquals(result.first().id, pendingEphemeralMessage.id)
    }

    @Test
    fun givenAssetTransferStatusInProgress_whenResettingAssetTransferStatus_thenTransferStatusesAreRemoved() = runTest {
        // given
        val source = conversationEntity1
        val destination = conversationEntity2
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(source)
        conversationDAO.insertConversation(destination)
        val messageId = "messageid"
        val message2Id = "messageid2"
        val messages = listOf(
            newRegularMessageEntity(
                id = messageId,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                expireAfterMs = 2000,
                content = MessageEntityContent.Asset(
                    1000,
                    assetName = "test name",
                    assetMimeType = "image/png",
                    assetOtrKey = byteArrayOf(1),
                    assetSha256Key = byteArrayOf(1),
                    assetId = "assetId",
                    assetToken = "",
                    assetDomain = "",
                    assetEncryptionAlgorithm = "",
                    assetWidth = 20,
                    assetHeight = 20,
                )
            ),
            newRegularMessageEntity(
                id = message2Id,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                expireAfterMs = 2000,
                content = MessageEntityContent.Asset(
                    1000,
                    assetName = "test name2",
                    assetMimeType = "image/png",
                    assetOtrKey = byteArrayOf(1),
                    assetSha256Key = byteArrayOf(1),
                    assetId = "assetId2",
                    assetToken = "",
                    assetDomain = "",
                    assetEncryptionAlgorithm = "",
                    assetWidth = 20,
                    assetHeight = 20,
                )
            )
        )

        messageDAO.insertOrIgnoreMessages(messages)

        messageDAO.updateAssetTransferStatus(AssetTransferStatusEntity.DOWNLOAD_IN_PROGRESS, messageId, conversationEntity1.id)
        messageDAO.updateAssetTransferStatus(AssetTransferStatusEntity.UPLOAD_IN_PROGRESS, message2Id, conversationEntity1.id)

        // when
        messageDAO.resetAssetTransferStatus()

        // then
        val assetStatuses = messageDAO.observeAssetStatuses(conversationEntity1.id).first()

        assertTrue(assetStatuses.isEmpty())
    }

    @Test
    fun givenEmptyAssetTransferStatus_whenUpdatingMessageAssetTransferStatus_thenSourceIsProperlyPropagated() = runTest {
        // given
        val source = conversationEntity1
        val destination = conversationEntity2
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(source)
        conversationDAO.insertConversation(destination)
        val messageId = "messageid"
        val message2Id = "messageid2"
        val messages = listOf(
            newRegularMessageEntity(
                id = messageId,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                expireAfterMs = 2000,
                content = MessageEntityContent.Asset(
                    1000,
                    assetName = "test name",
                    assetMimeType = "image/png",
                    assetOtrKey = byteArrayOf(1),
                    assetSha256Key = byteArrayOf(1),
                    assetId = "assetId",
                    assetToken = "",
                    assetDomain = "",
                    assetEncryptionAlgorithm = "",
                    assetWidth = 20,
                    assetHeight = 20,
                )
            ),
            newRegularMessageEntity(
                id = message2Id,
                date = "2000-01-01T13:00:00.000Z".toInstant(),
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id,
                expireAfterMs = 2000,
                content = MessageEntityContent.Asset(
                    1000,
                    assetName = "test name2",
                    assetMimeType = "image/png",
                    assetOtrKey = byteArrayOf(1),
                    assetSha256Key = byteArrayOf(1),
                    assetId = "assetId2",
                    assetToken = "",
                    assetDomain = "",
                    assetEncryptionAlgorithm = "",
                    assetWidth = 20,
                    assetHeight = 20,
                )
            )
        )

        messageDAO.insertOrIgnoreMessages(messages)

        // when
        messageDAO.updateAssetTransferStatus(AssetTransferStatusEntity.DOWNLOAD_IN_PROGRESS, messageId, conversationEntity1.id)
        messageDAO.updateAssetTransferStatus(AssetTransferStatusEntity.UPLOAD_IN_PROGRESS, message2Id, conversationEntity1.id)

        // then
        val assetStatuses = messageDAO.observeAssetStatuses(conversationEntity1.id).first()

        assertEquals(messages.size, assetStatuses.size)
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(
            conversationEntity1.copy(
                lastReadDate = "2022-03-30T15:40:00.000Z".toInstant()
            )
        )
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.insertConversation(conversationEntity3)
    }
}
