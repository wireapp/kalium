//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium.assets

import kotlin.Throws
import java.io.IOException
import java.util.UUID
import java.io.ByteArrayInputStream
import com.waz.model.Messages.GenericMessage
import java.awt.image.BufferedImage
import com.waz.model.Messages.Asset.ImageMetaData
import com.waz.model.Messages.Asset.Original
import com.waz.model.Messages.Asset.RemoteData
import com.google.protobuf.ByteString
import com.waz.model.Messages.Ephemeral
import javax.imageio.ImageIO
import com.waz.model.Messages
import com.wire.kalium.tools.Util

class Picture : AssetBase {
    private var imageData: ByteArray?
    private var width = 0
    private var height = 0
    private var size = 0
    private var expires: Long = 0

    constructor(imageData: ByteArray?, mime: String?) : super(UUID.randomUUID(), mime, imageData) {
        this.imageData = imageData
        size = imageData.size
        val bufferedImage = loadBufferImage(imageData)
        width = bufferedImage.getWidth()
        height = bufferedImage.getHeight()
    }

    constructor(imageData: ByteArray?) : super(UUID.randomUUID(), Util.extractMimeType(imageData), imageData) {
        this.imageData = imageData
        size = imageData.size
        val bufferedImage = loadBufferImage(imageData)
        width = bufferedImage.getWidth()
        height = bufferedImage.getHeight()
    }

    constructor(messageId: UUID?, mimeType: String?) : super(messageId, mimeType) {}

    override fun createGenericMsg(): GenericMessage? {
        val ret = GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
        val metaData = ImageMetaData.newBuilder()
            .setHeight(height)
            .setWidth(width)
            .setTag("medium")
        val original = Original.newBuilder()
            .setSize(size.toLong())
            .setMimeType(mimeType)
            .setImage(metaData)
        val remoteData = RemoteData.newBuilder()
            .setOtrKey(ByteString.copyFrom(getOtrKey()))
            .setSha256(ByteString.copyFrom(getSha256()))
        if (getAssetToken() != null) {
            remoteData.assetToken = getAssetToken()
        }
        if (getAssetKey() != null) {
            remoteData.assetId = getAssetKey()
        }
        val asset = Messages.Asset.newBuilder()
            .setExpectsReadConfirmation(isReadReceiptsEnabled)
            .setUploaded(remoteData)
            .setOriginal(original)
        if (expires > 0) {
            val ephemeral = Ephemeral.newBuilder()
                .setAsset(asset)
                .setExpireAfterMillis(expires)
            return ret
                .setEphemeral(ephemeral)
                .build()
        }
        return ret
            .setAsset(asset)
            .build()
    }

    fun getWidth(): Int {
        return width
    }

    fun setWidth(width: Int) {
        this.width = width
    }

    fun getHeight(): Int {
        return height
    }

    fun setHeight(height: Int) {
        this.height = height
    }

    fun getImageData(): ByteArray? {
        return imageData
    }

    fun getSize(): Int {
        return size
    }

    fun setSize(size: Int) {
        this.size = size
    }

    fun getExpires(): Long {
        return expires
    }

    fun setExpires(expires: Long) {
        this.expires = expires
    }

    @Throws(IOException::class)
    private fun loadBufferImage(imageData: ByteArray?): BufferedImage? {
        ByteArrayInputStream(imageData).use { input -> return ImageIO.read(input) }
    }
}
