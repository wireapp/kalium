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
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.globalDatabaseProvider
import com.wire.kalium.util.KaliumDispatcherImpl
import java.io.File

/** Stores [userId] as an account on a test server under [root], so a [CoreLogic] there can open its session scope. */
internal suspend fun storeTestAccount(root: File, userId: UserId) {
    val database = globalDatabaseProvider(
        platformDatabaseData = PlatformDatabaseData(StorageData.FileBacked(File(root, "global-storage"))),
        queriesContext = KaliumDispatcherImpl.io,
        passphrase = null,
    )
    try {
        database.serverConfigurationDAO.insert(
            ServerConfigurationDAO.InsertData(
                id = SERVER_CONFIG_ID, apiBaseUrl = URL, accountBaseUrl = URL, webSocketBaseUrl = "wss://example.invalid",
                blackListUrl = URL, teamsUrl = URL, websiteUrl = URL, title = "Test", isOnPremises = false,
                federation = false, domain = userId.domain, commonApiVersion = 8,
                apiProxyHost = null, apiProxyNeedsAuthentication = null, apiProxyPort = null,
            )
        )
        database.accountsDAO.insertOrReplace(UserIDEntity(userId.value, userId.domain), null, null, SERVER_CONFIG_ID, false)
    } finally {
        database.close()
    }
}

private const val SERVER_CONFIG_ID = "test-server"
private const val URL = "https://example.invalid"
