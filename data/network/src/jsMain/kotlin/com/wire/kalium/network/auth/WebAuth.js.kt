package com.wire.kalium.network.auth

import com.wire.kalium.network.api.model.RefreshTokenProperties
import io.ktor.client.fetchOptions
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

private const val BROWSER_MANAGED_REFRESH_TOKEN = "__browser_managed_refresh_cookie__"

internal actual fun HttpRequestBuilder.withBrowserCredentials() {
    fetchOptions {
        credentials = "include"
        mode = "cors"
    }
}

internal actual fun HttpRequestBuilder.withManagedRefreshCookie(refreshToken: String) {
    withBrowserCredentials()
    if (refreshToken != BROWSER_MANAGED_REFRESH_TOKEN) {
        header(HttpHeaders.Cookie, "${RefreshTokenProperties.COOKIE_NAME}=$refreshToken")
    }
}

internal actual fun extractManagedRefreshToken(cookies: Map<String, String>): String? =
    cookies.values.firstOrNull() ?: BROWSER_MANAGED_REFRESH_TOKEN
