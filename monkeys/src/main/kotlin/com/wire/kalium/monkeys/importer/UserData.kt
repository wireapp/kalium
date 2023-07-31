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

import com.wire.kalium.logic.data.user.UserId

data class UserData(
    val email: String,
    val password: String,
    val userId: UserId,
    val backend: Backend
)

data class Backend(
    val api: String,
    val accounts: String,
    val webSocket: String,
    val blackList: String,
    val teams: String,
    val website: String,
    val title: String,
    val domain: String
)
