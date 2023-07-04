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
import com.wire.kalium.monkeys.Backend
import com.wire.kalium.monkeys.UserData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

actual class TestDataImporter {
    @OptIn(ExperimentalSerializationApi::class)
    actual fun importFromFile(path: String): List<UserData> {
        val file = File(path)
        println("#### Importing test data from ${file.absolutePath}")
        val testData = Json.decodeFromStream<TestDataJsonModel>(
            file.inputStream()
        )

        return testData.backends.flatMap { backendDataJsonModel ->
            val backend = with(backendDataJsonModel) {
                Backend(
                    api,
                    accounts,
                    webSocket,
                    blackList,
                    teams,
                    website,
                    title,
                    domain
                )
            }
            backendDataJsonModel.users.map { user ->
                UserData(
                    user.email,
                    backendDataJsonModel.passwordForUsers,
                    UserId(user.unqualifiedId, backendDataJsonModel.domain),
                    backend
                )
            }
        }
    }
}
