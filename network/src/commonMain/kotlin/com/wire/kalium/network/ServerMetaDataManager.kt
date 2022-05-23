package com.wire.kalium.network

import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.WireServerDTO
import com.wire.kalium.network.utils.isSuccessful
import kotlin.coroutines.cancellation.CancellationException

interface LocalMetaDataServerManager {
    fun getLocalMetaData(backendLinks: WireServerDTO.Links): WireServerDTO?
    fun storeBackend(backend: WireServerDTO.Links, metaData: WireServerDTO.MetaData): WireServerDTO
}

internal interface ServerMetaDataManager {
    fun getLocalMetaData(backendLinks: WireServerDTO.Links): WireServerDTO?
    fun storeBackend(backend: WireServerDTO.Links, metaData: WireServerDTO.MetaData): WireServerDTO

    @Throws(KaliumException::class, CancellationException::class)
    suspend fun fetchRemoteAndStoreMetaData(backendLinks: WireServerDTO.Links): WireServerDTO
}

class ServerMetaDataManagerImpl(
    private val local: LocalMetaDataServerManager,
    private val versionApi: VersionApi,
    private val backendMetaDataUtil: BackendMetaDataUtil = BackendMetaDataUtilImpl
) : ServerMetaDataManager {
    override fun getLocalMetaData(backendLinks: WireServerDTO.Links): WireServerDTO? = local.getLocalMetaData(backendLinks)

    override fun storeBackend(backend: WireServerDTO.Links, metaData: WireServerDTO.MetaData): WireServerDTO =
        local.storeBackend(backend, metaData)

    @Throws(KaliumException::class)
    override suspend fun fetchRemoteAndStoreMetaData(backendLinks: WireServerDTO.Links): WireServerDTO =
        versionApi.fetchApiVersion(backendLinks.api).let { result ->
            if (!result.isSuccessful()) {
                throw result.kException
            } else {
                val metaData = backendMetaDataUtil.calculateApiVersion(result.value)
                storeBackend(backendLinks, metaData)
            }
        }
}
