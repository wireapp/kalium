package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.db.UserDatabaseProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MetadataDAOTest: BaseDatabaseTest() {

    private val value1 = "value1"
    val value2 = "value2"

    private val key1 = "key1"
    private val key2 = "key2"

    lateinit var db: UserDatabaseProvider

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        db = createDatabase()
    }

    @Test
    fun givenNonExistingKey_thenValueCanBeStored() {
        db.metadataDAO.insertValue(value1, key1)
        assertEquals(value1, db.metadataDAO.valueByKey(key1))
    }

    @Test
    fun givenExistingKey_thenExistingValueCanBeOverwritten() {
        db.metadataDAO.insertValue(value1, key1)
        db.metadataDAO.insertValue(value2, key1)
        assertEquals(value2, db.metadataDAO.valueByKey(key1))
    }

    @Test
    fun givenExistingKey_thenValueCanBeRetrieved() {
        db.metadataDAO.insertValue(value1, key1)
        assertEquals(value1, db.metadataDAO.valueByKey(key1))
    }

    @Test
    fun giveNonExistingKey_thenNullValueWillBeReturned() {
        db.metadataDAO.insertValue(value1, key1)
        assertNull(db.metadataDAO.valueByKey(key2))
    }
}
