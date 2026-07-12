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

/**
 * Optional, opt-in hook to observe real outgoing HTTP requests and their responses at the
 * transport level, including bodies and unredacted headers/paths.
 *
 * This is distinct from [KaliumHttpLogger]/[KaliumKtorCustomLogging], which obfuscate paths and
 * headers and never read bodies, by design, since those are meant to be safe to enable in
 * production logs. [HttpTrafficObserver] is for consumers (e.g. a developer debugging tool) that
 * need full visibility and can be trusted with unredacted traffic.
 */
interface HttpTrafficObserver {
    fun onRequest(method: String, url: String, headers: Map<String, List<String>>, body: ByteArray?)
    fun onResponse(method: String, url: String, statusCode: Int, headers: Map<String, List<String>>, body: ByteArray?)
}
