package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import io.ktor.client.HttpClient

internal class AccessTokenApiV2 internal constructor(
    httpClient: HttpClient
) : AccessTokenApiV0(httpClient)
