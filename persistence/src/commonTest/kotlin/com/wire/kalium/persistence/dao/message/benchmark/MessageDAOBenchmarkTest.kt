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

package com.wire.kalium.persistence.dao.message.benchmark

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.benchmark.MessageDAOBenchmarkParams.MESSAGE_COUNT
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.measureTime

// Ignore to avoid running unnecessarily on CI. Can be easily re-enabled by developers when needed.
class MessageDAOBenchmarkTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val conversationEntity1 = newConversationEntity("Test1")
    private val userEntity1 = newUserEntity("userEntity1")
    private val userEntity2 = newUserEntity("userEntity2")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, enableWAL = true, shouldTune = false)
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun insertRandomMessages() = runTest {
        setupData()
        val count = MESSAGE_COUNT
        val messagesToInsert = generateRandomMessages(count)
        val messageDuration = measureTime {
            messageDAO.insertOrIgnoreMessages(messagesToInsert)
        }

        val assetMessages = messagesToInsert.filter { it.content is MessageEntityContent.Asset }
        val assetMessagesCount = assetMessages.count()
        assetMessages.forEach {
            messageDAO.updateAssetTransferStatus(generateRandomAssetTransferStatus(), it.id, it.conversationId)
        }

        val assetStatusesDuration = measureTime {
            messageDAO.observeAssetStatuses(conversationEntity1.id).first()
        }

        println("Took $messageDuration to insert $count random messages")
        println("Took $assetStatusesDuration to get $assetMessagesCount asset transfer statuses")
    }


    private fun generateRandomMessages(count: Int): List<MessageEntity> {
        val conversations = listOf(conversationEntity1)
        val users = listOf(userEntity1, userEntity2)
        return buildList {
            repeat(count) {
                add(
                    MessageEntity.Regular(
                        id = it.toString(),
                        conversationId = conversations.random().id,
                        date = Instant.fromEpochSeconds(it.toLong()),
                        senderUserId = users.random().id,
                        status = MessageEntity.Status.values().random(),
                        visibility = MessageEntity.Visibility.values().random(),
                        content = generateRandomMessageContent(),
                        senderClientId = Random.nextLong(2_000).toString(),
                        editStatus = MessageEntity.EditStatus.NotEdited,
                        senderName = "senderName",
                        readCount = 0
                    )
                )
            }
        }
    }

    private fun generateRandomMessageContent() = when (Random.nextInt(0..3)) {
        0 -> MessageEntityContent.Unknown(typeName = null, Random.nextBytes(1000))
        1 -> MessageEntityContent.Text(Random.nextBytes(100).toString())
        2 -> MessageEntityContent.Asset(
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

        else -> MessageEntityContent.Knock(Random.nextBoolean())
    }

    private fun generateRandomAssetTransferStatus() = AssetTransferStatusEntity.entries[Random.nextInt(0..8)]

    @Test
    fun queryIncreasinglyBiggerAmountByConversationAndVisibility() = runTest {
        setupData()
        var totalMessageCount = MESSAGE_COUNT
        repeat(3) { count ->
            val messagesToInsert = generateRandomMessages(totalMessageCount)
            messageDAO.insertOrIgnoreMessages(messagesToInsert)
            measureTime {
                messageDAO.getMessagesByConversationAndVisibility(
                    conversationEntity1.id,
                    totalMessageCount,
                    0,
                    listOf(MessageEntity.Visibility.VISIBLE)
                ).first()
            }.also {
                println("Took $it to query visible messages from a single conversation, with $totalMessageCount random messages inserted")
            }
            totalMessageCount += MESSAGE_COUNT
        }
    }

    @Test
    fun concurrentInsertAndQuery() = runTest {
        setupData()
        val totalMessageCount = MESSAGE_COUNT
        val pageSize = 50
        val messagesToInsert = generateRandomMessages(totalMessageCount)
        measureTime {
            val insertingJob = launch(KaliumDispatcherImpl.io) {
                messagesToInsert.forEach { messageEntity ->
                    messageDAO.insertOrIgnoreMessage(messageEntity)
                }
            }
            launch(KaliumDispatcherImpl.io) {
                while (insertingJob.isActive) {
                    messageDAO.getMessagesByConversationAndVisibility(
                        conversationId = conversationEntity1.id,
                        limit = pageSize,
                        offset = 0,
                        visibility = listOf(MessageEntity.Visibility.VISIBLE)
                    ).first()
                }
            }.join()
        }.also {
            println(
                """
                Took $it to insert $totalMessageCount random messages while a worker was constantly querying the database.
                """.trimIndent()
            )
        }
    }

    private suspend fun setupData() {
        conversationDAO.insertConversations(listOf(conversationEntity1))
        userDAO.upsertUser(userEntity1)
        userDAO.upsertUser(userEntity2)
    }

}
