package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseProvider
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDAOTest : BaseDatabaseTest() {

    private val user1 = newUserEntity(id = "1")
    private val user2 = newUserEntity(id = "2")
    private val user3 = newUserEntity(id = "3")

    lateinit var db: UserDatabaseProvider

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenUser_ThenUserCanBeInserted() = runTest {
        db.userDAO.insertUser(user1)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(result, user1)
    }

    @Test
    fun givenListOfUsers_ThenMultipleUsersCanBeInsertedAtOnce() = runTest {
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        val result1 = db.userDAO.getUserByQualifiedID(user1.id).first()
        val result2 = db.userDAO.getUserByQualifiedID(user2.id).first()
        val result3 = db.userDAO.getUserByQualifiedID(user3.id).first()
        assertEquals(result1, user1)
        assertEquals(result2, user2)
        assertEquals(result3, user3)
    }

    @Test
    fun givenExistingUser_ThenUserCanBeDeleted() = runTest {
        db.userDAO.insertUser(user1)
        db.userDAO.deleteUserByQualifiedID(user1.id)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertNull(result)
    }

    @Test
    fun givenExistingUser_ThenUserCanBeUpdated() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(
            user1.id, "John Doe", "johndoe", "email1", "phone1", 1, "team",
            UserEntity.ConnectionState.ACCEPTED, UserAssetIdEntity(), UserAssetIdEntity()
        )
        db.userDAO.updateUser(updatedUser1)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(result, updatedUser1)
    }

    @Test
    fun givenListOfUsers_ThenUserCanBeQueriedByName() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(
            user1.id, "John Doe", "johndoe", "email1", "phone1", 1, "team",
            UserEntity.ConnectionState.ACCEPTED, UserAssetIdEntity(), UserAssetIdEntity()
        )

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

    @Test
    fun givenRetrievedUser_ThenUpdatesArePropagatedThroughFlow() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(
            user1.id, "John Doe", "johndoe", "email1", "phone1", 1, "team",
            UserEntity.ConnectionState.ACCEPTED, null, null
        )

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByUserEmail_ThenResultsIsEqualToThatUser() = runTest {
        //given
        val user1 = USER_ENTITY_1
        val user2 = USER_ENTITY_2.copy(email = "uniqueEmailForUser2")
        val user3 = USER_ENTITY_3
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(user2.email!!, UserEntity.ConnectionState.ACCEPTED)
        //then
        assertEquals(searchResult, listOf(user2))
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByName_ThenResultsIsEqualToThatUser(): TestResult {
        return runTest {
            //given
            val user1 = USER_ENTITY_1
            val user2 = USER_ENTITY_3
            val user3 = USER_ENTITY_3.copy(handle = "uniqueHandlForUser3")
            db.userDAO.insertUsers(listOf(user1, user2, user3))
            //when
            val searchResult =
                db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(user3.handle!!, UserEntity.ConnectionState.ACCEPTED)
            //then
            assertEquals(searchResult, listOf(user3))
        }
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByHandle_ThenResultsIsEqualToThatUser() = runTest {
        //given
        val user1 = USER_ENTITY_1.copy(name = "uniqueNameFor User1")
        val user2 = USER_ENTITY_2
        val user3 = USER_ENTITY_3
        db.userDAO.insertUsers(listOf(user1, user2, user3))
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(user1.name!!, UserEntity.ConnectionState.ACCEPTED)
        //then
        assertEquals(searchResult, listOf(user1))
    }

    @Test
    fun givenAExistingUsersWithCommonEmailPrefix_WhenQueriedWithThatEmailPrefix_ThenResultIsEqualToTheUsersWithCommonEmailPrefix() =
        runTest {
            //given
            val commonEmailPrefix = "commonEmail"

            val commonEmailUsers = listOf(
                USER_ENTITY_1.copy(email = commonEmailPrefix + "u1@example.org"),
                USER_ENTITY_2.copy(email = commonEmailPrefix + "u2@example.org"),
                USER_ENTITY_3.copy(email = commonEmailPrefix + "u3@example.org")
            )
            val notCommonEmailUsers = listOf(
                UserEntity(
                    id = QualifiedIDEntity("4", "wire.com"),
                    name = "testName4",
                    handle = "testHandle4",
                    email = "someDifferentEmail1@wire.com",
                    phone = "testPhone4",
                    accentId = 4,
                    team = "testTeam4",
                    UserEntity.ConnectionState.ACCEPTED,
                    null, null
                ),
                UserEntity(
                    id = QualifiedIDEntity("5", "wire.com"),
                    name = "testName5",
                    handle = "testHandle5",
                    email = "someDifferentEmail2@wire.com",
                    phone = "testPhone5",
                    accentId = 5,
                    team = "testTeam5",
                    UserEntity.ConnectionState.ACCEPTED,
                    null, null
                )
            )
            val mockUsers = commonEmailUsers + notCommonEmailUsers

            db.userDAO.insertUsers(mockUsers)
            //when
            val searchResult =
                db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonEmailPrefix, UserEntity.ConnectionState.ACCEPTED)
            //then
            assertEquals(searchResult, commonEmailUsers)
        }

    //when entering
    @Test
    fun givenAExistingUsers_WhenQueriedWithNonExistingEmail_ThenReturnNoResults() = runTest {
        //given
        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.insertUsers(mockUsers)

        val nonExistingEmailQuery = "doesnotexist@wire.com"
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(nonExistingEmailQuery, UserEntity.ConnectionState.ACCEPTED)
        //then
        assertTrue { searchResult.isEmpty() }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonEmailPrefix_ThenResultsUsersEmailContainsThatPrefix() = runTest {
        //given
        val commonEmailPrefix = "commonEmail"

        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonEmailPrefix, UserEntity.ConnectionState.ACCEPTED)
        //then
        searchResult.forEach { userEntity ->
            assertContains(userEntity.email!!, commonEmailPrefix)
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonHandlePrefix_ThenResultsUsersHandleContainsThatPrefix() = runTest {
        //given
        val commonHandlePrefix = "commonHandle"

        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonHandlePrefix, UserEntity.ConnectionState.ACCEPTED)
        //then
        searchResult.forEach { userEntity ->
            assertContains(userEntity.handle!!, commonHandlePrefix)
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonNamePrefix_ThenResultsUsersNameContainsThatPrefix() = runTest {
        //given
        val commonNamePrefix = "commonName"

        val mockUsers = listOf(USER_ENTITY_1, USER_ENTITY_2, USER_ENTITY_3)
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonNamePrefix, UserEntity.ConnectionState.ACCEPTED)
        //then
        searchResult.forEach { userEntity ->
            assertContains(userEntity.name!!, commonNamePrefix)
        }
    }

    @Test
    fun givenAExistingUsers_whenQueriedWithCommonPrefixForNameHandleAndEmail_ThenResultsUsersNameHandleAndEmailContainsThatPrefix() =
        runTest {
            //given
            val commonPrefix = "common"

            val mockUsers = listOf(
                USER_ENTITY_1.copy(name = commonPrefix + "u1"),
                USER_ENTITY_2.copy(handle = commonPrefix + "u2"),
                USER_ENTITY_3.copy(email = commonPrefix + "u3")
            )
            db.userDAO.insertUsers(mockUsers)
            //when
            val searchResult =
                db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonPrefix, UserEntity.ConnectionState.ACCEPTED)
            //then
            assertEquals(mockUsers, searchResult)
        }

    @Test
    fun givenAExistingUsers_whenQueried_ThenResultsUsersAreConnected() = runTest {
        //given
        val commonPrefix = "common"

        val mockUsers = listOf(
            USER_ENTITY_1.copy(name = commonPrefix + "u1"),
            USER_ENTITY_2.copy(handle = commonPrefix + "u2"),
            USER_ENTITY_3.copy(email = commonPrefix + "u3")
        )

        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonPrefix, UserEntity.ConnectionState.ACCEPTED)
        //then
        searchResult.forEach { userEntity ->
            assertEquals(UserEntity.ConnectionState.ACCEPTED, userEntity.connectionStatus)
        }
    }

    @Test
    fun givenAConnectedExistingUsersAndNonConnected_whenQueried_ThenResultsUsersAreConnected() = runTest {
        //given
        val commonPrefix = "common"

        val mockUsers = listOf(
            USER_ENTITY_1.copy(name = commonPrefix + "u1"),
            USER_ENTITY_2.copy(handle = commonPrefix + "u2"),
            USER_ENTITY_3.copy(email = commonPrefix + "u3", connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED),
            USER_ENTITY_4.copy(email = commonPrefix + "u4", connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED)
        )

        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonPrefix, UserEntity.ConnectionState.ACCEPTED)
        //then
        assertTrue(searchResult.size == 2)
        searchResult.forEach { userEntity ->
            assertEquals(UserEntity.ConnectionState.ACCEPTED, userEntity.connectionStatus)
        }
    }

    @Test
    fun givenAConnectedExistingUserAndNonConnected_whenQueried_ThenResultIsTheConnectedUser() = runTest {
        //given
        val commonPrefix = "common"

        val expectedResult = listOf(USER_ENTITY_1.copy(name = commonPrefix + "u1"))

        val mockUsers = listOf(
            USER_ENTITY_1.copy(name = commonPrefix + "u1"),
            USER_ENTITY_2.copy(handle = commonPrefix + "u2", connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED),
            USER_ENTITY_3.copy(name = commonPrefix + "u3", connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED),
            USER_ENTITY_4.copy(email = commonPrefix + "u4", connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED)
        )

        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult =
            db.userDAO.getUserByNameOrHandleOrEmailAndConnectionState(commonPrefix, UserEntity.ConnectionState.ACCEPTED)
        //then
        assertEquals(expectedResult, searchResult)
    }

    @Test
    fun givenTheListOfUser_whenQueriedByHandle_ThenResultContainsOnlyTheUserHavingTheHandleAndAreConnected() = runTest {
        val expectedResult = listOf(
            USER_ENTITY_1.copy(handle = "@someHandle"),
            USER_ENTITY_4.copy(handle = "@someHandle1")
        )

        val mockUsers = listOf(
            USER_ENTITY_1.copy(handle = "@someHandle"),
            USER_ENTITY_2.copy(connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED),
            USER_ENTITY_3.copy(connectionStatus = UserEntity.ConnectionState.NOT_CONNECTED),
            USER_ENTITY_4.copy(handle = "@someHandle1")
        )

        db.userDAO.insertUsers(mockUsers)

        //when
        val searchResult = db.userDAO.getUserByHandleAndConnectionState("some", UserEntity.ConnectionState.ACCEPTED)

        //then
        assertEquals(expectedResult, searchResult)
    }

    @Test
    fun givenAExistingUsers_whenUpdatingTheirValues_ThenResultsIsEqualToThatUserButWithFieldsModified() = runTest {
        //given
        val newNameA = "new user naming a"
        val newNameB = "new user naming b"
        db.userDAO.insertUsers(listOf(user1, user3))
        //when
        val updatedUser1 = user1.copy(name = newNameA)
        val updatedUser3 = user3.copy(name = newNameB)
        db.userDAO.updateUsers(listOf(updatedUser1, updatedUser3))
        //then
        val updated1 = db.userDAO.getUserByQualifiedID(updatedUser1.id)
        val updated3 = db.userDAO.getUserByQualifiedID(updatedUser3.id)
        assertEquals(newNameA, updated1.first()?.name)
        assertEquals(newNameB, updated3.first()?.name)
    }

    @Test
    fun givenAExistingUsers_whenUpdatingTheirValuesAndRecordNotExists_ThenResultsOneUpdatedAnotherInserted() = runTest {
        //given
        val newNameA = "new user naming a"
        db.userDAO.insertUser(user1)
        //when
        val updatedUser1 = user1.copy(name = newNameA)
        db.userDAO.updateUsers(listOf(updatedUser1, user2))
        //then
        val updated1 = db.userDAO.getUserByQualifiedID(updatedUser1.id)
        val inserted2 = db.userDAO.getUserByQualifiedID(user2.id)
        assertEquals(newNameA, updated1.first()?.name)
        assertNotNull(inserted2)
    }


    private companion object {
        val USER_ENTITY_1 = newUserEntity(QualifiedIDEntity("1", "wire.com"))
        val USER_ENTITY_2 = newUserEntity(QualifiedIDEntity("2", "wire.com"))
        val USER_ENTITY_3 = newUserEntity(QualifiedIDEntity("3", "wire.com"))
        val USER_ENTITY_4 = newUserEntity(QualifiedIDEntity("4", "wire.com"))
    }

}
