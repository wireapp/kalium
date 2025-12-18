/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v12.authenticated

import com.wire.kalium.network.api.model.MessageSyncRequestDTO
import com.wire.kalium.network.api.v11.authenticated.MessageSyncApiV11
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class MessageSyncApiV12(
    private val httpClient: HttpClient,
    private val backupServiceUrl: String = "https://replica.wdebug.link"
) : MessageSyncApiV11() {

    override suspend fun syncMessages(request: MessageSyncRequestDTO): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$backupServiceUrl/messages") {
                setBody(request)
            }
        }
}
