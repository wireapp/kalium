package com.wire.kalium.network.auth

import com.wire.kalium.network.api.model.RefreshTokenProperties
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

internal actual fun HttpRequestBuilder.withBrowserCredentials() = Unit

internal actual fun HttpRequestBuilder.withManagedRefreshCookie(refreshToken: String) {
    header(HttpHeaders.Cookie, RefreshTokenProperties.COOKIE_NAME + "=" + refreshToken)
}

internal actual fun extractManagedRefreshToken(cookies: Map<String, String>): String? =
    cookies[RefreshTokenProperties.COOKIE_NAME]
