package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.client.Client
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

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

    @Test
    fun givenUserIsNotInserted_whenInsertingClient_thenAnExceptionIsThrown() = runTest {
        // Exception depends on each platform/sqlite driver, can't assert exception type or message in common source
        assertFails {
            clientDAO.insertClient(client)
        }
    }

    private companion object {
        val userId = QualifiedID("test", "domain")
        val user = newUserEntity(qualifiedID = userId)
        val client = Client(user.id, "id1")
    }
}
