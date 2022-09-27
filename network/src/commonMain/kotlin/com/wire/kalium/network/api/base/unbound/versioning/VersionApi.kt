package com.wire.kalium.network.api.base.unbound.versioning

import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.BackendMetaDataUtilImpl
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url

interface VersionApi {
    suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<ServerConfigDTO.MetaData>
}

class VersionApiImpl internal constructor(
    private val httpClient: HttpClient,
    private val util: BackendMetaDataUtil = BackendMetaDataUtilImpl,
    private val developmentApiEnabled: Boolean
) : VersionApi {
    internal constructor(unboundNetworkClient: UnboundNetworkClient, developmentApiEnabled: Boolean) : this(
        unboundNetworkClient.httpClient,
        developmentApiEnabled = developmentApiEnabled
    )

    override suspend fun fetchApiVersion(baseApiUrl: Url): NetworkResponse<ServerConfigDTO.MetaData> = wrapKaliumResponse({
        if (it.status.value != HttpStatusCode.NotFound.value) null
        else {
            NetworkResponse.Success(VersionInfoDTO(), it)
        }
    }, {
        httpClient.get {
            setUrl(baseApiUrl, API_VERSION_PATH)
        }
    }).mapSuccess {
        util.calculateApiVersion(it, developmentApiEnabled = developmentApiEnabled)
    }

    private companion object {
        const val API_VERSION_PATH = "api-version"
    }

}
