package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow

actual class SessionDaoImpl: SessionDao {
    override suspend fun addSession(persistenceSession: PersistenceSession) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteSession(userId: String) {
        TODO("Not yet implemented")
    }

    override suspend fun currentSession(): PersistenceSession? {
        TODO("Not yet implemented")
    }

    override suspend fun updateCurrentSession(persistenceSession: PersistenceSession) {
        TODO("Not yet implemented")
    }

    override fun allSessionsFlow(): Flow<DataStoreResult<Map<String, PersistenceSession>>> {
        TODO("Not yet implemented")
    }

    override suspend fun allSessions(): DataStoreResult<Map<String, PersistenceSession>> {
        TODO("Not yet implemented")
    }

    override suspend fun existSessions(): Boolean {
        TODO("Not yet implemented")
    }
}
