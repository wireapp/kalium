package com.wire.kalium.network.auth

import io.ktor.client.request.HttpRequestBuilder

internal expect fun HttpRequestBuilder.withBrowserCredentials()

internal expect fun HttpRequestBuilder.withManagedRefreshCookie(refreshToken: String)

internal expect fun extractManagedRefreshToken(cookies: Map<String, String>): String?
