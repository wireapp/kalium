package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.Database
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserDAOTest : BaseDatabaseTest() {

    val user1 = newUserEntity(id = "1")
    val user2 = newUserEntity(id = "2")
    val user3 = newUserEntity(id = "3")

    lateinit var db: Database

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
        val updatedUser1 = UserEntity(user1.id, "John Doe", "johndoe", "email1", "phone1", 1, "team")
        db.userDAO.updateUser(updatedUser1)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(result, updatedUser1)
    }

    @Test
    fun givenListOfUsers_ThenUserCanBeQueriedByName() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(user1.id, "John Doe", "johndoe", "email1", "phone1", 1, "team")

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

    @Test
    fun givenRetrievedUser_ThenUpdatesArePropagatedThroughFlow() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = UserEntity(user1.id, "John Doe", "johndoe", "email1", "phone1", 1, "team")

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByUserEmail_ThenResultsEmailIsEqualToContainsTheQueriedEmail() = runTest {
        //given
        val expectedResult = listOf(
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
            )
        )

        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
            )
        ) + expectedResult

        db.userDAO.insertUsers(mockUsers)
        //when
        val searchQuery = "testEmail2@wire.com"
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        assertTrue { searchResult.isNotEmpty() }
        assertEquals(searchResult, expectedResult)
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByName_ThenTheResultIsEqualToTheUserWithQueriedUserName() = runTest {
        //given
        val expectedResult = listOf(
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
            )
        )

        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
            )
        ) + expectedResult

        db.userDAO.insertUsers(mockUsers)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail("testName2").first()
        //then
        assertTrue { searchResult.isNotEmpty() }
        assertEquals(searchResult, expectedResult)
    }

    @Test
    fun givenAExistingUsers_WhenQueriedUserByHandle_ThenTheResultIsEqualToTheOneUserWithQueriedHandle() = runTest {
        //given
        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
            ), UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
            )
        )
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchQuery = "testHandle2"
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        assertTrue { searchResult.size == 1 }
        assertEquals(searchResult.first().handle, searchQuery)
    }

    @Test
    fun givenAExistingUser_WhenQueriedUserByPartOfEmail_ThenResultContainsAEntitiesWithThatPartOfUserEmail() = runTest {
        //given
        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
            ), UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
            )
        )
        db.userDAO.insertUsers(mockUsers)
        //when
        val searchQuery = mockUserEmail.substring(5, mockUserEmail.length)
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        searchResult.forEach { userEntity ->
            assertNotNull(userEntity.email)
            assertContains(userEntity.email!!, searchQuery)
        }
    }

    @Test
    fun givenAExistingUser_ThenQueriedUserBySpecificNameContainsTheQueriedName() = runTest {
        //given
        val mockName = "testName"
        val mockUser = newUserEntity(name = mockName)
        db.userDAO.insertUser(mockUser)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(mockName).first()
        //then
        searchResult.forEach { userEntity ->
            assertNotNull(userEntity)
            assertEquals(userEntity.name, mockName)
        }
    }

    @Test
    fun givenAExistingUser_ThenQueriedUserByPartOfNameContainsTheSearchQuery() = runTest {
        //given
        val mockName = "testName"
        val mockUser = newUserEntity(name = mockName)
        db.userDAO.insertUser(mockUser)
        //when
        val searchQuery = mockName.substring(5, mockName.length)
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        searchResult.forEach { userEntity ->
            assertNotNull(userEntity.name)
            assertContains(userEntity.name!!, searchQuery)
        }
    }

    @Test
    fun givenAExistingUser_ThenQueriedUserBySpecificHandleContainsTheQueriedHandle() = runTest {
        //given
        val mockHandle = "testHandle"
        val mockUser = newUserEntity(handle = mockHandle)
        db.userDAO.insertUser(mockUser)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(mockHandle).first()
        //then
        searchResult.forEach { userEntity ->
            assertEquals(userEntity.handle, mockHandle)
        }
    }

    @Test
    fun givenAExistingUser_ThenQueriedUserByPartOfHandleContainsTheSearchQuery() = runTest {
        //given
        val mockHandle = "testHandle"
        val mockUser = newUserEntity(handle = mockHandle)
        db.userDAO.insertUser(mockUser)
        //when
        val searchQuery = mockHandle.substring(5, mockHandle.length)
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        searchResult.forEach { userEntity ->
            assertNotNull(userEntity.handle)
            assertContains(userEntity.handle!!, searchQuery)
        }
    }

    @Test
    fun givenAExistingUsersWithCommonEmailPrefix_ThenQueriedUserEmailByPartOfEmailAllContainTheSearchQuery() = runTest {
        //given
        val commonEmailUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName1",
                handle = "testHandle1",
                email = "testEmail1@wire.com",
                phone = "testPhone1",
                accentId = 1,
                team = "testTeam1",
            ),
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
            )
        )

        val notCommonEmailUsers = listOf(
            UserEntity(
                id = QualifiedID("4", "wire.com"),
                name = "testName4",
                handle = "testHandle4",
                email = "someDifferentEmail1@wire.com",
                phone = "testPhone4",
                accentId = 4,
                team = "testTeam4",
            ),
            UserEntity(
                id = QualifiedID("5", "wire.com"),
                name = "testName5",
                handle = "testHandle5",
                email = "someDifferentEmail2@wire.com",
                phone = "testPhone5",
                accentId = 5,
                team = "testTeam5",
            )
        )

        val mockUsers = commonEmailUsers + notCommonEmailUsers

        db.userDAO.insertUsers(mockUsers)
        //when
        val searchQuery = "testEmail"
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        assertEquals(searchResult, commonEmailUsers)
        searchResult.forEach { newUserEntity ->
            assertNotNull(newUserEntity.email)
            assertContains(newUserEntity.email!!, searchQuery)
        }
    }

    @Test
    fun givenAExistingUsers_ThenQueriedUsersByNonExistingEmailReturnEmptyList() = runTest {
        //given
        val mockUsers = listOf(
            UserEntity(
                id = QualifiedID("1", "wire.com"),
                name = "testName",
                handle = "testHandle",
                email = "testEmail@wire.com",
                phone = "testPhone",
                accentId = 1,
                team = "testTeam",
            ),
            UserEntity(
                id = QualifiedID("2", "wire.com"),
                name = "testName2",
                handle = "testHandle2",
                email = "testEmail2@wire.com",
                phone = "testPhone2",
                accentId = 2,
                team = "testTeam2",
            ),
            UserEntity(
                id = QualifiedID("3", "wire.com"),
                name = "testName3",
                handle = "testHandle3",
                email = "testEmail3@wire.com",
                phone = "testPhone3",
                accentId = 3,
                team = "testTeam3",
            )
        )
        db.userDAO.insertUsers(mockUsers)
        //when
        val nonExistingEmailQuery = "doesnotexist@wire.com"
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(nonExistingEmailQuery).first()
        //then
        assertTrue { searchResult.isEmpty() }
    }

}
