package com.wire.kalium.persistence.client

import androidx.datastore.preferences.core.stringPreferencesKey
import com.wire.kalium.persistence.data_store.DataStoreStorage
import com.wire.kalium.persistence.model.DataStoreResult
import com.wire.kalium.persistence.model.PersistenceSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

actual class SessionDaoImpl(
    private val dataStoreStorage: DataStoreStorage
) : SessionDao {
    override suspend fun addSession(persistenceSession: PersistenceSession) {
        when (val result = allSessions()) {
            is DataStoreResult.Success -> {
                if (result.data.containsKey(persistenceSession.userId).not()) {
                    result.data.toMutableMap().put(persistenceSession.userId, persistenceSession)
                    saveSessions(result.data)
                }
            }
            is DataStoreResult.DataNotFound -> saveSessions(mapOf(persistenceSession.userId to persistenceSession))
        }
    }

    override suspend fun updateCurrentSession(persistenceSession: PersistenceSession) =
        dataStoreStorage.setString(persistenceSession.userId, CURRENT_SESSION_KEY)


    override suspend fun deleteSession(userId: String) {
        allSessionsFlow().collect { result ->
            when (result) {
                is DataStoreResult.Success -> {
                    result.data.toMutableMap().remove(userId)
                    saveSessions(result.data)
                    return@collect
                }
                is DataStoreResult.DataNotFound -> return@collect
            }
        }
    }

    override suspend fun currentSession(): PersistenceSession? {
        var currentPersistenceSession: PersistenceSession? = null
        dataStoreStorage.getString(CURRENT_SESSION_KEY).collect { currentUserId ->
            allSessionsFlow().collect { result ->
                currentPersistenceSession = when (result) {
                    is DataStoreResult.Success -> result.data[currentUserId]
                    is DataStoreResult.DataNotFound -> null
                }
            }
        }
        return currentPersistenceSession
    }


    override suspend fun existSessions(): Boolean = dataStoreStorage.hasKey(stringPreferencesKey(SESSION_DATASTORE_KEY))

    override fun allSessionsFlow(): Flow<DataStoreResult<Map<String, PersistenceSession>>> {
        return dataStoreStorage.getSecuredData(key = SESSION_DATASTORE_KEY, securityKeyAlias = SESSION_ENC_KEY_ALIAS)
    }

    override suspend fun allSessions(): DataStoreResult<Map<String, PersistenceSession>> = allSessionsFlow().first()

    private suspend fun saveSessions(sessions: Map<String, PersistenceSession>) {
        dataStoreStorage.setSecuredData(sessions, SESSION_DATASTORE_KEY, SESSION_ENC_KEY_ALIAS)
    }

    private companion object {
        private const val SESSION_DATASTORE_KEY = "session_data_store_key"
        private const val SESSION_ENC_KEY_ALIAS = "session_store_key"
        private const val CURRENT_SESSION_KEY = "current_session_key"
    }
}
