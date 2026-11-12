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

package com.wire.kalium.share

import com.wire.kalium.cells.CellFileSharing
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.session.SessionManager

internal suspend fun AppleAccountRuntime.cellFileSharing(): CellFileSharing = CellFileSharing.create(
    network.cellsHttpClient,
    authenticatedSession,
    network.accessTokenApi,
    // As Cells reads it: a configuration that cannot be read is none.
    wrapStorageRequest { database.userConfigDAO.getWireCellsConfig()?.backendUrl }.getOrNull()
)

/**
 * Cancels the Cells drafts among [items], which no stored message owns. Like uploading them, it needs neither the
 * account's database nor CoreCrypto, and a cancelled send still finishes it.
 */
internal suspend fun discardSharedDrafts(
    network: AuthenticatedNetworkContainer,
    session: SessionManager,
    cellsUrl: String?,
    items: List<ShareItem>,
) {
    val attachments = items.filterIsInstance<ShareItem.Cells>().flatMap { it.attachments }
    if (attachments.isEmpty()) return
    val sharing = CellFileSharing.create(network.cellsHttpClient, session, network.accessTokenApi, cellsUrl)
    try {
        sharing.discard(attachments)
    } finally {
        sharing.close()
    }
}
