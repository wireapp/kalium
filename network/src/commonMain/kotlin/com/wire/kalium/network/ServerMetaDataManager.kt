package com.wire.kalium.network

import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.isSuccessful
import kotlin.coroutines.cancellation.CancellationException

interface LocalMetaDataServerManager {
    fun getLocalMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO?
    fun storeBackend(backend: ServerConfigDTO.Links, metaData: ServerConfigDTO.MetaData): ServerConfigDTO
}

internal interface ServerMetaDataManager {
    fun getLocalMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO?
    fun storeBackend(backend: ServerConfigDTO.Links, metaData: ServerConfigDTO.MetaData): ServerConfigDTO

    @Throws(KaliumException::class, CancellationException::class)
    suspend fun fetchRemoteAndStoreMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO
}

class ServerMetaDataManagerImpl(
    private val local: LocalMetaDataServerManager,
    private val versionApi: VersionApi,
    private val backendMetaDataUtil: BackendMetaDataUtil = BackendMetaDataUtilImpl
) : ServerMetaDataManager {
    override fun getLocalMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO? = local.getLocalMetaData(backendLinks)

    override fun storeBackend(backend: ServerConfigDTO.Links, metaData: ServerConfigDTO.MetaData): ServerConfigDTO =
        local.storeBackend(backend, metaData)

    @Throws(KaliumException::class, CancellationException::class)
    override suspend fun fetchRemoteAndStoreMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO =
        versionApi.fetchApiVersion(backendLinks.api).let { result ->
            if (!result.isSuccessful()) {
                throw result.kException
            } else {
                val metaData = backendMetaDataUtil.calculateApiVersion(result.value)
                storeBackend(backendLinks, metaData)
            }
        }
}
