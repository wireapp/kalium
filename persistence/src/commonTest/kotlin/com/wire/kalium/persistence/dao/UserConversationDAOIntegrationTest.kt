package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserConversationDAOIntegrationTest : BaseDatabaseTest() {

    private val user1 = newUserEntity(id = "1")
    private val user2 = newUserEntity(id = "2")

    private val conversationEntity1 = newConversationEntity()

    private val member1 = Member(user1.id, Member.Role.Admin)

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
    fun givenUserExists_whenInsertingMember_thenOriginalUserDetailsAreKept() = runTest {
        userDAO.insertUser(user1)

        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertMember(member1, conversationEntity1.id)

        val result = userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(user1, result)
    }

    @Test
    fun givenTheUserIsPartOfConversation_WHenGettingUsersNotPartOfConversation_ThenReturnUsersWithoutTheConversationMember() = runTest {
        val userThatIsPartOfConversation = newUserEntity(QualifiedIDEntity("3", "someDomain"))

        userDAO.upsertUsers(listOf(user1, user2, userThatIsPartOfConversation))

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId,
            listOf(
                Member(
                    user = QualifiedIDEntity(
                        "3",
                        "someDomain"
                    )
                )
            )
        )

        val result = userDAO.getUsersNotInConversation(conversationId)

        assertTrue { !result.contains(userThatIsPartOfConversation) }
    }

    @Test
    fun givenAllTheUserArePartOfConversation_WHenGettingUsersNotPartOfConversation_ThenReturnEmptyResult() = runTest {
        userDAO.upsertUsers(listOf(user1, user2))

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId,
            listOf(
                Member(
                    user = user1.id
                ),
                Member(
                    user = user2.id
                ),
            )
        )

        val result = userDAO.getUsersNotInConversation(conversationId)

        assertTrue { result.isEmpty() }
    }

    @Test
    fun givenConversationHasNoMembers_WhenGettingUsersNotPartOfConversation_ThenReturnAllTheUsers() = runTest {
        userDAO.upsertUsers(listOf(user1, user2))

        val conversationId = QualifiedIDEntity(value = "someValue", domain = "someDomain")

        createTestConversation(
            conversationId,
            emptyList()
        )

        val result = userDAO.getUsersNotInConversation(conversationId)

        assertTrue { result == listOf(user1, user2) }
    }

    private suspend fun createTestConversation(conversationIDEntity: QualifiedIDEntity, members: List<Member>) {
        conversationDAO.insertConversation(
            newConversationEntity(conversationIDEntity)
        )

        conversationDAO.insertMembers(
            memberList = members,
            conversationID = conversationIDEntity
        )
    }
}
