package com.wire.kalium.persistence

import com.wire.kalium.persistence.db.GlobalDatabaseProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

class TestGlobalDatabase(
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
) {

    val provider: GlobalDatabaseProvider

    init {
        deleteTestGlobalDatabase()
        provider = createTestGlobalDatabase()
    }

    fun delete() {
        deleteTestGlobalDatabase()
    }
}

internal expect fun deleteTestGlobalDatabase()

internal expect fun createTestGlobalDatabase(): GlobalDatabaseProvider
