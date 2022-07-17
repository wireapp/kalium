package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationDAOTest : BaseDatabaseTest() {

    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenConversation_ThenConversationCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        val result = conversationDAO.observeGetConversationByQualifiedID(conversationEntity1.id).first()
        assertEquals(conversationEntity1, result)
    }

    @Test
    fun givenListOfConversations_ThenMultipleConversationsCanBeInsertedAtOnce() = runTest {
        conversationDAO.insertConversations(listOf(conversationEntity1, conversationEntity2))
        val result1 = conversationDAO.observeGetConversationByQualifiedID(conversationEntity1.id).first()
        val result2 = conversationDAO.observeGetConversationByQualifiedID(conversationEntity2.id).first()
        assertEquals(conversationEntity1, result1)
        assertEquals(conversationEntity2, result2)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.deleteConversationByQualifiedID(conversationEntity1.id)
        val result = conversationDAO.observeGetConversationByQualifiedID(conversationEntity1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        val updatedConversation1Entity = conversationEntity1.copy(name = "Updated conversation1")
        conversationDAO.updateConversation(updatedConversation1Entity)
        val result = conversationDAO.observeGetConversationByQualifiedID(conversationEntity1.id).first()
        assertEquals(updatedConversation1Entity, result)
    }

    @Test
    fun givenExistingConversation_ThenConversationCanBeRetrievedByGroupID() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        val result =
            conversationDAO.getConversationByGroupID((conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId)
                .first()
        assertEquals(conversationEntity2, result)
    }

    @Test
    fun givenExistingMLSConversation_ThenConversationIdCanBeRetrievedByGroupID() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        val result =
            conversationDAO.getConversationIdByGroupID((conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId)
        assertEquals(conversationEntity2.id, result)
    }

    @Test
    fun givenExistingConversation_ThenConversationGroupStateCanBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.updateConversationGroupState(
            ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
            (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
        )
        val result = conversationDAO.observeGetConversationByQualifiedID(conversationEntity2.id).first()
        assertEquals(
            (result?.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState, ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
        )
    }

    @Test
    fun givenExistingConversation_ThenConversationIsUpdatedOnInsert() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        val updatedConversation1Entity = conversationEntity1.copy(name = "Updated conversation1")
        conversationDAO.insertConversation(updatedConversation1Entity)
        val result = conversationDAO.observeGetConversationByQualifiedID(conversationEntity1.id).first()
        assertEquals(updatedConversation1Entity, result)
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeInserted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMember(member1, conversationEntity1.id)

        assertEquals(listOf(member1), conversationDAO.getAllMembers(conversationEntity1.id).first())
    }

    @Test
    fun givenExistingConversation_ThenMemberCanBeDeleted() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMember(member1, conversationEntity1.id)
        conversationDAO.deleteMemberByQualifiedID(conversationEntity1.id, member1.user)

        assertEquals(emptyList(), conversationDAO.getAllMembers(conversationEntity1.id).first())
    }

    @Test
    fun givenExistingConversation_ThenAllMembersCanBeRetrieved() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMembers(listOf(member1, member2), conversationEntity1.id)

        assertEquals(setOf(member1, member2), conversationDAO.getAllMembers(conversationEntity1.id).first().toSet())
    }

    @Test
    fun givenExistingMLSConversation_whenAddingMembersByGroupId_ThenAllMembersCanBeRetrieved() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.insertMembers(
            listOf(member1, member2),
            (conversationEntity2.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
        )

        assertEquals(setOf(member1, member2), conversationDAO.getAllMembers(conversationEntity2.id).first().toSet())
    }

    @Test
    fun givenExistingConversation_ThenInsertedOrUpdatedMembersAreRetrieved() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
            member = member1,
            status = ConnectionEntity.State.ACCEPTED,
            conversationID = conversationEntity1.id
        )

        assertEquals(
            setOf(member1), conversationDAO.getAllMembers(conversationEntity1.id).first().toSet()
        )
    }

    @Test
    fun givenExistingConversation_ThenUserTableShouldBeUpdatedOnlyAndNotReplaced() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        userDAO.insertUser(user1.copy(connectionStatus = ConnectionEntity.State.NOT_CONNECTED))

        conversationDAO.updateOrInsertOneOnOneMemberWithConnectionStatus(
            member = member1,
            status = ConnectionEntity.State.SENT,
            conversationID = conversationEntity1.id
        )

        assertEquals(setOf(member1), conversationDAO.getAllMembers(conversationEntity1.id).first().toSet())
        assertEquals(ConnectionEntity.State.SENT, userDAO.getUserByQualifiedID(user1.id).first()?.connectionStatus)
        assertEquals(user1.name, userDAO.getUserByQualifiedID(user1.id).first()?.name)
    }

    @Test
    fun givenAnExistingConversation_WhenUpdatingTheMutingStatus_ThenConversationShouldBeUpdated() = runTest {
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.updateConversationMutedStatus(
            conversationId = conversationEntity2.id,
            mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED,
            mutedStatusTimestamp = 1649702788L
        )

        val result = conversationDAO.observeGetConversationByQualifiedID(conversationEntity2.id).first()

        assertEquals(ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED, result?.mutedStatus)
    }

    @Test
    fun givenMultipleConversations_whenGettingConversationsForNotifications_thenOnlyUnnotifiedConversationsAreReturned() = runTest {

        // GIVEN
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.insertConversation(conversationEntity3)

        // WHEN
        // Updating the last notified date to later than last modified
        conversationDAO
            .updateConversationNotificationDate(
                QualifiedIDEntity("2", "wire.com"),
                "2022-03-30T15:37:10.000Z"
            )

        val result = conversationDAO.getConversationsForNotifications().first()

        // THEN
        // only conversation one should be selected for notifications
        assertEquals(listOf(conversationEntity1, conversationEntity3), result)
    }

    @Test
    fun givenMultipleConversations_whenGettingConversations_thenOrderIsCorrect() = runTest {
        // GIVEN
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
        conversationDAO.insertConversation(conversationEntity3)

        // WHEN
        // Updating the last notified date to later than last modified
        conversationDAO
            .updateConversationNotificationDate(
                QualifiedIDEntity("2", "wire.com"),
                "2022-03-30T15:37:10.000Z"
            )

        val result = conversationDAO.getConversationsForNotifications().first()
        // THEN
        // The order of the conversations is not affected
        assertEquals(conversationEntity1, result.first())
        assertEquals(conversationEntity3, result[1])

    }

    @Test
    fun givenConversation_whenInsertingMembers_thenMembersShouldNotBeDuplicated() = runTest {
        val expected = listOf(member1, member2)

        conversationDAO.insertConversation(conversationEntity1)

        conversationDAO.insertMember(member1, conversationEntity1.id)
        conversationDAO.insertMember(member2, conversationEntity1.id)
        conversationDAO.getAllMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
        conversationDAO.insertMember(member1, conversationEntity1.id)
        conversationDAO.insertMember(member2, conversationEntity1.id)
        conversationDAO.getAllMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
    }

    @Test
    fun givenMultipleConversation_whenInsertingMembers_thenMembersAreInserted() = runTest {
        val expected = listOf(member1, member2)

        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)


        conversationDAO.insertMember(member1, conversationEntity1.id)
        conversationDAO.insertMember(member2, conversationEntity1.id)
        conversationDAO.getAllMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
        conversationDAO.insertMember(member1, conversationEntity2.id)
        conversationDAO.insertMember(member2, conversationEntity2.id)
        conversationDAO.getAllMembers(conversationEntity2.id).first().also { actual ->
            assertEquals(expected, actual)
        }
        conversationDAO.getAllMembers(conversationEntity1.id).first().also { actual ->
            assertEquals(expected, actual)
        }
    }


    @Test
    fun givenConversation_whenInsertingStoredConversation_thenLastChangesTimeIsNotChanged() = runTest {
        val convStored = conversationEntity1.copy(
            lastNotificationDate = "2022-04-30T15:36:00.000Z", lastModifiedDate = "2022-03-30T15:36:00.000Z", name = "old name"
        )
        val convAfterSync = conversationEntity1.copy(
            lastNotificationDate = "2023-04-30T15:36:00.000Z", lastModifiedDate = "2023-03-30T15:36:00.000Z", name = "new name"
        )

        val expected = convAfterSync.copy(lastModifiedDate = "2022-03-30T15:36:00.000Z", lastNotificationDate = "2022-04-30T15:36:00.000Z")
        conversationDAO.insertConversation(convStored)
        conversationDAO.insertConversation(convAfterSync)

        val actual = conversationDAO.observeGetConversationByQualifiedID(convAfterSync.id).first()
        assertEquals(expected, actual)
    }


    private companion object {
        val user1 = newUserEntity(id = "1")
        val user2 = newUserEntity(id = "2")

        const val teamId = "teamId"

        val conversationEntity1 = ConversationEntity(
            QualifiedIDEntity("1", "wire.com"),
            "conversation1",
            ConversationEntity.Type.ONE_ON_ONE,
            teamId,
            ConversationEntity.ProtocolInfo.Proteus,
            lastNotificationDate = null,
            lastModifiedDate = "2022-03-30T15:36:00.000Z",
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
        )
        val conversationEntity2 = ConversationEntity(
            QualifiedIDEntity("2", "wire.com"),
            "conversation2",
            ConversationEntity.Type.ONE_ON_ONE,
            null,
            ConversationEntity.ProtocolInfo.MLS("group2", ConversationEntity.GroupState.ESTABLISHED),
            lastNotificationDate = null,
            lastModifiedDate = "2021-03-30T15:36:00.000Z",
            mutedStatus = ConversationEntity.MutedStatus.ALL_MUTED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
        )

        val conversationEntity3 = ConversationEntity(
            QualifiedIDEntity("3", "wire.com"),
            "conversation3",
            ConversationEntity.Type.GROUP,
            null,
            ConversationEntity.ProtocolInfo.MLS("group3", ConversationEntity.GroupState.ESTABLISHED),
            // This conversation was modified after the last time the user was notified about it
            lastNotificationDate = "2021-03-30T15:30:00.000Z",
            lastModifiedDate = "2021-03-30T15:36:00.000Z",
            // and it's status is set to be only notified if there is a mention for the user
            mutedStatus = ConversationEntity.MutedStatus.ONLY_MENTIONS_ALLOWED,
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER)
        )

        val member1 = Member(user1.id, Member.Role.Admin)
        val member2 = Member(user2.id, Member.Role.Member)
    }
}
