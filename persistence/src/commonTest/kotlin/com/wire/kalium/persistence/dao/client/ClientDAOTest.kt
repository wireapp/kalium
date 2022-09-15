package com.wire.kalium.persistence.dao.client

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
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
        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientIsInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {
        val insertedClient = Client(user.id, "id1", deviceType = null)
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(1, result.size)
        assertEquals(insertedClient, result.first())
    }

    @Test
    fun givenMultipleClientsAreInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {
        val insertedClient = Client(user.id, "id1", deviceType = null)
        val insertedClient2 = Client(user.id, "id2", deviceType = null)
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(2, result.size)
        assertEquals(insertedClient, result[0])
        assertEquals(insertedClient2, result[1])
    }

    @Test
    fun givenClientsAreInsertedForMultipleUsers_whenFetchingClientsByUserId_thenOnlyTheRelevantClientsAreReturned() = runTest {
        val insertedClient = Client(user.id, "id1", deviceType = null)
        val insertedClient2 = Client(user.id, "id2", deviceType = null)
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        val unrelatedUserId = QualifiedIDEntity("unrelated", "user")
        val unrelatedUser = newUserEntity(unrelatedUserId)
        val unrelatedInsertedClient = Client(unrelatedUserId, "id1", deviceType = null)
        userDAO.insertUser(unrelatedUser)
        clientDAO.insertClient(unrelatedInsertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(2, result.size)
        assertEquals(insertedClient, result.first())
    }

    @Test
    fun givenClientIsInserted_whenDeletingItSpecifically_thenItShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        val insertedClient = Client(user.id, "id1", deviceType = null)
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)

        clientDAO.deleteClient(insertedClient.userId, insertedClient.id)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientsAreInserted_whenDeletingClientsOfUser_thenTheyShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        val insertedClient = Client(user.id, "id1", deviceType = null)
        val insertedClient2 = Client(user.id, "id2", deviceType = null)
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        clientDAO.deleteClientsOfUserByQualifiedID(insertedClient.userId)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientWithDeviceIsStored_whenInsertingTheSameClientWithNullType_thenTypeIsNotOverwritten() = runTest {
        val insertClientWithType = Client(user.id, "id1", deviceType = DeviceTypeEntity.Tablet)
        val insertClientWithNullType = insertClientWithType.copy(deviceType = null)
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertClientWithType))
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(insertClientWithType), resultList)
        }

        clientDAO.insertClients(listOf(insertClientWithNullType))

        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(insertClientWithType), resultList)
        }
    }

    private companion object {
        val userId = QualifiedIDEntity("test", "domain")
        val user = newUserEntity(userId)
    }
}
