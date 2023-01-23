package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.random.nextInt
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

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
    @Test
    fun insertRandomMessages() = runTest {
        setupData()
        val count = MESSAGE_COUNT
        val messagesToInsert = generateRandomMessages(count)
        val duration = measureTime {
            messageDAO.insertOrIgnoreMessages(messagesToInsert)
        }

        println("Took $duration to insert $count random messages")
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
                        senderName = "senderName"
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

        else -> MessageEntityContent.Knock(Random.nextBoolean())
    }

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
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

    @OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
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
        userDAO.insertUser(userEntity1)
        userDAO.insertUser(userEntity2)
    }

    private companion object {
        const val MESSAGE_COUNT = 1_000
    }
}
