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
package com.wire.kalium.persistence.dao.asset

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.datetime.Instant

data class AssetMessageEntity(
    val time: Instant,
    val username: String?,
    val messageId: String,
    val conversationId: QualifiedIDEntity,
    val assetId: String,
    val width: Int,
    val height: Int,
    val downloadStatus: MessageEntity.DownloadStatus,
    val assetPath: String?,
    val isSelfAsset: Boolean
)
