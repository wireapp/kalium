/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.monkeys.importer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TestDataJsonModel(
    @SerialName("backends")
    val backends: List<BackendDataJsonModel>
)

@Serializable
data class BackendDataJsonModel(
    val api: String,
    val accounts: String,
    val webSocket: String,
    val blackList: String,
    val teams: String,
    val website: String,
    val title: String,
    val passwordForUsers: String,
    val domain: String,
    val users: List<UserAccountDataJsonModel>
)

@Serializable
data class UserAccountDataJsonModel(
    val email: String,
    @SerialName("id")
    val unqualifiedId: String
)
