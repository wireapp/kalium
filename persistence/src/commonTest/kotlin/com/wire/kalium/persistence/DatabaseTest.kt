package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.AppDatabase
import com.wire.kalium.persistence.db.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DatabaseTest: BaseDatabaseTest() {

    @Test
    fun givenDatabase_ThenUserCanBeStoredAndRetrieved() = runTest {
        val factory = createDatabaseDriverFactory()
        val database = AppDatabase(factory.createDriver())
        val user = User(1, "John Doe")

        database.usersQueries.insertUser(user.id, user.name)
        assertEquals(listOf(user), database.usersQueries.selectAllUsers().executeAsList())
    }

}
