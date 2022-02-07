package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.Database
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals

class MetadataDAOTest: BaseDatabaseTest() {

    val value1 = "value1"
    val value2 = "value2"

    val key1 = "key1"
    val key2 = "key2"

    lateinit var db: Database

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenNonExistingKey_thenValueCanBeStored() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        assertEquals(value1, db.metadataDAO.valueByKey(key1))
    }

    @Test
    fun givenExistingKey_thenExistingValueCanOverwritten() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        db.metadataDAO.insertValue(value2, key1)
        assertEquals(value2, db.metadataDAO.valueByKey(key1))
    }

    @Test
    fun givenExistingKey_thenValueCanBeRetrieved() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        assertEquals(value1, db.metadataDAO.valueByKey(key1))
    }

    @Test
    fun giveNonExistingKey_thenNullValueWillBeReturned() = runTest {
        db.metadataDAO.insertValue(value1, key1)
        assertNull(db.metadataDAO.valueByKey(key2))
    }
}
