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
    fun givenAExistingUser_ThenQueriedUserBySpecificEmailContainsTheQueriedEmail() = runTest {
        //given
        val mockUserEmail = "test@wire.com"
        val mockUser = newUserEntity(email = mockUserEmail)
        db.userDAO.insertUser(mockUser)
        //when
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(mockUserEmail).first()
        //then
        assertNotNull(searchResult)
        assertEquals(searchResult.email, mockUserEmail)
    }

    @Test
    fun givenAExistingUser_ThenQueriedUserBySpecificPartOfEmailContainsTheSearchQuery() = runTest {
        //given
        val mockUserEmail = "test@wire.com"
        val mockUser = newUserEntity(email = mockUserEmail)
        db.userDAO.insertUser(mockUser)
        //when
        val searchQuery = mockUserEmail.substring(5, mockUserEmail.length)
        val searchResult = db.userDAO.getUserByNameOrHandleOrEmail(searchQuery).first()
        //then
        assertNotNull(searchResult)
        assertNotNull(searchResult.email)
        assertContains(searchResult.email!!, searchQuery)
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
        assertNotNull(searchResult)
        assertEquals(searchResult.name, mockName)
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
        assertNotNull(searchResult)
        assertNotNull(searchResult.name)
        assertContains(searchResult.name!!, searchQuery)
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
        assertNotNull(searchResult)
        assertEquals(searchResult.handle, mockHandle)
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
        assertNotNull(searchResult)
        assertNotNull(searchResult.handle)
        assertContains(searchResult.handle!!, searchQuery)
    }

}
