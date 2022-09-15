package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

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
            messageDAO.insertMessages(messagesToInsert)
        }

        println("Took $duration to insert $count text messages")
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
                        date = it.toString(),
                        senderUserId = users.random().id,
                        status = MessageEntity.Status.values().random(),
                        visibility = MessageEntity.Visibility.values().random(),
                        content = MessageEntityContent.Text("Text content for message $it"),
                        senderClientId = Random.nextLong(2_000).toString(),
                        editStatus = MessageEntity.EditStatus.NotEdited
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun queryTextMessages() = runTest {
        setupData()
        val totalMessageCount = MESSAGE_COUNT
        val messagesToInsert = generateRandomMessages(totalMessageCount)
        messageDAO.insertMessages(messagesToInsert)

        val duration = measureTime {
            messageDAO.getMessagesByConversationAndVisibility(
                conversationEntity1.id,
                totalMessageCount,
                0,
                MessageEntity.Visibility.values().toList()
            ).first()
        }
        println("Took $duration to query $totalMessageCount messages")
    }

    private suspend fun setupData() {
        conversationDAO.insertConversations(listOf(conversationEntity1))
        userDAO.insertUser(userEntity1)
        userDAO.insertUser(userEntity2)
    }

    private companion object {
        const val MESSAGE_COUNT = 100
    }
}
