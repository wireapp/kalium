/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientType

/**
 * The required data needed to register a client
 * password
 * capabilities :Hints provided by the client for the backend so it can behave in a backwards-compatible way.
 * ex : legalHoldConsent
 * preKeysToSend : the initial public keys to start a conversation with another client
 * @see [RegisterClientParam]
 */
data class RegisterClientParam(
    val password: String?,
    val capabilities: List<ClientCapability>?,
    val clientType: ClientType? = null,
    val model: String? = null,
    val preKeysToSend: Int = DEFAULT_PRE_KEYS_COUNT,
    val secondFactorVerificationCode: String? = null,
    val modelPostfix: String? = null
)

const val FIRST_KEY_ID = 0
const val DEFAULT_PRE_KEYS_COUNT = 100
