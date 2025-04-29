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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse

internal open class MLSMessageApiV0 internal constructor() : MLSMessageApi {

    override suspend fun sendMessage(message: ByteArray): NetworkResponse<SendMLSMessageResponse> = NetworkResponse.Error(
        APINotSupported("MLS: sendMessage api is only available on API V5")
    )

    override suspend fun sendCommitBundle(bundle: MLSMessageApi.CommitBundle): NetworkResponse<SendMLSMessageResponse> =
        NetworkResponse.Error(
            APINotSupported("MLS: sendCommitBundle api is only available on API V5")
        )
}
