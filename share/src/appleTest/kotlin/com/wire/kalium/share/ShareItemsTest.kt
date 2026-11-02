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

import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.CellAssetContent
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareItemsTest {
    @Test
    fun cellsCaptionBelongsToTheSameMultipartMessageAsEveryFile() {
        val files = listOf(ShareFile("/tmp/a", "a.txt", "text/plain", 1), ShareFile("/tmp/b", "b.txt", "text/plain", 2))
        val attachments = files.mapIndexed { index, file ->
            CellAssetContent(id = "node$index", versionId = "version$index", mimeType = file.mimeType,
                assetPath = "cell/${file.name}", assetSize = file.sizeInBytes, localPath = null, previewUrl = null,
                metadata = null, transferStatus = AssetTransferStatus.NOT_DOWNLOADED)
        }
        val item = ShareItem.Cells(files, attachments, "cell")
        assertEquals(listOf(item.copy(text = "Caption")), sharedItems(listOf(item), "Caption"))
        assertEquals(listOf(item), sharedItems(listOf(item), ""))
    }

    @Test
    fun textOnlyKeepsTheExistingTextMessagePath() {
        assertEquals(listOf(ShareItem.Text("hello")), sharedItems(emptyList(), "hello"))
    }
}
