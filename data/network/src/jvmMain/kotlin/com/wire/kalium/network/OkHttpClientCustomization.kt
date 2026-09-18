/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.network

import okhttp3.OkHttpClient

/**
 * Adjusts every OkHttp client Kalium builds on the JVM, the one behind the WebSocket included.
 *
 * Meant for settings that depend on where Kalium runs, such as authenticating at a proxy with the user's Windows logon,
 * choosing the proxy the way the system does, or trusting the system's certificate store. The customizer runs after
 * Kalium's own settings, so it can also override them. Set it once, before Kalium makes its first request.
 */
object OkHttpClientCustomization {

    @Volatile
    var customizer: (OkHttpClient.Builder) -> Unit = {}
}
