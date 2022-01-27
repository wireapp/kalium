package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.Database
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserDAOTest: BaseDatabaseTest() {

    val user1 = User(QualifiedID("1", "wire.com"), "user1", "handle1")
    val user2 = User(QualifiedID("2", "wire.com"), "user2", "handle2")
    val user3 = User(QualifiedID("3", "wire.com"), "user3", "handle3")

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
    fun givenExistingUser_ThenUserBeUpdated() = runTest {
        db.userDAO.insertUser(user1)
        var updateUser1 = User(user1.id, "John Doe", "johndoe")
        db.userDAO.updateUser(updateUser1)
        val result = db.userDAO.getUserByQualifiedID(user1.id).first()
        assertEquals(result, updateUser1)
    }

    @Test
    fun givenRetrievedUser_ThenUpdatesArePropagatedThroughFlow() = runTest {
        db.userDAO.insertUser(user1)
        val updatedUser1 = User(user1.id, "John Doe", "johndoe")

        val result = db.userDAO.getUserByQualifiedID(user1.id)
        assertEquals(user1, result.first())

        db.userDAO.updateUser(updatedUser1)
        assertEquals(updatedUser1, result.first())
    }

}
