package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface ClientRegistrationStorage {
    suspend fun getRegisteredClientId(): String?
    suspend fun setRegisteredClientId(registeredClientId: String)
    suspend fun observeRegisteredClientId(): Flow<String?>
}

class ClientRegistrationStorageImpl(private val metadataDAO: MetadataDAO) : ClientRegistrationStorage {


    override suspend fun getRegisteredClientId(): String? = observeRegisteredClientId().first()

    override suspend fun setRegisteredClientId(registeredClientId: String) =
        metadataDAO.insertValue(registeredClientId, REGISTERED_CLIENT_ID_KEY)

    override suspend fun observeRegisteredClientId(): Flow<String?> = metadataDAO.valueByKey(REGISTERED_CLIENT_ID_KEY)

    var registeredClientId: String?
        get() = kaliumPreferences.getString(REGISTERED_CLIENT_ID_KEY)
        set(value) = kaliumPreferences.putString(REGISTERED_CLIENT_ID_KEY, value)

    private companion object {
        const val REGISTERED_CLIENT_ID_KEY = "registered_client_id"
    }
}
