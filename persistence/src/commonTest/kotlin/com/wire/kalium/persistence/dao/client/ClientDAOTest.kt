package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.User
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClientDAOTest : BaseDatabaseTest() {

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
    fun givenNoClientsAreInserted_whenFetchingClientsByUserId_thenTheResultIsEmpty() = runTest {
        val result = clientDAO.getClientsOfUserByQualifiedID(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientIsInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {
        val insertedClient = Client(user.id, "id1")
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedID(userId).first()

        assertEquals(1, result.size)
        assertEquals(insertedClient, result.first())
    }

    @Test
    fun givenMultipleClientsAreInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {
        val insertedClient = Client(user.id, "id1")
        val insertedClient2 = Client(user.id, "id2")
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedID(userId).first()

        assertEquals(2, result.size)
        assertEquals(insertedClient, result[0])
        assertEquals(insertedClient2, result[1])
    }

    @Test
    fun givenClientsAreInsertedForMultipleUsers_whenFetchingClientsByUserId_thenOnlyTheRelevantClientsAreReturned() = runTest {
        val insertedClient = Client(user.id, "id1")
        val insertedClient2 = Client(user.id, "id2")
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        val unrelatedUserId = QualifiedID("unrelated", "user")
        val unrelatedUser = User(unrelatedUserId, "name", "handle")
        val unrelatedInsertedClient = Client(unrelatedUserId, "id1")
        userDAO.insertUser(unrelatedUser)
        clientDAO.insertClient(unrelatedInsertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedID(userId).first()

        assertEquals(2, result.size)
        assertEquals(insertedClient, result.first())
    }

    @Test
    fun givenClientIsInserted_whenDeletingItSpecifically_thenItShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        val insertedClient = Client(user.id, "id1")
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)

        clientDAO.deleteClient(insertedClient.userId, insertedClient.id)

        val result = clientDAO.getClientsOfUserByQualifiedID(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientsAreInserted_whenDeletingClientsOfUser_thenTheyShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        val insertedClient = Client(user.id, "id1")
        val insertedClient2 = Client(user.id, "id2")
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        clientDAO.deleteClientsOfUserByQualifiedID(insertedClient.userId)

        val result = clientDAO.getClientsOfUserByQualifiedID(userId).first()
        assertTrue(result.isEmpty())
    }

    private companion object {
        val userId = QualifiedID("test", "domain")
        val user = User(userId, "name", "handle")
    }
}
