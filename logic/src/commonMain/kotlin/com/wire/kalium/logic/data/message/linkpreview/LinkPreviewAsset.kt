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
package com.wire.kalium.logic.data.message.linkpreview

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import okio.Path

data class LinkPreviewAsset(
    val name: String? = null,
    val mimeType: String,
    val metadata: AssetMetadata? = null,
    val assetDataPath: Path?,
    val assetDataSize: Long,
    val assetHeight: Int,
    val assetWidth: Int,
    val assetName: String? = null,
    var assetKey: String? = null,
    var assetToken: String? = null,
    var assetDomain: String? = null,
    var otrKey: AES256Key = AES256Key(ByteArray(0)),
    var sha256Key: SHA256Key = SHA256Key(ByteArray(0)),
    var encryptionAlgorithm: MessageEncryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
)
