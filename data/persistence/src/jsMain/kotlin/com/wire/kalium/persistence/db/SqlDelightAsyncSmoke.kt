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

@file:OptIn(ExperimentalJsExport::class)

package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.dao.UserIDEntity
import kotlin.js.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.promise

@JsExport
fun runSqlDelightAsyncSmoke(): Promise<String> = CoroutineScope(SupervisorJob() + Dispatchers.Default).promise {
    val database = userDatabaseBuilder(
        platformDatabaseData = PlatformDatabaseData(),
        userId = UserIDEntity("js-smoke", "wire.test"),
        passphrase = null,
        dispatcher = Dispatchers.Default,
        enableWAL = false,
    )

    try {
        database.metadataDAO.insertValue("value", "async-smoke")
        val stored = database.metadataDAO.valueByKey("async-smoke")
        check(stored == "value") { "Unexpected metadata value: $stored" }
        "ok"
    } finally {
        database.nuke()
    }
}
