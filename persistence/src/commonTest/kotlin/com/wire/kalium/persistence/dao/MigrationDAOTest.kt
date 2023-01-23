package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MigrationDAOTest : BaseDatabaseTest() {
    private lateinit var migrationDAO: MigrationDAO
    private lateinit var conversationDAO: ConversationDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        migrationDAO = db.migrationDAO
        conversationDAO = db.conversationDAO
    }

    @Test
    fun givenConversationAlreadyStored_whenInsertingFromMigration_thenIgnore() = runTest {
        val conversation = newConversationEntity(id = "conversation_id").copy(type = ConversationEntity.Type.GROUP, name = "conv name")
        val conversationFromMigration = conversation.copy(type = ConversationEntity.Type.ONE_ON_ONE, name = "migration name")

        conversationDAO.insertConversation(conversation)
        conversationDAO.getConversationByQualifiedID(conversation.id).also {
            assertEquals(conversation.type, it?.type)
            assertEquals(conversation.name, it?.name)
        }

        migrationDAO.insertConversation(listOf(conversationFromMigration))
        conversationDAO.getConversationByQualifiedID(conversationFromMigration.id).also {
            assertEquals(conversation.type, it?.type)
            assertEquals(conversation.name, it?.name)
        }
    }

    @Test
    fun test() {
        runTest {
            GlobalScope.launch(Dispatchers.Default) {
                launch {
                    println("1")
                    delay(100)
                }
                launch {

                }
                println("test")

            }.join()
        }
    }
}
