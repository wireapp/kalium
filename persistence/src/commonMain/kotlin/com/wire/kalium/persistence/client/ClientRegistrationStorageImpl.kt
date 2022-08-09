package com.wire.kalium.persistence.client

import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface ClientRegistrationStorage {
    suspend fun getRegisteredClientId(): String?
    suspend fun setRegisteredClientId(registeredClientId: String?)
    suspend fun observeRegisteredClientId(): Flow<String?>
}

class ClientRegistrationStorageImpl(private val metadataDAO: MetadataDAO) : ClientRegistrationStorage {


    override suspend fun getRegisteredClientId(): String? = observeRegisteredClientId().first()

    override suspend fun setRegisteredClientId(registeredClientId: String?) = registeredClientId?.let {
        metadataDAO.insertValue(registeredClientId, REGISTERED_CLIENT_ID_KEY)
    } ?: run {
        metadataDAO.deleteValue(REGISTERED_CLIENT_ID_KEY)
    }

    override suspend fun observeRegisteredClientId(): Flow<String?> = metadataDAO.valueByKeyFlow(REGISTERED_CLIENT_ID_KEY)

    private companion object {
        const val REGISTERED_CLIENT_ID_KEY = "registered_client_id"
    }
}
