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
import kotlin.test.assertFalse
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
        val expected = Client(user.id, "id1", deviceType = null, true)
        val insertedClient = InsertClientParam(expected.userId, expected.id, deviceType = expected.deviceType)
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(1, result.size)
        assertEquals(expected, result.first())
    }

    @Test
    fun givenMultipleClientsAreInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {
        val client = Client(user.id, "id1", deviceType = null, isValid = true)
        val insertedClient = InsertClientParam(user.id, "id1", deviceType = null)

        val client2 = Client(user.id, "id2", deviceType = null, isValid = true)
        val insertedClient2 = InsertClientParam(user.id, "id2", deviceType = null)

        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(2, result.size)
        assertEquals(client, result[0])
        assertEquals(client2, result[1])
    }

    @Test
    fun givenClientsAreInsertedForMultipleUsers_whenFetchingClientsByUserId_thenOnlyTheRelevantClientsAreReturned() = runTest {

        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient1, insertedClient2))

        val unrelatedUserId = QualifiedIDEntity("unrelated", "user")
        val unrelatedUser = newUserEntity(unrelatedUserId)
        val unrelatedInsertedClient = InsertClientParam(unrelatedUserId, "id1", deviceType = null)
        userDAO.insertUser(unrelatedUser)
        clientDAO.insertClient(unrelatedInsertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(2, result.size)
        assertEquals(client1, result.first())
    }

    @Test
    fun givenClientIsInserted_whenDeletingItSpecifically_thenItShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient1)

        clientDAO.deleteClient(insertedClient1.userId, insertedClient1.id)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientsAreInserted_whenDeletingClientsOfUser_thenTheyShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertedClient1, insertedClient2))

        clientDAO.deleteClientsOfUserByQualifiedID(insertedClient1.userId)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientWithDeviceIsStored_whenInsertingTheSameClientWithNullType_thenTypeIsNotOverwritten() = runTest {
        val insertClientWithType = InsertClientParam(user.id, "id1", deviceType = DeviceTypeEntity.Tablet)
        val clientWithType = Client(insertClientWithType.userId, insertClientWithType.id, insertClientWithType.deviceType, true)

        val insertClientWithNullType = insertClientWithType.copy(deviceType = null)
        val clientWithNullType =
            Client(insertClientWithNullType.userId, insertClientWithNullType.id, insertClientWithNullType.deviceType, true)

        userDAO.insertUser(user)
        clientDAO.insertClients(listOf(insertClientWithType))
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithType), resultList)
        }

        clientDAO.insertClients(listOf(insertClientWithNullType))

        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithType), resultList)
        }
    }

    @Test
    fun givenClientIsInsertedAndRemoveRedundant_whenFetchingClientsByUserId_thenTheRelevantClientsAreReturned() = runTest {
        userDAO.insertUser(user)
        clientDAO.insertClientsAndRemoveRedundant(user.id, listOf(insertedClient1, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedID(userId)

        assertEquals(2, result.size)
        assertEquals(listOf(client1, client2), result)
    }

    @Test
    fun givenClientIsInsertedAndRemoveRedundant_whenFetchingClientsByUserId_thenTheRedundantClientsAreNotReturned() = runTest {

        userDAO.insertUser(user)
        clientDAO.insertClientsAndRemoveRedundant(user.id, listOf(insertedClient, insertedClient1))
        // this supposes to remove insertedClient1
        clientDAO.insertClientsAndRemoveRedundant(user.id, listOf(insertedClient, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedID(userId)

        assertEquals(2, result.size)
        assertEquals(listOf(client, client2), result)
    }

    @Test
    fun whenInsertingANewClient_thenIsMustBeMarkedAsValid() = runTest {
        val user = user
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isValid }
    }

    @Test
    fun givenValidClient_whenMarkingAsInvalid_thenClientInfoIsUpdated() = runTest {
        val user = user
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)
        clientDAO.tryMarkInvalid(insertedClient.userId, insertedClient.id)
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isValid }
    }

    @Test
    fun whenClientIsInsertedTwice_thenIvValidMustNotBeChanged() = runTest {
        val user = user
        userDAO.insertUser(user)
        clientDAO.insertClient(insertedClient)
        clientDAO.tryMarkInvalid(insertedClient.userId, insertedClient.id)
        clientDAO.insertClient(insertedClient)
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isValid }
    }

    private companion object {
        val userId = QualifiedIDEntity("test", "domain")
        val user = newUserEntity(userId)
        val insertedClient = InsertClientParam(user.id, "id0", deviceType = null)
        val client = Client(insertedClient.userId, insertedClient.id, deviceType = insertedClient.deviceType, isValid = true)

        val client1 = Client(user.id, "id1", deviceType = null, isValid = true)
        val insertedClient1 = InsertClientParam(user.id, "id1", deviceType = null)

        val client2 = Client(user.id, "id2", deviceType = null, isValid = true)
        val insertedClient2 = InsertClientParam(user.id, "id2", deviceType = null)
    }
}
