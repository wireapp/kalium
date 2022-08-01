package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetadataDAOTest: BaseDatabaseTest() {

    val value1 = "value1"
    val value2 = "value2"

    val key1 = "key1"
    val key2 = "key2"

    lateinit var db: UserDatabaseProvider

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenNonExistingKey_thenValueCanBeStored() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        assertEquals(value1, db.metadataDAO.valueByKey(key1).first())
    }

    @Test
    fun givenExistingKey_thenExistingValueCanBeOverwritten() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        db.metadataDAO.insertValue(value2, key1)
        assertEquals(value2, db.metadataDAO.valueByKey(key1).first())
    }

    @Test
    fun givenExistingKey_thenValueCanBeRetrieved() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        assertEquals(value1, db.metadataDAO.valueByKey(key1).first())
    }

    @Test
    fun giveNonExistingKey_thenNullValueWillBeReturned() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        assertNull(db.metadataDAO.valueByKey(key2).first())
    }
}
