package com.wire.kalium.persistence.dao

import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MetadataDAOTest : BaseDatabaseTest() {

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

    @Test
    fun giveExistingKey_whenValueHasBeenModified_thenEmitNewValue() = runTest(dispatcher) {
        launch(dispatcher) {
            metadataDAO.insertValue(value1, key1)
            metadataDAO.valueByKeyFlow(key1).test {
                assertEquals(value1, this.awaitItem())
                metadataDAO.insertValue(value2, key1)
                assertEquals(value2, this.awaitItem())
            }
        }
    }

    @Test
    fun giveExistingKey_whenOtherValueHasBeenModified_thenDoNotReEmitTheSameValue() = runTest(dispatcher) {
        launch(dispatcher) {
            metadataDAO.insertValue(value1, key1)
            metadataDAO.valueByKeyFlow(key1).test {
                assertEquals(value1, this.awaitItem())
                metadataDAO.insertValue(value2, key2)
                this.expectNoEvents()
            }
        }
    }
}
