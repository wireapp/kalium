package com.wire.kalium.network


import com.wire.kalium.network.tools.ServerConfigDTO

interface ServerMetaDataManager {
    fun getLocalMetaData(backendLinks: ServerConfigDTO.Links): ServerConfigDTO?
    fun storeBackend(links: ServerConfigDTO.Links, metaData: ServerConfigDTO.MetaData): ServerConfigDTO?
}
