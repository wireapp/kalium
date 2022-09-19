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

    private val value1 = "value1"
    private val value2 = "value2"

    private val key1 = "key1"
    private val key2 = "key2"

    private lateinit var metadataDAO: MetadataDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        metadataDAO = db.metadataDAO
    }

    @Test
    fun givenNonExistingKey_thenValueCanBeStored() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        assertEquals(value1, metadataDAO.valueByKeyFlow(key1).first())
    }

    @Test
    fun givenExistingKey_thenExistingValueCanBeOverwritten() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        metadataDAO.insertValue(value2, key1)
        assertEquals(value2, metadataDAO.valueByKeyFlow(key1).first())
    }

    @Test
    fun givenExistingKey_thenValueCanBeRetrieved() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        assertEquals(value1, metadataDAO.valueByKeyFlow(key1).first())
    }

    @Test
    fun giveNonExistingKey_thenNullValueWillBeReturned() = runTest(dispatcher) {
        metadataDAO.insertValue(value1, key1)
        assertNull(metadataDAO.valueByKeyFlow(key2).first())
    }
}
