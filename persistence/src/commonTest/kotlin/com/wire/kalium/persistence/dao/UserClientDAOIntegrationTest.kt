package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.dao.client.ClientDAO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class UserClientDAOIntegrationTest : BaseDatabaseTest() {

    private lateinit var clientDAO: ClientDAO
    private lateinit var userDAO: UserDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        clientDAO = db.clientDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenClientsAreInserted_whenDeletingTheUser_thenTheClientsAreDeleted() = runTest {
        userDAO.insertUser(user)
        clientDAO.insertClient(client)

        userDAO.deleteUserByQualifiedID(user.id)

        val result = clientDAO.getClientsOfUserByQualifiedID(user.id).first()
        assertTrue(result.isEmpty())
    }

    private companion object {
        val userId = QualifiedID("test", "domain")
        val user = User(userId, "name", "handle")
        val client = Client(user.id, "id1")
    }
}
