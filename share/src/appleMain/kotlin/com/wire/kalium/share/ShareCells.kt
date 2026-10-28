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
import com.wire.kalium.logic.sync.receiving.AppleAccountRuntime

internal fun AppleAccountRuntime.cellFileSharing(): CellFileSharing = CellFileSharing.create(
    network.cellsHttpClient,
    authenticatedSession,
    network.accessTokenApi,
    database.userConfigDAO,
    database.publicLinks
)

internal suspend fun AppleAccountRuntime.discardSharedDrafts(items: List<ShareItem>) {
    val attachments = items.filterIsInstance<ShareItem.Cells>().flatMap { it.attachments }
    if (attachments.isEmpty()) return
    val sharing = cellFileSharing()
    try {
        sharing.discard(attachments)
    } finally {
        sharing.close()
    }
}
