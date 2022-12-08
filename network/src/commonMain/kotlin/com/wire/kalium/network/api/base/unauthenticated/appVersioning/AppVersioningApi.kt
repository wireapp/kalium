package com.wire.kalium.network.api.base.unauthenticated.appVersioning

import com.wire.kalium.network.UnauthenticatedNetworkClient
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.setUrl
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.http.URLBuilder
import io.ktor.http.Url

interface AppVersioningApi {
    suspend fun fetchAppVersionBlackList(blackListUrl: String): NetworkResponse<AppVersionBlackListResponse>
}

class AppVersioningApiImpl internal constructor(
    private val unauthenticatedNetworkClient: UnauthenticatedNetworkClient
) : AppVersioningApi {

    private val httpClient get() = unauthenticatedNetworkClient.httpClient

    override suspend fun fetchAppVersionBlackList(blackListUrl: String): NetworkResponse<AppVersionBlackListResponse> {
        return wrapKaliumResponse<AppVersionBlackListResponse> {
            httpClient.get {
                val platformBlackListUrl = URLBuilder().apply {
                    val url = Url(blackListUrl)
                    host = url.host
                    protocol = url.protocol
                    pathSegments = url.pathSegments + appVersioningUrlPlatformPath()
                }.buildString()

                setUrl(platformBlackListUrl)
            }
        }
    }
}
