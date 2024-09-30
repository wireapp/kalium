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
package com.wire.kalium.logic.data.client

import com.wire.kalium.cryptography.PreKeyCrypto

data class RegisterClientParam(
    val password: String?,
    val preKeys: List<PreKeyCrypto>,
    val lastKey: PreKeyCrypto,
    val deviceType: DeviceType?,
    val label: String?,
    val capabilities: List<ClientCapability>?,
    val clientType: ClientType?,
    val model: String?,
    val cookieLabel: String?,
    val secondFactorVerificationCode: String? = null,
    val modelPostfix: String? = null
)
