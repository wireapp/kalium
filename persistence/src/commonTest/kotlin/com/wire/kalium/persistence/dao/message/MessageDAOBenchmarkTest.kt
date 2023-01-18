package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

// Ignore to avoid running unnecessarily on CI. Can be easily re-enabled by developers when needed.
@Ignore
class MessageDAOBenchmarkTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val conversationEntity1 = newConversationEntity("Test1")
    private val userEntity1 = newUserEntity("userEntity1")
    private val userEntity2 = newUserEntity("userEntity2")

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun insertTextMessages() = runTest {
        setupData()
        val count = MESSAGE_COUNT
        val messagesToInsert = generateRandomMessages(count)
        val duration = measureTime {
            messageDAO.insertOrIgnoreMessages(messagesToInsert)
        }

        println("Took $duration to insert $count text messages")
    }

    @Suppress("LongMethod")
    private fun generateRandomMessages(count: Int): List<MessageEntity> {
        val conversations = listOf(conversationEntity1)
        val users = listOf(userEntity1, userEntity2)
        return buildList {
            repeat(count / 4) {
                add(
                    MessageEntity.Regular(
                        id = it.toString(),
                        conversationId = conversations.random().id,
                        date = Instant.fromEpochSeconds(it.toLong()),
                        senderUserId = users.random().id,
                        status = MessageEntity.Status.values().random(),
                        visibility = MessageEntity.Visibility.values().random(),
                        content = MessageEntityContent.Text("Text content for message $it"),
                        senderClientId = Random.nextLong(2_000).toString(),
                        editStatus = MessageEntity.EditStatus.NotEdited,
                        senderName = "senderName"
                    )
                )

                add(
                    MessageEntity.System(
                        id = it.toString(),
                        conversationId = conversations.random().id,
                        date = Instant.fromEpochSeconds(it.toLong()),
                        senderUserId = users.random().id,
                        status = MessageEntity.Status.values().random(),
                        visibility = MessageEntity.Visibility.values().random(),
                        content = MessageEntityContent.MemberChange(
                            listOf(UserIDEntity("value", "domain")),
                            MessageEntity.MemberChangeType.REMOVED
                        ),
                        senderName = "senderName"
                    )
                )

                add(
                    MessageEntity.Regular(
                        id = it.toString(),
                        conversationId = conversations.random().id,
                        date = Instant.fromEpochSeconds(it.toLong()),
                        senderUserId = users.random().id,
                        status = MessageEntity.Status.values().random(),
                        visibility = MessageEntity.Visibility.values().random(),
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
                        ),
                        senderClientId = Random.nextLong(2_000).toString(),
                        editStatus = MessageEntity.EditStatus.NotEdited,
                        senderName = "senderName"
                    )
                )

                add(
                    MessageEntity.Regular(
                        id = it.toString(),
                        conversationId = conversations.random().id,
                        date = Instant.fromEpochSeconds(it.toLong()),
                        senderUserId = users.random().id,
                        status = MessageEntity.Status.values().random(),
                        visibility = MessageEntity.Visibility.values().random(),
                        content = MessageEntityContent.Unknown(
                            typeName = null,
                            Random.nextBytes(100000)
                        ),
                        senderClientId = Random.nextLong(2_000).toString(),
                        editStatus = MessageEntity.EditStatus.NotEdited,
                        senderName = "senderName"
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun queryTextMessagesNormal() = runTest {
        setupData()
        val totalMessageCount = MESSAGE_COUNT
        val messagesToInsert = generateRandomMessages(totalMessageCount)
        messageDAO.insertOrIgnoreMessages(messagesToInsert)
        repeat(4) {
            measureTime {
                messageDAO.getMessagesByConversationAndVisibility(
                    conversationEntity1.id,
                    totalMessageCount,
                    0,
                    MessageEntity.Visibility.values().toList()
                ).first()
            }.also {
                println("Took $it to query $totalMessageCount messages")
            }
        }
    }

    private suspend fun setupData() {
        conversationDAO.insertConversations(listOf(conversationEntity1))
        userDAO.insertUser(userEntity1)
        userDAO.insertUser(userEntity2)
    }

    private companion object {
        const val MESSAGE_COUNT = 1000
    }
}
