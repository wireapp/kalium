package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class PropertiesApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
) : PropertiesApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    private companion object {
        const val PATH_PROPERTIES = "properties"
    }

    override suspend fun setProperty(propertyKey: PropertiesApi.PropertyKey, propertyValue: Any): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.put("$PATH_PROPERTIES/${propertyKey.key}") { setBody(propertyValue) }
        }

    override suspend fun deleteProperty(propertyKey: PropertiesApi.PropertyKey): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.delete("$PATH_PROPERTIES/${propertyKey.key}")
    }

}
