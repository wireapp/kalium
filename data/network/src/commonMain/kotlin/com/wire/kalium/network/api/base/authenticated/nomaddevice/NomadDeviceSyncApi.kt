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

package com.wire.kalium.network.api.base.authenticated.nomaddevice

import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mockable
import okio.Sink
import okio.Source

@Mockable
interface NomadDeviceSyncApi {
    suspend fun postMessageEvents(request: NomadMessageEventsRequest): NetworkResponse<Unit>
    suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse>
    suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse>
    suspend fun uploadCryptoState(
        clientId: String,
        backupSource: () -> Source,
        backupSize: Long
    ): NetworkResponse<Unit>

    suspend fun downloadCryptoState(tempBackupFileSink: Sink): NetworkResponse<Unit>
    suspend fun setLastDeviceId(userId: String, deviceId: String): NetworkResponse<Unit>
}
