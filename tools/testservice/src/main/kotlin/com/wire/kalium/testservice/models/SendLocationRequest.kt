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
package com.wire.kalium.testservice.models

data class SendLocationRequest(
    val conversationDomain: String = "staging.zinfra.io",
    val conversationId: String = "",
    val latitude: Float = 0.0f,
    val longitude: Float = 0.0f,
    val locationName: String = "",
    val zoom: Int = 0,
    val messageTimer: Int = 0
)
