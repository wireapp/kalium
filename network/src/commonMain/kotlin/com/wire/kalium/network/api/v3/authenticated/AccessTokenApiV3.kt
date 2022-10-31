package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.api.v2.authenticated.AccessTokenApiV2
import io.ktor.client.HttpClient

internal class AccessTokenApiV3 internal constructor(
    httpClient: HttpClient
) : AccessTokenApiV2(httpClient)
