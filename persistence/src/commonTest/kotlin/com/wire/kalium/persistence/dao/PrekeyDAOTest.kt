package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PrekeyDAOTest: BaseDatabaseTest() {

    private lateinit var prekeyDAO: PrekeyDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        prekeyDAO = db.prekeyDAO
    }

    @Test
    fun givenOTRLastPrekeyId_whenUpdating_thenItsOnlyUpdatedIfTheNewIdIsHigher() = runTest {
        val currentStoredId = 100
        prekeyDAO.forceInsertOTRLastPrekeyId(100)

        prekeyDAO.updateOTRLastPrekeyId(50)
        assertEquals(currentStoredId, prekeyDAO.lastOTRPrekeyId())

        prekeyDAO.updateOTRLastPrekeyId(101)
        assertEquals(101, prekeyDAO.lastOTRPrekeyId())
    }


    @Test
    fun whenForceInsertingPrekeyId_thenTheNewIdIsInserted() = runTest {
        prekeyDAO.forceInsertOTRLastPrekeyId(100)

        prekeyDAO.forceInsertOTRLastPrekeyId(50)
        assertEquals(50, prekeyDAO.lastOTRPrekeyId())
    }
}
