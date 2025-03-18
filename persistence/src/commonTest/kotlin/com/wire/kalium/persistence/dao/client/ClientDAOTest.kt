/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.dao.client

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.toInstant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ClientDAOTest : BaseDatabaseTest() {

    private lateinit var clientDAO: ClientDAO
    private lateinit var userDAO: UserDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var memberDAO: MemberDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        clientDAO = db.clientDAO
        userDAO = db.userDAO
        conversationDAO = db.conversationDAO
        memberDAO = db.memberDAO
    }

    @Test
    fun givenNoClientsAreInserted_whenFetchingClientsByUserId_thenTheResultIsEmpty() = runTest {
        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientIsInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {
        val insertedClient = insertedClient1.copy(user.id, "id1", deviceType = null, isMLSCapable = true)
        val expected = client1.copy(user.id, "id1", deviceType = null, isValid = true, isProteusVerified = false, isMLSCapable = true)

        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(1, result.size)
        assertEquals(expected, result.first())
    }

    @Test
    fun givenMultipleClientsAreInserted_whenFetchingClientsByUserId_thenTheRelevantClientIsReturned() = runTest {

        val insertedClient = insertedClient1.copy(user.id, "id1", deviceType = null)
        val client = insertedClient.toClient()

        val insertedClient2 = insertedClient2.copy(user.id, "id2", deviceType = null)
        val client2 = insertedClient2.toClient()

        userDAO.upsertUser(user)
        clientDAO.insertClients(listOf(insertedClient, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(2, result.size)
        assertEquals(client, result[0])
        assertEquals(client2, result[1])
    }

    @Test
    fun givenClientsAreInsertedForMultipleUsers_whenFetchingClientsByUserId_thenOnlyTheRelevantClientsAreReturned() = runTest {

        userDAO.upsertUser(user)
        clientDAO.insertClients(listOf(insertedClient1, insertedClient2))

        val unrelatedUserId = QualifiedIDEntity("unrelated", "user")
        val unrelatedUser = newUserEntity(unrelatedUserId)
        val unrelatedInsertedClient = insertedClient1.copy(unrelatedUserId, "id1", deviceType = null)

        userDAO.upsertUser(unrelatedUser)
        clientDAO.insertClient(unrelatedInsertedClient)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()

        assertEquals(2, result.size)
        assertEquals(client1, result.first())
    }

    @Test
    fun givenClientIsInserted_whenDeletingItSpecifically_thenItShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient1)

        clientDAO.deleteClient(insertedClient1.userId, insertedClient1.id)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientsAreInserted_whenDeletingClientsOfUser_thenTheyShouldNotBeReturnedAnymoreOnNextFetch() = runTest {
        userDAO.upsertUser(user)
        clientDAO.insertClients(listOf(insertedClient1, insertedClient2))

        clientDAO.deleteClientsOfUserByQualifiedID(insertedClient1.userId)

        val result = clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun givenClientWithDeviceIsStored_whenInsertingTheSameClientWithNullType_thenTypeIsNotOverwritten() = runTest {
        val insertClientWithType = insertedClient1.copy(user.id, "id1", deviceType = DeviceTypeEntity.Tablet)
        val clientWithType = insertClientWithType.toClient()

        val insertClientWithNullType = insertClientWithType.copy(deviceType = null)

        userDAO.upsertUser(user)
        clientDAO.insertClients(listOf(insertClientWithType))
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithType), resultList)
        }

        clientDAO.insertClients(listOf(insertClientWithNullType))

        // device type should not be overwritten
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithType), resultList)
        }
    }

    @Test
    fun givenClientIsInsertedAndRemoveRedundant_whenFetchingClientsByUserId_thenTheRelevantClientsAreReturned() = runTest {
        userDAO.upsertUser(user)
        clientDAO.insertClientsAndRemoveRedundant(listOf(insertedClient1, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedID(userId)

        assertEquals(2, result.size)
        assertEquals(listOf(client1, client2), result)
    }

    @Test
    fun givenClientIsInsertedAndRemoveRedundant_whenFetchingClientsByUserId_thenTheRedundantClientsAreNotReturned() = runTest {

        userDAO.upsertUser(user)
        clientDAO.insertClientsAndRemoveRedundant(listOf(insertedClient, insertedClient1))
        // this supposes to remove insertedClient1
        clientDAO.insertClientsAndRemoveRedundant(listOf(insertedClient, insertedClient2))

        val result = clientDAO.getClientsOfUserByQualifiedID(userId)

        assertEquals(2, result.size)
        assertEquals(listOf(client, client2), result)
    }

    @Test
    fun givenIsMLSCapableIsFalse_whenUpdatingAClient_thenItShouldUpdatedToTrue() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(
            insertedClient.copy(
                isMLSCapable = false
            )
        )
        clientDAO.insertClient(
            insertedClient.copy(
                isMLSCapable = true
            )
        )
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isMLSCapable }
    }

    @Test
    fun givenIsMLSCapableIsTrue_whenUpdatingAClient_thenItShouldRemainTrue() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(
            insertedClient.copy(
                isMLSCapable = true
            )
        )
        clientDAO.insertClient(
            insertedClient.copy(
                isMLSCapable = false
            )
        )
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isMLSCapable }
    }

    @Test
    fun whenInsertingANewClient_thenIsMustBeMarkedAsValid() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isValid }
    }

    @Test
    fun givenValidClient_whenMarkingAsInvalid_thenClientInfoIsUpdated() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        clientDAO.tryMarkInvalid(listOf(insertedClient.userId to listOf(insertedClient.id)))
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isValid }
    }

    @Test
    fun whenClientIsInsertedTwice_thenIvValidMustNotBeChanged() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        clientDAO.tryMarkInvalid(listOf(insertedClient.userId to listOf(insertedClient.id)))
        clientDAO.insertClient(insertedClient)
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isValid }
    }

    @Test
    fun givenInvalidUserClient_whenSelectingConversationRecipients_thenOnlyValidClientAreReturned() = runTest {
        val user = user
        val expected: Map<QualifiedIDEntity, List<Client>> = mapOf(user.id to listOf(client1, client2))
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        clientDAO.insertClient(insertedClient1)
        clientDAO.insertClient(insertedClient2)
        clientDAO.tryMarkInvalid(listOf(insertedClient.userId to listOf(insertedClient.id)))
        conversationDAO.insertConversations(listOf(conversationEntity1))
        memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Admin), conversationEntity1.id)
        val actual = clientDAO.conversationRecipient(conversationEntity1.id)
        assertEquals(expected, actual)
    }

    @Test
    fun givenNewClientAdded_thenItIsMarkedAsNotVerified() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isProteusVerified }
    }

    @Test
    fun givenClient_whenUpdatingVerificationStatus_thenItIsUpdated() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        clientDAO.updateClientProteusVerificationStatus(user.id, insertedClient.id, true)
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isProteusVerified }

        clientDAO.updateClientProteusVerificationStatus(user.id, insertedClient.id, false)
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isProteusVerified }
    }

    @Test
    fun givenUserId_whenAClientIsAdded_thenNewListIsEmitted() = runTest {
        val user = user
        userDAO.upsertUser(user)

        clientDAO.observeClientsByUserId(user.id).test {
            awaitItem().also { result -> assertEquals(emptyList(), result) }

            clientDAO.insertClient(insertedClient)
            awaitItem().also { result -> assertEquals(listOf(client), result) }

            clientDAO.insertClient(insertedClient1)
            awaitItem().also { result -> assertEquals(listOf(client, client1), result) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenVerifiedClient_whenInsertingTheSameIdAgain_thenVerificationStatusIsNotChanges() = runTest {
        val user = user
        userDAO.upsertUser(user)

        clientDAO.insertClient(insertedClient)
        assertFalse { clientDAO.getClientsOfUserByQualifiedID(userId).first().isProteusVerified }

        clientDAO.updateClientProteusVerificationStatus(user.id, insertedClient.id, true)
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isProteusVerified }

        clientDAO.insertClient(insertedClient)
        assertTrue { clientDAO.getClientsOfUserByQualifiedID(userId).first().isProteusVerified }
    }

    @Test
    fun givenUserIsPartOfConversation_whenGettingRecipient_thenOnlyValidUserClientsAreReturned() = runTest {
        val user = user
        userDAO.upsertUser(user)
        userDAO.upsertUser(user.copy(id = selfUserId))
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Admin), conversationEntity1.id)

        clientDAO.insertClient(insertedClient)
        val invalidClient = insertedClient.copy(id = "id2")
        clientDAO.insertClient(invalidClient)
        clientDAO.tryMarkInvalid(listOf(invalidClient.userId to listOf(invalidClient.id)))

        clientDAO.recipientsIfTheyArePartOfConversation(conversationEntity1.id, setOf(user.id)).also {
            assertEquals(1, it.size)
            assertEquals(listOf(client), it[user.id])
        }
    }

    @Test
    fun givenUserIsNotPartOfConversation_whenGettingRecipient_thenTheyAreNotIncludedInTheResult() = runTest {
        val user = user
        userDAO.upsertUser(user)
        clientDAO.insertClient(insertedClient)
        conversationDAO.insertConversation(conversationEntity1)
        memberDAO.insertMember(MemberEntity(user.id, MemberEntity.Role.Admin), conversationEntity1.id)

        val user2 = newUserEntity(QualifiedIDEntity("test2", "domain"))
        userDAO.upsertUser(user2)
        val insertedClient2 = InsertClientParam(
            userId = user2.id,
            id = "id01",
            deviceType = null,
            clientType = null,
            label = null,
            model = null,
            registrationDate = null,
            lastActive = null,
            mlsPublicKeys = null,
            isMLSCapable = false
        )
        clientDAO.insertClient(insertedClient2)


        clientDAO.recipientsIfTheyArePartOfConversation(conversationEntity1.id, setOf(user.id, user2.id)).also {
            assertEquals(1, it.size)
            assertEquals(listOf(client), it[user.id])
            assertNull(it[user2.id])
        }
    }

    @Test
    fun givenYUserHaveNoClientsAfterDeletingClients_whenCallingRemoveClientsAndReturnUsersWithNoClients_thenUserIsReturned() = runTest {
        val userWithAllClientsDeleted = user
        val clientsWithAllClientsDeleted = listOf(insertedClient1, insertedClient2)


        val userWithClients = newUserEntity(QualifiedIDEntity("test2", "domain"))
        val clientsWithClients = listOf(
            insertedClient1.copy(userWithClients.id, "id1", deviceType = null), // will be deleted
            insertedClient2.copy(userWithClients.id, "id2", deviceType = null) // will be retained
        )

        userDAO.upsertUsers(listOf(userWithAllClientsDeleted, userWithClients))
        clientDAO.insertClients(clientsWithAllClientsDeleted)
        clientDAO.insertClients(clientsWithClients)

        clientDAO.removeClientsAndReturnUsersWithNoClients(
            mapOf(
                userWithAllClientsDeleted.id to clientsWithAllClientsDeleted.map { it.id },
                userWithClients.id to listOf(clientsWithClients.first()).map { it.id }
            )
        ).also {
            assertEquals(listOf(userWithAllClientsDeleted.id), it)
        }
    }


    @Test
    fun givenClientWithNonNullValuesIsStored_whenInsertingTheSameClientWithNullValues_thenValuesAreNotOverwrittenWithNulls() = runTest {
        val insertClientWithNonNullValues = insertedClient1.copy(
            deviceType = DeviceTypeEntity.Tablet,
            clientType = ClientTypeEntity.Permanent,
            label = "label",
            registrationDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastActive = "2022-03-30T15:36:00.000Z".toInstant(),
            model = "model",
        )
        val insertClientWithNullValues = insertedClient1.copy(
            deviceType = null,
            clientType = null,
            label = null,
            registrationDate = null,
            lastActive = null,
            model = null,
        )
        val clientWithNonNullValues = insertClientWithNonNullValues.toClient()

        userDAO.upsertUser(user)
        clientDAO.insertClients(listOf(insertClientWithNonNullValues))
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithNonNullValues), resultList)
        }

        clientDAO.insertClients(listOf(insertClientWithNullValues))

        // values should not be overwritten with nulls
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithNonNullValues), resultList)
        }
    }

    @Test
    fun givenClientWithNullValuesIsStored_whenInsertingTheSameClientWithNonNullValues_thenNullValuesAreOverwritten() = runTest {
        val insertClientWithNonNullValues = insertedClient1.copy(
            deviceType = DeviceTypeEntity.Tablet,
            clientType = ClientTypeEntity.Permanent,
            label = "label",
            registrationDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastActive = "2022-03-30T15:36:00.000Z".toInstant(),
            model = "model",
        )
        val insertClientWithNullValues = insertedClient1.copy(
            deviceType = null,
            clientType = null,
            label = null,
            registrationDate = null,
            lastActive = null,
            model = null,
        )
        val clientWithNonNullValues = insertClientWithNonNullValues.toClient()
        val clientWithNullValues = insertClientWithNullValues.toClient()

        userDAO.upsertUser(user)
        clientDAO.insertClients(listOf(insertClientWithNullValues))
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithNullValues), resultList)
        }

        clientDAO.insertClients(listOf(insertClientWithNonNullValues))

        // null values should be overwritten with proper ones
        clientDAO.getClientsOfUserByQualifiedIDFlow(userId).first().also { resultList ->
            assertEquals(listOf(clientWithNonNullValues), resultList)
        }
    }

    @Test
    fun givenClientIsNotMlsCapable_whenCallingIsMlsCapable_thenReturnFalse() = runTest {
        val user = user
        val client: InsertClientParam = insertedClient.copy(isMLSCapable = false)
        userDAO.upsertUser(user)
        clientDAO.insertClient(client)
        assertFalse { clientDAO.isMLSCapable(userId, clientId = client.id)!! }
    }

    @Test
    fun givenClientIsMlsCapable_whenCallingIsMlsCapable_thenReturnTrue() = runTest {
        val user = user
        val client: InsertClientParam = insertedClient.copy(isMLSCapable = true)
        userDAO.upsertUser(user)
        clientDAO.insertClient(client)
        assertTrue { clientDAO.isMLSCapable(userId, clientId = client.id)!! }
    }

    @Test
    fun givenNotFound_whenCallingIsMlsCapableForUser_thenReturnNull() = runTest {
        val user = user
        userDAO.upsertUser(user)
        assertNull(clientDAO.isMLSCapable(userId, clientId = client.id))
    }

    private companion object {
        val userId = QualifiedIDEntity("test", "domain")
        val user = newUserEntity(userId)
        val insertedClient = InsertClientParam(
            userId = user.id,
            id = "id0",
            deviceType = null,
            clientType = null,
            label = null,
            model = null,
            registrationDate = null,
            lastActive = null,
            mlsPublicKeys = null,
            isMLSCapable = false
        )
        val client = insertedClient.toClient()

        val insertedClient1 = insertedClient.copy(user.id, "id1", deviceType = null)
        val client1 = insertedClient1.toClient()

        val insertedClient2 = insertedClient.copy(user.id, "id2", deviceType = null)
        val client2 = insertedClient2.toClient()

        const val teamId = "teamId"
        val conversationEntity1 = ConversationEntity(
            QualifiedIDEntity("1", "wire.com"),
            "conversation1",
            ConversationEntity.Type.ONE_ON_ONE,
            teamId,
            ConversationEntity.ProtocolInfo.Proteus,
            creatorId = "someValue",
            lastNotificationDate = null,
            lastModifiedDate = "2022-03-30T15:36:00.000Z".toInstant(),
            lastReadDate = "2000-01-01T12:00:00.000Z".toInstant(),
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            isChannel = false,
            wireCell = null,
        )
    }
}

private fun InsertClientParam.toClient(): Client =
    Client(
        userId,
        id,
        deviceType = deviceType,
        clientType = clientType,
        isValid = true,
        isProteusVerified = false,
        isMLSCapable = false,
        label = label,
        model = model,
        registrationDate = registrationDate,
        lastActive = lastActive,
        mlsPublicKeys = null
    )
