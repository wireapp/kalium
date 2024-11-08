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
package com.wire.kalium.mocks.mocks.asset

import com.wire.kalium.network.api.authenticated.asset.AssetResponse
import com.wire.kalium.network.api.model.ErrorResponse

object AssetMocks {

    val invalid = ErrorResponse(code = 401, message = "Invalid Asset Token", label = "invalid_asset_token")

    val asset = AssetResponse(
        key = "3-1-e7788668-1b22-488a-b63c-acede42f771f",
        expires = "expiration_date",
        token = "asset_token",
        domain = "staging.wire.link"
    )

}
