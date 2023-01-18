package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionDaoTest : BaseDatabaseTest() {

    private val connection1 = connectionEntity("1")
    private val connection2 = connectionEntity("2")

    lateinit var db: UserDatabaseBuilder

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenConnection_ThenConnectionCanBeInserted() = runTest {
        db.connectionDAO.insertConnection(connection1)
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(connection1, result[0])
    }

    @Test
    fun givenConnectionWithoutShouldNotifyFlag_ThenConnectionCanBeInsertedAndDefaultFlagIsUsed() = runTest {
        db.connectionDAO.insertConnection(connection1.copy(shouldNotify = null))
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(connection1, result[0])
    }

    @Test
    fun givenConnection_WhenInsertingAlreadyExistedConnection_ThenShouldNotifyStaysOldOne() = runTest {
        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.insertConnection(connection1.copy(shouldNotify = false))
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(connection1, result[0])
    }

    @Test
    fun givenConnection_WhenUpdateNotifyFlag_ThenItIsUpdated() = runTest {
        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.updateNotificationFlag(false, connection1.qualifiedToId)
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(false, result[0].shouldNotify)
    }

    @Test
    fun givenFewConnections_WhenUpdateNotifyFlagForAll_ThenItIsUpdated() = runTest {
        db.connectionDAO.insertConnection(connection1)
        db.connectionDAO.insertConnection(connection2)
        db.connectionDAO.setAllConnectionsAsNotified()
        val result = db.connectionDAO.getConnectionRequests().first()
        assertEquals(false, result[0].shouldNotify)
        assertEquals(false, result[1].shouldNotify)
    }

    companion object {
        private fun connectionEntity(id: String = "0") = ConnectionEntity(
            conversationId = "$id@wire.com",
            from = "from_string",
            lastUpdateDate = "2022-03-30T15:36:00.000Z".toInstant(),
            qualifiedConversationId = QualifiedIDEntity(id, "wire.com"),
            qualifiedToId = QualifiedIDEntity("me", "wire.com"),
            status = ConnectionEntity.State.PENDING,
            toId = "me@wire.com",
            shouldNotify = true
        )
    }
}
